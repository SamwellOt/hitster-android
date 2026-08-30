package com.hitster.mobile

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hitster.mobile.audio.PlaybackState
import com.hitster.mobile.audio.PreviewPlayer
import com.hitster.mobile.audio.PreviewResolver
import com.hitster.mobile.data.Prefs
import com.hitster.mobile.host.Catalog
import com.hitster.mobile.host.Discovery
import com.hitster.mobile.host.FoundSession
import com.hitster.mobile.host.LocalHost
import com.hitster.mobile.net.Action
import com.hitster.mobile.net.ClientMessage
import com.hitster.mobile.net.ConnState
import com.hitster.mobile.net.GameClient
import com.hitster.mobile.net.GameEvent
import com.hitster.mobile.net.GameOptions
import com.hitster.mobile.net.Phase
import com.hitster.mobile.net.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A transient message for the snackbar / toast layer. */
data class Toast(val text: String, val kind: String = "info")

class GameViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val client = GameClient()
    private val resolver = PreviewResolver()
    private val player = PreviewPlayer(app)

    /** Phone‑to‑phone mode: the session creator runs the game host inside the app. */
    private val catalog by lazy { Catalog.load(app) }
    private var host: LocalHost? = null
    private val discovery = Discovery(app)
    val sessions: StateFlow<List<FoundSession>> = discovery.sessions
    /** "ip:port" of the host running on this phone (shown in the lobby for manual entry). */
    val hostAddress = MutableStateFlow<String?>(null)

    val room: StateFlow<Room?> = client.room
    val connection: StateFlow<ConnState> = client.connection
    val isHost: StateFlow<Boolean> = client.isHost
    val playback: StateFlow<PlaybackState> = player.state
    val myId: String get() = prefs.playerId
    val clockOffset: Long get() = client.clockOffset

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 16)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()
    val events: SharedFlow<GameEvent> = client.events

    /** Local UI state that must survive recomposition but is not part of the server snapshot. */
    val selectedSlot = MutableStateFlow<Int?>(null)
    val claimsTitle = MutableStateFlow(false)
    val viewingTimelineOf = MutableStateFlow<String?>(null)
    val challengeMode = MutableStateFlow(false)
    val challengeSlot = MutableStateFlow<Int?>(null)
    val previewReady = MutableStateFlow<String?>(null) // resolved preview url for the current card
    val busy = MutableStateFlow(false)

    private var lastTurnKey: String? = null
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch {
            client.errors.collect {
                _toasts.tryEmit(Toast(it, "error"))
                // A rejected create/join (wrong code, game already started…) ends the connecting state at once.
                if (busy.value && client.room.value == null) { busy.value = false; client.leave() }
            }
        }
        viewModelScope.launch { client.kicked.collect { _toasts.tryEmit(Toast("Você foi removido da sessão.", "error")) } }
        viewModelScope.launch { client.ended.collect { player.stop(); resetTurnUi(); _toasts.tryEmit(Toast(it, "error")) } }
        viewModelScope.launch { client.events.collect(::onEvent) }
        viewModelScope.launch { client.room.collectLatest(::onRoom) }
        viewModelScope.launch {
            client.self.collect { it?.let { (code, _) -> prefs.lastRoom = code } }
        }
    }

    // ---------------------------------------------------------------- session

    /** Start the in‑app host on this phone, advertise it on the LAN and join it as the first player. */
    fun createSession(name: String, color: String) {
        saveIdentity(name, color)
        busy.value = true
        viewModelScope.launch {
            val started = withContext(Dispatchers.IO) {
                runCatching {
                    stopHost()
                    if (catalog.decks.isEmpty()) throw IllegalStateException("Nenhum baralho embutido no app.")
                    LocalHost(catalog).also { h ->
                        h.start()
                        // wait until the socket is bound (port is assigned asynchronously)
                        var tries = 0
                        while (h.port <= 0 && tries++ < 50) delay(50)
                        if (h.port <= 0) throw IllegalStateException("Não consegui abrir a porta do anfitrião.")
                    }
                }
            }
            val h = started.getOrElse {
                busy.value = false
                _toasts.tryEmit(Toast("Não foi possível criar a sessão: ${it.message}", "error"))
                return@launch
            }
            host = h
            hostAddress.value = Discovery.localIpv4()?.let { ip -> "$ip:${h.port}" } ?: "porta ${h.port} (sem Wi‑Fi?)"
            discovery.advertise(h.code, name, h.port)
            client.connect("ws://127.0.0.1:${h.port}", ClientMessage(type = "create", name = name, color = color, playerId = prefs.playerId))
            watchBusy()
        }
    }

    /** Join a host found on the network (or typed manually as "ip:port"). */
    fun joinSession(address: String, code: String, name: String, color: String) {
        saveIdentity(name, color)
        busy.value = true
        hostAddress.value = null
        val url = address.trim().let { if (it.startsWith("ws")) it else "ws://$it" }
        if (!address.startsWith("ws")) prefs.lastAddress = address.trim()
        client.connect(url, ClientMessage(type = "join", code = code.trim().uppercase(), name = name, color = color, playerId = prefs.playerId))
        watchBusy()
    }

    fun startDiscovery() = discovery.startDiscovery()
    fun stopDiscovery() = discovery.stopDiscovery()

    private fun stopHost() {
        discovery.stopAdvertising()
        host?.shutdown(); host = null
        hostAddress.value = null
    }

    private fun watchBusy() = viewModelScope.launch {
        // stop the spinner as soon as we are in a room, or after a timeout
        var waited = 0
        while (busy.value && waited < 12_000) {
            if (client.room.value != null) break
            delay(200); waited += 200
        }
        if (client.room.value == null && busy.value) {
            _toasts.tryEmit(Toast("Não foi possível conectar ao servidor. Confira a URL nas configurações.", "error"))
            client.leave()
        }
        busy.value = false
    }

    private fun saveIdentity(name: String, color: String) {
        prefs.name = name; prefs.color = color
    }

    fun leaveSession() {
        player.stop()
        client.leave()
        stopHost()
        resetTurnUi()
        prefs.lastRoom = null
    }

    fun setDecks(decks: List<String>) = client.setDecks(decks)
    fun setOptions(o: GameOptions) = client.setOptions(o)
    fun startGame() = client.start()
    fun restart() = client.restart()
    fun kick(id: String) = client.kick(id)

    // ---------------------------------------------------------------- turn actions

    fun selectSlot(slot: Int) { selectedSlot.value = if (selectedSlot.value == slot) null else slot }
    fun toggleClaim() { claimsTitle.value = !claimsTitle.value; client.action(Action("claimTitle", value = claimsTitle.value)) }

    fun confirmPlacement() {
        val slot = selectedSlot.value ?: return
        player.pause()
        client.action(Action("place", slot = slot, claimsTitle = claimsTitle.value))
    }

    fun skipSong() { client.action(Action("skip")) }
    fun buyCard() { client.action(Action("buyCard")) }
    fun pass() { client.action(Action("pass")) }
    fun vote(v: Boolean) { client.action(Action("vote", value = v)) }
    fun continueGame() { client.action(Action("continue")) }

    fun startChallenge() { challengeMode.value = true; challengeSlot.value = null }
    fun cancelChallenge() { challengeMode.value = false; challengeSlot.value = null }
    fun pickChallengeSlot(slot: Int) { challengeSlot.value = if (challengeSlot.value == slot) null else slot }
    fun confirmChallenge() {
        val slot = challengeSlot.value ?: return
        client.action(Action("challenge", slot = slot))
        challengeMode.value = false
        challengeSlot.value = null
    }

    fun openTimeline(playerId: String?) { viewingTimelineOf.value = playerId }

    // ---------------------------------------------------------------- audio

    fun playPreview() {
        previewReady.value?.let { player.play(it) } ?: run {
            val card = room.value?.game?.turn?.card ?: return
            resolveAndPlay(card.id, card.preview, autoplay = true)
        }
    }

    fun replay() = player.replay()
    fun pausePreview() = player.pause()

    private fun resolveAndPlay(trackId: String, fallback: String?, autoplay: Boolean) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val url = resolver.resolve(trackId, fallback)
            if (url == null) {
                _toasts.tryEmit(Toast("Não consegui obter o preview desta música. Você pode pular (1 ficha) ou tentar de novo.", "error"))
                return@launch
            }
            previewReady.value = url
            if (autoplay) player.play(url)
        }
    }

    // ---------------------------------------------------------------- reactions to server state

    private fun onRoom(room: Room?) {
        val game = room?.game
        val turn = game?.turn
        if (game == null || turn == null) {
            // Back in the lobby (or no session): forget the last turn so a rematch's first turn
            // (same round/player/skips key) is detected as a new turn and autoplays again.
            lastTurnKey = null
            resetTurnUi(); player.stop(); return
        }
        val key = "${game.round}:${turn.playerId}:${turn.skips}"
        if (key != lastTurnKey) {
            lastTurnKey = key
            resetTurnUi()
            player.stop()
            if (turn.playerId == myId && turn.phase == Phase.LISTEN) {
                // My turn: pre‑fetch and autoplay the preview (only this phone plays).
                turn.card?.let { resolveAndPlay(it.id, it.preview, autoplay = true) }
                vibrate(longArrayOf(0, 60, 60, 120))
            }
        }
        if (turn.phase != Phase.LISTEN && player.state.value.isPlaying) player.pause()
        if (turn.phase != Phase.CHALLENGE) { challengeMode.value = false; challengeSlot.value = null }
        // keep local toggle in sync with the server copy
        if (turn.playerId == myId) claimsTitle.value = turn.claimsTitle
    }

    private fun resetTurnUi() {
        selectedSlot.value = null
        claimsTitle.value = false
        challengeMode.value = false
        challengeSlot.value = null
        previewReady.value = null
        resolveJob?.cancel()
    }

    private fun onEvent(e: GameEvent) {
        val r = room.value ?: return
        fun name(id: String?) = r.game?.player(id)?.name ?: r.players.firstOrNull { it.id == id }?.name ?: "Alguém"
        val me = { id: String? -> id == myId }
        when (e.kind) {
            "joined" -> if (!me(e.playerId)) _toasts.tryEmit(Toast("${e.name} entrou na sessão"))
            "left" -> _toasts.tryEmit(Toast("${e.name ?: name(e.playerId)} saiu"))
            "disconnected" -> _toasts.tryEmit(Toast("${e.name ?: name(e.playerId)} perdeu a conexão", "warn"))
            "reconnected" -> if (!me(e.playerId)) _toasts.tryEmit(Toast("${e.name ?: name(e.playerId)} voltou"))
            "skip" -> _toasts.tryEmit(Toast(if (me(e.playerId)) "Música pulada (−1 ficha)" else "${name(e.playerId)} pulou a música"))
            "challenge" -> {
                if (!me(e.playerId)) { _toasts.tryEmit(Toast("${name(e.playerId)} gritou HITSTER!", "hot")); vibrate(longArrayOf(0, 40, 40, 40)) }
            }
            "bought" -> _toasts.tryEmit(Toast("${if (me(e.playerId)) "Você" else name(e.playerId)} trocou 3 fichas por uma carta (${e.card?.year})"))
            "reveal" -> vibrate(longArrayOf(0, 80))
            "finished" -> vibrate(longArrayOf(0, 100, 80, 100, 80, 300))
        }
    }

    private fun vibrate(pattern: LongArray) {
        runCatching {
            val ctx = getApplication<Application>()
            val v = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    override fun onCleared() {
        player.release()
        client.leave()
        discovery.stopDiscovery()
        stopHost()
    }
}
