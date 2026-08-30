package com.hitster.mobile.net

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Thin WebSocket client around the sync server. Keeps the last room snapshot in a StateFlow,
 * exposes transient events/errors as SharedFlows and reconnects (with rejoin) automatically.
 */
class GameClient(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var serverUrl: String = ""
    private var reconnectJob: Job? = null
    private var wantConnection = false

    /** Sent again on every reconnect so the server can put us back into the room. */
    private var rejoin: ClientMessage? = null

    val connection = MutableStateFlow(ConnState.DISCONNECTED)
    val room = MutableStateFlow<Room?>(null)
    val self = MutableStateFlow<Pair<String, String>?>(null) // (roomCode, playerId)
    val isHost = MutableStateFlow(false)
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()
    private val _kicked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val kicked = _kicked.asSharedFlow()
    /** The host closed the session (message to show). */
    private val _ended = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ended = _ended.asSharedFlow()
    /** Estimated (serverNow - localNow) so countdowns line up on every phone. */
    @Volatile var clockOffset: Long = 0

    fun connect(url: String, firstMessage: ClientMessage) {
        serverUrl = normalize(url)
        rejoin = firstMessage
        wantConnection = true
        open()
    }

    private fun normalize(url: String): String {
        var u = url.trim()
        if (u.startsWith("http://")) u = "ws://" + u.removePrefix("http://")
        if (u.startsWith("https://")) u = "wss://" + u.removePrefix("https://")
        if (!u.startsWith("ws://") && !u.startsWith("wss://")) u = "ws://$u"
        return u.trimEnd('/')
    }

    private fun open() {
        socket?.cancel()
        connection.value = ConnState.CONNECTING
        val req = Request.Builder().url(serverUrl).build()
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connection.value = ConnState.CONNECTED
                rejoin?.let { send(it) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("GameClient", "ws failure: ${t.message}")
                connection.value = ConnState.DISCONNECTED
                if (wantConnection) scheduleReconnect(t.message)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connection.value = ConnState.DISCONNECTED
                if (wantConnection) scheduleReconnect(null)
            }
        })
    }

    private fun scheduleReconnect(reason: String?) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempt = 0
            while (wantConnection && connection.value != ConnState.CONNECTED) {
                val wait = minOf(1000L * (1 shl minOf(attempt, 4)), 15_000L)
                delay(wait)
                if (!wantConnection) break
                attempt++
                open()
                delay(4_000)
            }
        }
    }

    private fun handle(text: String) {
        val msg = try { json.decodeFromString(ServerMessage.serializer(), text) } catch (e: Exception) {
            Log.e("GameClient", "bad message: $text", e); return
        }
        when (msg.type) {
            "joined" -> {
                val code = msg.roomCode ?: return
                val pid = msg.playerId ?: return
                self.value = code to pid
                isHost.value = msg.isHost == true
                // From now on reconnects should *rejoin* with the same id.
                rejoin = ClientMessage(type = "join", code = code, playerId = pid, name = rejoin?.name, color = rejoin?.color)
            }
            "room" -> {
                msg.room?.let { r ->
                    r.game?.now?.takeIf { it > 0 }?.let { clockOffset = it - System.currentTimeMillis() }
                    room.value = r
                    isHost.value = r.hostId == self.value?.second
                }
            }
            "events" -> msg.events?.forEach { _events.tryEmit(it) }
            "error" -> _errors.tryEmit(msg.message ?: "Erro")
            "kicked" -> { leave(); _kicked.tryEmit(Unit) }
            "ended" -> { leave(); _ended.tryEmit(msg.message ?: "O anfitrião encerrou a sessão.") }
            "pong" -> msg.now?.let { clockOffset = it - System.currentTimeMillis() }
        }
    }

    fun send(msg: ClientMessage): Boolean {
        val s = socket ?: return false
        return s.send(json.encodeToString(ClientMessage.serializer(), msg))
    }

    fun action(action: Action) = send(ClientMessage(type = "action", action = action))
    fun setDecks(decks: List<String>) = send(ClientMessage(type = "setDecks", decks = decks))
    fun setOptions(options: GameOptions) = send(ClientMessage(type = "setOptions", options = options))
    fun start() = send(ClientMessage(type = "start"))
    fun restart() = send(ClientMessage(type = "restart"))
    fun kick(playerId: String) = send(ClientMessage(type = "kick", playerId = playerId))

    fun leave() {
        wantConnection = false
        reconnectJob?.cancel()
        runCatching { send(ClientMessage(type = "leave")) }
        socket?.close(1000, "bye")
        socket = null
        rejoin = null
        room.value = null
        self.value = null
        isHost.value = false
        connection.value = ConnState.DISCONNECTED
    }
}
