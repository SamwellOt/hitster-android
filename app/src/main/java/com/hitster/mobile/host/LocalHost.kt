package com.hitster.mobile.host

import android.util.Log
import com.hitster.mobile.net.ClientMessage
import com.hitster.mobile.net.GameEvent
import com.hitster.mobile.net.GameOptions
import com.hitster.mobile.net.PlayerInfo
import com.hitster.mobile.net.Room
import com.hitster.mobile.net.ServerMessage
import com.hitster.mobile.net.json
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The game host that runs *inside the app* on the phone that created the session.
 * Other phones connect over the local network (same Wi‑Fi or this phone's hotspot).
 * Same JSON protocol as server/src/index.js, single room per host.
 */
class LocalHost(private val catalog: Catalog, port: Int = 0) : WebSocketServer(InetSocketAddress(port)) {

    private class Player(val id: String, var name: String, val color: String, var conn: WebSocket?, var connected: Boolean)

    val code: String = List(4) { ALPHABET.random() }.joinToString("")
    private val lock = Any()
    private val players = LinkedHashMap<String, Player>()
    private var hostId: String? = null
    private var phase = "lobby"
    private var decks: List<String> = catalog.summaries.firstOrNull()?.let { listOf(it.sku) } ?: emptyList()
    private var options = GameOptions()
    private var engine: GameEngine? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var timer: ScheduledFuture<*>? = null
    private val connPlayer = HashMap<WebSocket, String>()

    init {
        isReuseAddr = true
        connectionLostTimeout = 15
    }

    // ---------------------------------------------------------------- WebSocketServer callbacks

    override fun onStart() { Log.i(TAG, "host listening on $port, room $code") }
    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) = synchronized(lock) {
        val pid = connPlayer.remove(conn) ?: return
        val p = players[pid] ?: return
        if (p.conn !== conn) return
        p.connected = false; p.conn = null
        if (phase == "lobby" && hostId != pid) leave(pid) else broadcast(listOf(GameEvent(kind = "disconnected", playerId = pid, name = p.name)))
    }

    override fun onError(conn: WebSocket?, ex: Exception) { Log.w(TAG, "ws error", ex) }

    override fun onMessage(conn: WebSocket, message: String) {
        val msg = runCatching { json.decodeFromString(ClientMessage.serializer(), message) }.getOrNull()
            ?: return send(conn, ServerMessage(type = "error", message = "JSON inválido."))
        synchronized(lock) {
            try {
                handle(conn, msg)
            } catch (e: GameError) {
                send(conn, ServerMessage(type = "error", message = e.message))
            } catch (e: Exception) {
                Log.e(TAG, "handler failed", e)
                send(conn, ServerMessage(type = "error", message = "Erro interno no anfitrião."))
            }
        }
    }

    // ---------------------------------------------------------------- protocol

    private fun handle(conn: WebSocket, msg: ClientMessage) {
        val pid = connPlayer[conn]
        when (msg.type) {
            "ping" -> send(conn, ServerMessage(type = "pong", now = System.currentTimeMillis()))

            "create", "join" -> {
                // "create" may skip the code only while the room is empty (the creator's own first message)
                // or when it comes from someone already in the room (a reconnect before "joined" arrived);
                // otherwise anyone who reaches this ip:port would join by sending "create" instead of "join".
                val reconnecting = msg.playerId != null && players.containsKey(msg.playerId)
                if ((msg.type == "join" || (players.isNotEmpty() && !reconnecting)) && msg.code?.uppercase()?.trim() != code) {
                    throw GameError("Sessão não encontrada. Confira o código.")
                }
                val id = msg.playerId ?: UUID.randomUUID().toString()
                val existing = players[id]
                if (existing != null) {
                    // reconnect (or the host re‑attaching to its own room)
                    existing.conn?.takeIf { it !== conn && it.isOpen }?.close(1000, "replaced")
                    existing.conn = conn; existing.connected = true
                    msg.name?.let { existing.name = cleanName(it) }
                    connPlayer[conn] = id
                    send(conn, ServerMessage(type = "joined", roomCode = code, playerId = id, isHost = hostId == id))
                    broadcast(listOf(GameEvent(kind = "reconnected", playerId = id, name = existing.name)))
                    return
                }
                if (phase != "lobby") throw GameError("Essa partida já começou.")
                if (players.size >= 10) throw GameError("A sessão está cheia (máx. 10).")
                val p = Player(id, cleanName(msg.name), msg.color ?: "#00E5FF", conn, true)
                players[id] = p
                connPlayer[conn] = id
                if (hostId == null) hostId = id
                send(conn, ServerMessage(type = "joined", roomCode = code, playerId = id, isHost = hostId == id))
                broadcast(listOf(GameEvent(kind = "joined", playerId = id, name = p.name)))
            }

            "setDecks" -> {
                requireHost(pid)
                val chosen = msg.decks.orEmpty().filter { it in catalog.decks }
                if (chosen.isEmpty()) throw GameError("Escolha pelo menos um baralho.")
                decks = chosen; broadcast()
            }

            "setOptions" -> {
                requireHost(pid)
                val o = msg.options ?: return
                options = GameOptions(
                    challengeSeconds = o.challengeSeconds.coerceIn(5, 60),
                    voteSeconds = o.voteSeconds.coerceIn(10, 60),
                    resultSeconds = o.resultSeconds.coerceIn(5, 60),
                    cardsToWin = o.cardsToWin.coerceIn(5, 20),
                    startTokens = 2,
                )
                broadcast()
            }

            "kick" -> {
                requireHost(pid)
                val target = msg.playerId?.takeIf { it != pid } ?: return
                players[target]?.let { t ->
                    t.conn?.let { send(it, ServerMessage(type = "kicked")) }
                    leave(target)
                }
            }

            "start" -> {
                requireHost(pid)
                if (phase == "playing") throw GameError("O jogo já começou.")
                val cards = catalog.cardsFor(decks)
                if (cards.isEmpty()) throw GameError("Nenhum baralho selecionado.")
                val infos = players.values.map { PlayerInfo(it.id, it.name, it.color, it.connected) }
                // msg.playerId = who starts; unknown/absent means a random order (sortedBy is stable: the rest keeps lobby order).
                val first = msg.playerId?.takeIf { players.containsKey(it) }
                val order = if (first != null) infos.sortedBy { if (it.id == first) 0 else 1 } else infos.shuffled()
                engine = GameEngine(order, cards, options)
                phase = "playing"
                broadcast(listOf(GameEvent(kind = "started"), GameEvent(kind = "turn", playerId = engine!!.turn!!.playerId)))
            }

            "restart" -> {
                requireHost(pid)
                engine = null; phase = "lobby"
                broadcast(listOf(GameEvent(kind = "lobby")))
            }

            "action" -> {
                val id = pid ?: throw GameError("Você não está em uma sessão.")
                val e = engine?.takeIf { phase == "playing" } ?: throw GameError("O jogo não está em andamento.")
                val events = e.apply(id, msg.action ?: throw GameError("Ação inválida."))
                if (e.finished) phase = "finished"
                broadcast(events)
            }

            "leave" -> pid?.let { connPlayer.remove(conn); leave(it) }

            else -> throw GameError("Mensagem desconhecida: ${msg.type}")
        }
    }

    private fun requireHost(pid: String?) {
        if (pid == null || pid != hostId) throw GameError("Só o anfitrião pode fazer isso.")
    }

    private fun leave(playerId: String) {
        val p = players.remove(playerId) ?: return
        // removePlayer can close the round the leaver was blocking – those events must reach the others.
        val events = engine?.takeIf { !it.finished }?.let { e ->
            e.removePlayer(playerId).also { if (e.finished) phase = "finished" }
        }.orEmpty()
        if (hostId == playerId) hostId = players.keys.firstOrNull()
        broadcast(listOf(GameEvent(kind = "left", playerId = playerId, name = p.name)) + events)
    }

    // ---------------------------------------------------------------- snapshots & timers

    private fun snapshot(forPlayerId: String) = Room(
        code = code,
        hostId = hostId,
        phase = phase,
        players = players.values.map { PlayerInfo(it.id, it.name, it.color, it.connected) },
        decks = decks,
        availableDecks = catalog.summaries,
        options = options,
        game = engine?.view(forPlayerId),
    )

    private fun broadcast(events: List<GameEvent> = emptyList()) {
        for (p in players.values) {
            val c = p.conn ?: continue
            if (!p.connected) continue
            send(c, ServerMessage(type = "room", room = snapshot(p.id)))
            if (events.isNotEmpty()) send(c, ServerMessage(type = "events", events = events))
        }
        armTimer()
    }

    private fun armTimer() {
        timer?.cancel(false); timer = null
        val e = engine ?: return
        if (e.finished || scheduler.isShutdown) return
        val deadline = e.turn?.deadline ?: return
        val delay = (deadline - System.currentTimeMillis()).coerceAtLeast(0) + 50
        timer = runCatching {
            scheduler.schedule({
                synchronized(lock) {
                    val eng = engine ?: return@synchronized
                    val events = runCatching { eng.tick() }.getOrDefault(emptyList())
                    if (eng.finished) phase = "finished"
                    broadcast(events)
                }
            }, delay, TimeUnit.MILLISECONDS)
        }.getOrNull()
    }

    private fun send(conn: WebSocket, msg: ServerMessage) {
        if (!conn.isOpen) return
        runCatching { conn.send(json.encodeToString(ServerMessage.serializer(), msg)) }
    }

    /** Tell every guest the session is over (the host phone left), then stop listening. */
    fun shutdown() {
        synchronized(lock) {
            timer?.cancel(false)
            for (p in players.values) p.conn?.let { send(it, ServerMessage(type = "ended", message = "O anfitrião encerrou a sessão.")) }
        }
        scheduler.shutdownNow()
        runCatching { stop(500) }
    }

    companion object {
        private const val TAG = "LocalHost"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun cleanName(n: String?): String = n?.trim()?.take(18)?.ifBlank { null } ?: "Jogador"
    }
}
