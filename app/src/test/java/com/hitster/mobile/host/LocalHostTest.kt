package com.hitster.mobile.host

import com.hitster.mobile.net.Action
import com.hitster.mobile.net.Card
import com.hitster.mobile.net.ClientMessage
import com.hitster.mobile.net.GameOptions
import com.hitster.mobile.net.Phase
import com.hitster.mobile.net.Room
import com.hitster.mobile.net.ServerMessage
import com.hitster.mobile.net.json
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Boots the real in‑app host and drives three WebSocket clients through a game (port of server/test/e2e.test.js). */
class LocalHostTest {
    private lateinit var host: LocalHost
    private val clients = mutableListOf<C>()

    private class C(uri: URI, val name: String) : WebSocketClient(uri) {
        @Volatile var room: Room? = null
        @Volatile var playerId: String? = null
        val errors = CopyOnWriteArrayList<String>()
        val events = CopyOnWriteArrayList<String>()
        override fun onOpen(h: ServerHandshake) {}
        override fun onClose(code: Int, reason: String, remote: Boolean) { System.err.println("[$name] CLOSED code=$code reason=$reason remote=$remote") }
        override fun onError(ex: Exception) { errors += "ws:" + ex.message; ex.printStackTrace() }
        override fun onMessage(text: String) {
            // System.err.println("[$name] <- ${text.take(200)}")
            val m = json.decodeFromString(ServerMessage.serializer(), text)
            when (m.type) {
                "room" -> room = m.room
                "joined" -> playerId = m.playerId
                "error" -> errors += m.message ?: "?"
                "events" -> m.events?.forEach { events += it.kind }
            }
        }
        fun sendMsg(m: ClientMessage) = send(json.encodeToString(ClientMessage.serializer(), m))
        fun action(a: Action) = sendMsg(ClientMessage(type = "action", action = a))
        val game get() = room!!.game!!
        val me get() = game.players.first { it.id == playerId }
        fun until(ms: Long = 3000, pred: (C) -> Boolean) {
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < ms) { if (pred(this)) return; Thread.sleep(20) }
            throw AssertionError("$name: condition not met; errors=$errors phase=${room?.game?.turn?.phase}")
        }
    }

    @Before fun up() {
        val cards = (0 until 60).map { Card(id = "trk$it", title = "Song $it", artist = "Artist $it", year = 1960 + it) }
        host = LocalHost(Catalog(mapOf("aaaq0001" to Deck("aaaq0001", "Fixture", cards = cards))), port = 0)
        host.start()
        var tries = 0
        while (host.port <= 0 && tries++ < 100) Thread.sleep(20)
        assertTrue(host.port > 0)
    }

    @After fun down() {
        clients.forEach { runCatching { it.closeBlocking() } }
        host.shutdown()
    }

    private fun connect(name: String): C = C(URI("ws://127.0.0.1:${host.port}"), name).also {
        assertTrue(it.connectBlocking(5, TimeUnit.SECONDS)); clients += it
    }

    @Test fun fullSession() {
        val a = connect("A"); val b = connect("B"); val c = connect("C")
        a.sendMsg(ClientMessage(type = "create", name = "Ana", color = "#f00", playerId = "ana-1"))
        a.until { it.room?.code != null }
        val code = a.room!!.code
        assertEquals(host.code, code)

        b.sendMsg(ClientMessage(type = "join", code = code, name = "Bia", color = "#0f0", playerId = "bia-1"))
        c.sendMsg(ClientMessage(type = "join", code = code.lowercase(), name = "Caio", color = "#00f", playerId = "caio-1"))
        a.until { it.room!!.players.size == 3 }

        b.sendMsg(ClientMessage(type = "start"))
        b.until { it.errors.size == 1 }
        assertTrue(b.errors[0].contains("anfitrião"))

        a.sendMsg(ClientMessage(type = "setOptions", options = GameOptions(challengeSeconds = 5, voteSeconds = 10, resultSeconds = 5, cardsToWin = 3)))
        a.until { it.room!!.options.challengeSeconds == 5 && it.room!!.options.cardsToWin == 5 }
        a.sendMsg(ClientMessage(type = "start"))
        listOf(a, b, c).forEach { x -> x.until { it.room?.phase == "playing" && it.room?.game != null } }

        val turnOf = a.game.turn!!.playerId
        val current = listOf(a, b, c).first { it.playerId == turnOf }
        val others = listOf(a, b, c).filter { it !== current }
        assertNotNull(current.game.turn!!.card!!.id)
        assertNull(current.game.turn!!.card!!.title)
        others.forEach { assertNull(it.game.turn!!.card) }

        val year = 1960 + current.game.turn!!.card!!.id.removePrefix("trk").toInt()
        val tl = current.me.timeline
        var slot = 0; while (slot < tl.size && tl[slot].year!! <= year) slot++
        current.action(Action("place", slot = slot, claimsTitle = true))
        others.forEach { o -> o.until { it.game.turn!!.phase == Phase.CHALLENGE } }

        others[0].action(Action("challenge", slot = if (slot == 0) 1 else 0))
        others[1].action(Action("pass"))
        current.until { it.game.turn!!.phase == Phase.VOTE }
        assertEquals("Song " + current.game.turn!!.card!!.id.removePrefix("trk"), current.game.turn!!.card!!.title)
        assertEquals(1, others[0].me.tokens)

        others[0].action(Action("vote", value = true)); others[1].action(Action("vote", value = true))
        current.until { it.game.turn!!.phase == Phase.RESULT }
        assertEquals(true, current.game.turn!!.result!!.correct)
        assertEquals(true, current.game.turn!!.result!!.tokenEarned)
        assertEquals(3, current.me.tokens)
        assertEquals(2, current.me.timeline.size)

        // auto-advance after resultSeconds (host timer)
        a.until(8000) { it.game.turn!!.phase == Phase.LISTEN && it.game.turn!!.playerId != turnOf }

        // reconnect keeps identity
        val rc = others[1]; val rcId = rc.playerId!!
        rc.closeBlocking()
        val rc2 = connect("R")
        rc2.sendMsg(ClientMessage(type = "join", code = code, playerId = rcId, name = "Caio2"))
        rc2.until { it.room?.game != null }
        assertEquals(rcId, rc2.playerId)
        assertEquals(3, rc2.room!!.players.size)
        assertTrue(rc2.room!!.players.all { it.connected })

        // 3 tokens → card at any time
        val buyer = listOf(a, b, rc2).firstOrNull { it.me.tokens >= 3 }
        if (buyer != null) {
            val before = buyer.me.timeline.size
            buyer.action(Action("buyCard"))
            buyer.until { it.me.timeline.size == before + 1 }
            assertEquals(0, buyer.me.tokens)
        }
    }
}
