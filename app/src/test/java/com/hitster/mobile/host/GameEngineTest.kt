package com.hitster.mobile.host

import com.hitster.mobile.net.Action
import com.hitster.mobile.net.Card
import com.hitster.mobile.net.GameOptions
import com.hitster.mobile.net.Phase
import com.hitster.mobile.net.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Port of server/test/game.test.js – same rules, same expectations. */
class GameEngineTest {
    private val cards = (0 until 40).map { Card(id = "id$it", title = "T$it", artist = "A$it", year = 1960 + it * 2) }
    private val players = listOf(PlayerInfo("p1", "Ana", "#f00"), PlayerInfo("p2", "Bia", "#0f0"), PlayerInfo("p3", "Caio", "#00f"))
    private var clock = 1_000_000L

    /** A Random whose nextInt(bound) always returns bound‑1 keeps `shuffled()` in the original order. */
    private object KeepOrder : Random() {
        override fun nextBits(bitCount: Int): Int = (1 shl bitCount) - 1
        override fun nextInt(until: Int): Int = until - 1
        override fun nextInt(from: Int, until: Int): Int = until - 1
    }

    private fun fresh(opts: GameOptions = GameOptions(challengeSeconds = 10, voteSeconds = 10, resultSeconds = 5)): GameEngine {
        clock = 1_000_000L
        return GameEngine(players, cards, opts, random = KeepOrder, now = { clock })
    }

    private fun GameEngine.p(id: String) = players.first { it.id == id }

    /** Every opponent answers the title/artist question, which is now asked on every reveal. */
    private fun GameEngine.answerTitle(said: Boolean) {
        if (turn?.phase != Phase.VOTE) return
        val cur = turn!!.playerId
        players.map { it.id }.filter { it != cur }.forEach { apply(it, Action("vote", value = said)) }
    }

    @Test fun setup() {
        val g = fresh()
        assertEquals(3, g.players.size)
        g.players.forEach { assertEquals(2, it.tokens); assertEquals(1, it.timeline.size) }
        assertEquals("p1", g.turn!!.playerId)
        assertEquals(Phase.LISTEN, g.turn!!.phase)
        assertEquals(1960, g.p("p1").timeline[0].year)
        assertEquals(1962, g.p("p2").timeline[0].year)
        assertEquals(1964, g.p("p3").timeline[0].year)
        assertEquals(1966, g.turn!!.card.year)
    }

    @Test fun fitsAndInsert() {
        val tl = listOf(Card("a", year = 1970), Card("b", year = 1990))
        assertTrue(GameEngine.fits(tl, 0, 1960)); assertFalse(GameEngine.fits(tl, 0, 1975))
        assertTrue(GameEngine.fits(tl, 1, 1975)); assertTrue(GameEngine.fits(tl, 2, 1995))
        assertTrue(GameEngine.fits(tl, 1, 1990)); assertTrue(GameEngine.fits(tl, 2, 1990))
        val t2 = tl.toMutableList()
        assertEquals(1, GameEngine.insertSorted(t2, Card("c", year = 1980)))
        assertEquals(listOf(1970, 1980, 1990), t2.map { it.year })
    }

    @Test fun hiddenCard() {
        val g = fresh()
        assertNull(g.view("p2").turn!!.card)
        val c = g.view("p1").turn!!.card!!
        assertEquals("id3", c.id); assertNull(c.title); assertNull(c.year)
    }

    @Test fun placementRightAndWrong() {
        val g = fresh()
        g.apply("p1", Action("place", slot = 1))
        assertEquals(Phase.CHALLENGE, g.turn!!.phase)
        g.apply("p2", Action("pass")); g.apply("p3", Action("pass"))
        assertEquals(Phase.VOTE, g.turn!!.phase)   // title/artist is always asked
        g.answerTitle(false)
        assertEquals(Phase.RESULT, g.turn!!.phase)
        assertTrue(g.turn!!.result!!.correct)
        assertEquals(2, g.p("p1").timeline.size)
        g.apply("p1", Action("continue"))
        assertEquals("p2", g.turn!!.playerId)
        g.apply("p2", Action("place", slot = 0)) // wrong
        clock += 11_000; g.tick()                 // challenge window closes → vote
        clock += 11_000; g.tick()                 // nobody voted → no extra token
        assertEquals(Phase.RESULT, g.turn!!.phase)
        assertFalse(g.turn!!.result!!.correct)
        assertEquals(1, g.p("p2").timeline.size)
        assertEquals(1, g.discard.size)
    }

    @Test fun skip() {
        val g = fresh()
        val first = g.turn!!.card
        g.apply("p1", Action("skip"))
        assertEquals(1, g.p("p1").tokens)
        assertNotEquals(first.id, g.turn!!.card.id)
        assertEquals(first.id, g.deck.last().id)
        g.apply("p1", Action("skip"))
        assertThrows(GameError::class.java) { g.apply("p1", Action("skip")) }
    }

    @Test fun challengeSteals() {
        val g = fresh()
        g.apply("p1", Action("place", slot = 0)) // wrong
        assertThrows(GameError::class.java) { g.apply("p1", Action("challenge")) }
        g.apply("p2", Action("challenge"))         // p2 shouts first
        assertEquals(1, g.p("p2").tokens)
        assertThrows(GameError::class.java) { g.apply("p2", Action("challenge")) }
        g.apply("p3", Action("challenge"))         // p3 also bets: spends the token, p2 was first
        assertEquals(1, g.p("p3").tokens)
        g.answerTitle(false)
        assertEquals(Phase.RESULT, g.turn!!.phase)
        assertFalse(g.turn!!.result!!.correct)
        assertEquals("p2", g.turn!!.result!!.stolenBy)
        assertTrue(g.turn!!.result!!.challenges.all { it.correct == true })
        assertEquals(listOf(1962, 1966), g.p("p2").timeline.map { it.year })
        assertEquals(1, g.p("p1").timeline.size)
        assertEquals(1, g.p("p3").timeline.size)
    }

    @Test fun challengeLosesTokenWhenOwnerRight() {
        val g = fresh()
        g.apply("p1", Action("place", slot = 1))
        g.apply("p2", Action("challenge"))
        g.apply("p3", Action("pass"))
        assertTrue(g.turn!!.result!!.correct)
        assertNull(g.turn!!.result!!.stolenBy)
        assertEquals(false, g.turn!!.result!!.challenges[0].correct)
        assertEquals(1, g.p("p2").tokens)
        assertEquals(2, g.p("p1").timeline.size)
    }

    @Test fun voteEarnsToken() {
        val g = fresh()
        g.apply("p1", Action("place", slot = 0))
        g.apply("p2", Action("pass")); g.apply("p3", Action("pass"))
        assertEquals(Phase.VOTE, g.turn!!.phase)
        assertNotNull(g.view("p2").turn!!.card!!.title)
        g.apply("p2", Action("vote", value = true))
        g.apply("p3", Action("vote", value = false))
        assertEquals(Phase.RESULT, g.turn!!.phase)
        assertEquals(true, g.turn!!.result!!.tokenEarned)
        assertEquals(3, g.p("p1").tokens)
    }

    @Test fun buyCard() {
        val g = fresh()
        g.p("p3").tokens = 5
        val top = g.deck.first()
        g.apply("p3", Action("buyCard"))
        assertEquals(2, g.p("p3").tokens)
        assertEquals(2, g.p("p3").timeline.size)
        assertTrue(g.p("p3").timeline.any { it.id == top.id })
        assertThrows(GameError::class.java) { g.apply("p3", Action("buyCard")) }
    }

    /** Buying mid‑challenge would slide a card into the timeline the engine is about to judge. */
    @Test fun buyCardIsBlockedWhileMyPlacementWaitsForTheReveal() {
        val g = fresh()
        g.p("p1").tokens = 5
        g.p("p3").tokens = 3
        g.apply("p1", Action("place", slot = 1))
        assertThrows(GameError::class.java) { g.apply("p1", Action("buyCard")) }
        g.apply("p3", Action("buyCard"))          // an opponent's timeline is unaffected: still allowed
        g.apply("p2", Action("pass")); g.apply("p3", Action("pass"))
        assertTrue(g.turn!!.result!!.correct)
        assertEquals(5, g.p("p1").tokens)
    }

    /** The last undecided opponent leaving must close the window instead of stalling until the deadline. */
    @Test fun leavingClosesTheChallengeWindow() {
        val g = fresh()
        g.apply("p1", Action("place", slot = 1))
        g.apply("p2", Action("pass"))
        assertEquals(Phase.CHALLENGE, g.turn!!.phase)
        val ev = g.removePlayer("p3")
        assertTrue(ev.any { it.kind == "reveal" })
        assertEquals(Phase.VOTE, g.turn!!.phase)
        assertTrue(g.turn!!.result!!.correct)
    }

    @Test fun win() {
        val g = fresh(GameOptions(challengeSeconds = 10, voteSeconds = 10, resultSeconds = 5, cardsToWin = 3))
        fun round(cur: String, slot: Int) {
            g.apply(cur, Action("place", slot = slot))
            players.map { it.id }.filter { it != cur }.forEach { g.apply(it, Action("pass")) }
            g.answerTitle(false)
            if (!g.finished) g.apply(cur, Action("continue"))
        }
        round("p1", 1); round("p2", 1); round("p3", 1)
        assertEquals(2, g.round)
        round("p1", 2)
        assertTrue(g.finished)
        assertEquals("p1", g.winnerId)
        assertThrows(GameError::class.java) { g.apply("p1", Action("continue")) }
    }

    @Test fun resultAutoAdvances() {
        val g = fresh()
        g.apply("p1", Action("place", slot = 1))
        g.apply("p2", Action("pass")); g.apply("p3", Action("pass"))
        g.answerTitle(false)
        clock += 6_000
        val ev = g.tick()
        assertEquals("p2", g.turn!!.playerId)
        assertTrue(ev.any { it.kind == "turn" })
    }

    @Test fun removeCurrentPlayer() {
        val g = fresh()
        g.removePlayer("p1")
        assertEquals(listOf("p2", "p3"), g.order)
        assertEquals("p2", g.turn!!.playerId)
        assertFalse(g.finished)
        g.removePlayer("p3")
        assertTrue(g.finished)
        assertEquals("p2", g.winnerId)
    }
}
