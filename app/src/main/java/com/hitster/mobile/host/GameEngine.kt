package com.hitster.mobile.host

import com.hitster.mobile.net.Action
import com.hitster.mobile.net.Card
import com.hitster.mobile.net.Challenge
import com.hitster.mobile.net.GameEvent
import com.hitster.mobile.net.GameOptions
import com.hitster.mobile.net.GamePlayer
import com.hitster.mobile.net.GameView
import com.hitster.mobile.net.Phase
import com.hitster.mobile.net.PlayerInfo
import com.hitster.mobile.net.Turn
import com.hitster.mobile.net.TurnResult
import kotlin.random.Random

/** Illegal move – the message is shown to the player as‑is (pt‑BR). */
class GameError(message: String) : Exception(message)

/**
 * Pure Hitster rules engine (port of server/src/game.js). Runs on the host phone.
 *
 *  • Each player starts with 2 HITSTER tokens and 1 face‑up card.
 *  • Turn: listen → place → (challenge window) → reveal → (vote on title/artist) → result.
 *  • Token 1 – your turn: skip the song (card to the bottom of the pile).
 *  • Token 2 – opponent's turn: shout HITSTER before the reveal = bet that the placement is wrong. The token is
 *    spent either way; if the owner was wrong the (first) challenger takes the card into their own timeline.
 *  • Token 3 – any time: 3 tokens for the top card, placed correctly.
 *  • +1 token for naming title + artist (max 5). First to `cardsToWin` cards wins.
 */
class GameEngine(
    players: List<PlayerInfo>,
    cards: List<Card>,
    val options: GameOptions,
    private val random: Random = Random.Default,
    private val now: () -> Long = System::currentTimeMillis,
) {
    class P(val id: String, val name: String, val color: String, var tokens: Int, val timeline: MutableList<Card>)

    class TurnState(
        val playerId: String,
        var card: Card,
        var phase: String = Phase.LISTEN,
        var slot: Int? = null,
        var claimsTitle: Boolean = false,
        val challenges: MutableList<Challenge> = mutableListOf(),
        val passed: MutableList<String> = mutableListOf(),
        val votes: MutableMap<String, Boolean> = mutableMapOf(),
        var deadline: Long? = null,
        var result: TurnResult? = null,
        var skips: Int = 0,
    )

    val deck: ArrayDeque<Card>
    val discard = ArrayDeque<Card>()
    val players: MutableList<P>
    val order: MutableList<String>
    var turnIndex = 0
    var round = 1
    var turn: TurnState? = null
    var winnerId: String? = null
    var finished = false

    init {
        if (players.size < 2) throw GameError("São necessários pelo menos 2 jogadores.")
        deck = ArrayDeque(cards.shuffled(random))
        if (deck.size < players.size + 10) throw GameError("Baralho pequeno demais.")
        this.players = players.map { P(it.id, it.name, it.color, options.startTokens, mutableListOf(deck.removeFirst())) }.toMutableList()
        order = players.map { it.id }.toMutableList()
        startTurn()
    }

    // ---------------------------------------------------------------- helpers

    private fun player(id: String): P = players.firstOrNull { it.id == id } ?: throw GameError("Jogador desconhecido.")
    private fun opponents(): List<P> = players.filter { it.id != turn!!.playerId }

    private fun drawCard(): Card? {
        if (deck.isEmpty()) {
            if (discard.isEmpty()) return null
            deck.addAll(discard.shuffled(random)); discard.clear()
        }
        return deck.removeFirst()
    }

    private fun startTurn() {
        val card = drawCard() ?: return finish(null)
        turn = TurnState(playerId = order[turnIndex], card = card)
    }

    private fun finish(winner: String?) {
        finished = true
        winnerId = winner
        turn?.let { it.phase = Phase.RESULT; it.deadline = null }
    }

    // ---------------------------------------------------------------- actions

    fun apply(playerId: String, action: Action): List<GameEvent> {
        if (finished) throw GameError("O jogo já terminou.")
        val t = turn ?: throw GameError("Sem rodada ativa.")
        val me = player(playerId)
        val isCurrent = t.playerId == playerId
        val events = mutableListOf<GameEvent>()

        when (action.type) {
            "skip" -> {
                if (!isCurrent || t.phase != Phase.LISTEN) throw GameError("Só é possível pular na sua vez, antes de posicionar.")
                if (me.tokens < 1) throw GameError("Você precisa de 1 ficha HITSTER para pular a música.")
                me.tokens -= 1
                deck.addLast(t.card)
                val next = drawCard() ?: return finishByCount(events)
                t.card = next
                t.skips += 1
                events += GameEvent(kind = "skip", playerId = playerId)
            }

            "claimTitle" -> {
                if (!isCurrent || t.phase != Phase.LISTEN) throw GameError("Ação indisponível.")
                t.claimsTitle = action.value == true
            }

            "place" -> {
                if (!isCurrent || t.phase != Phase.LISTEN) throw GameError("Não é sua vez de posicionar.")
                val slot = action.slot ?: throw GameError("Posição inválida.")
                if (slot < 0 || slot > me.timeline.size) throw GameError("Posição inválida.")
                t.slot = slot
                action.claimsTitle?.let { t.claimsTitle = it }
                t.phase = Phase.CHALLENGE
                t.deadline = now() + options.challengeSeconds * 1000L
                events += GameEvent(kind = "placed", playerId = playerId, slot = slot)
            }

            "challenge" -> {
                if (isCurrent) throw GameError("Você não pode desafiar a si mesmo.")
                if (t.phase != Phase.CHALLENGE) throw GameError("O desafio só vale antes da carta ser revelada.")
                if (me.tokens < 1) throw GameError("Você precisa de 1 ficha HITSTER para desafiar.")
                if (t.challenges.any { it.playerId == playerId }) throw GameError("Você já desafiou nesta rodada.")
                // A bet that the active player is wrong. The token is spent either way (house rule).
                me.tokens -= 1
                t.challenges += Challenge(playerId)
                t.passed.remove(playerId)
                events += GameEvent(kind = "challenge", playerId = playerId)
                if (everyoneDecided()) reveal(events)
            }

            "pass" -> {
                if (isCurrent || t.phase != Phase.CHALLENGE) throw GameError("Ação indisponível.")
                if (playerId !in t.passed && t.challenges.none { it.playerId == playerId }) t.passed += playerId
                if (everyoneDecided()) reveal(events)
            }

            "vote" -> {
                if (isCurrent || t.phase != Phase.VOTE) throw GameError("Ação indisponível.")
                t.votes[playerId] = action.value == true
                if (opponents().all { it.id in t.votes }) resolveVote(events)
            }

            "buyCard" -> {
                if (me.tokens < 3) throw GameError("Você precisa de 3 fichas HITSTER.")
                val card = drawCard() ?: throw GameError("O baralho acabou.")
                me.tokens -= 3
                val idx = insertSorted(me.timeline, card)
                events += GameEvent(kind = "bought", playerId = playerId, card = card, index = idx)
                if (checkWin()) events += GameEvent(kind = "finished", winnerId = winnerId)
            }

            "continue" -> {
                if (t.phase != Phase.RESULT) throw GameError("Ação indisponível.")
                nextTurn(events)
            }

            else -> throw GameError("Ação desconhecida: ${action.type}")
        }
        return events
    }

    /** Called by the host when `turn.deadline` has passed. */
    fun tick(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val t = turn ?: return events
        val dl = t.deadline ?: return events
        if (finished || now() < dl) return events
        when (t.phase) {
            Phase.CHALLENGE -> reveal(events)
            Phase.VOTE -> resolveVote(events)
            Phase.RESULT -> nextTurn(events)
        }
        return events
    }

    private fun everyoneDecided(): Boolean {
        val t = turn!!
        return opponents().all { p -> p.id in t.passed || t.challenges.any { it.playerId == p.id } }
    }

    private fun reveal(events: MutableList<GameEvent>) {
        val t = turn!!
        val owner = player(t.playerId)
        val card = t.card
        val slot = t.slot ?: 0
        val correct = fits(owner.timeline, slot, card.year ?: 0)
        var stolenBy: String? = null
        // every challenger bet on a mistake: the bet pays off iff the owner was wrong
        val results = t.challenges.map { it.copy(correct = !correct) }
        if (correct) {
            owner.timeline.add(slot, card)
        } else {
            val winner = t.challenges.firstOrNull() // first to shout takes the card
            if (winner != null) {
                stolenBy = winner.playerId
                insertSorted(player(stolenBy).timeline, card)
            } else discard.addLast(card)
        }
        t.result = TurnResult(correct = correct, stolenBy = stolenBy, challenges = results, tokenEarned = null)
        events += GameEvent(kind = "reveal", playerId = t.playerId, card = card, correct = correct, stolenBy = stolenBy)

        if (t.claimsTitle && owner.tokens < MAX_TOKENS && opponents().isNotEmpty()) {
            t.phase = Phase.VOTE
            t.deadline = now() + options.voteSeconds * 1000L
        } else {
            t.result = t.result!!.copy(tokenEarned = false)
            toResult(events)
        }
    }

    private fun resolveVote(events: MutableList<GameEvent>) {
        val t = turn!!
        val owner = player(t.playerId)
        val yes = t.votes.values.count { it }
        val no = t.votes.size - yes
        val earned = t.votes.isNotEmpty() && yes >= no
        if (earned && owner.tokens < MAX_TOKENS) owner.tokens += 1
        t.result = (t.result ?: TurnResult(correct = false)).copy(tokenEarned = earned)
        events += GameEvent(kind = "vote", playerId = t.playerId, earned = earned)
        toResult(events)
    }

    private fun toResult(events: MutableList<GameEvent>) {
        val t = turn!!
        t.phase = Phase.RESULT
        if (checkWin()) {
            t.deadline = null
            events += GameEvent(kind = "finished", winnerId = winnerId)
        } else t.deadline = now() + options.resultSeconds * 1000L
    }

    private fun checkWin(): Boolean {
        val reached = players.filter { it.timeline.size >= options.cardsToWin }
        if (reached.isEmpty()) return false
        val current = turn?.playerId
        val best = reached.sortedWith(compareByDescending<P> { it.timeline.size }.thenBy { if (it.id == current) 0 else 1 }).first()
        finish(best.id)
        return true
    }

    private fun finishByCount(events: MutableList<GameEvent>): List<GameEvent> {
        val best = players.maxByOrNull { it.timeline.size }!!
        finish(best.id)
        events += GameEvent(kind = "finished", winnerId = winnerId, reason = "deck")
        return events
    }

    private fun nextTurn(events: MutableList<GameEvent>) {
        turnIndex = (turnIndex + 1) % order.size
        if (turnIndex == 0) round += 1
        startTurn()
        if (finished) events += GameEvent(kind = "finished", winnerId = winnerId, reason = "deck")
        else events += GameEvent(kind = "turn", playerId = turn!!.playerId)
    }

    /** Remove a player who left for good. */
    fun removePlayer(playerId: String) {
        val idx = order.indexOf(playerId)
        if (idx < 0) return
        val wasCurrent = turn?.playerId == playerId
        order.removeAt(idx)
        players.removeAll { it.id == playerId }
        if (order.size < 2) { finish(order.firstOrNull()); return }
        if (idx < turnIndex) turnIndex -= 1
        if (wasCurrent) {
            turn?.card?.let { discard.addLast(it) }
            turnIndex %= order.size
            startTurn()
        } else turn?.let { t ->
            t.challenges.removeAll { it.playerId == playerId }
            t.passed.remove(playerId)
            t.votes.remove(playerId)
        }
    }

    /** Snapshot for one client – the current card is hidden until the reveal. */
    fun view(forPlayerId: String): GameView {
        val t = turn
        val turnView = t?.let {
            val revealed = it.phase == Phase.VOTE || it.phase == Phase.RESULT
            val card = when {
                revealed -> it.card
                it.playerId == forPlayerId -> Card(id = it.card.id, preview = it.card.preview)
                else -> null
            }
            Turn(
                playerId = it.playerId, card = card, phase = it.phase, slot = it.slot, claimsTitle = it.claimsTitle,
                challenges = it.challenges.toList(), passed = it.passed.toList(), votes = it.votes.toMap(),
                deadline = it.deadline, result = it.result, skips = it.skips,
            )
        }
        return GameView(
            options = options,
            deckCount = deck.size,
            players = players.map { GamePlayer(it.id, it.name, it.color, it.tokens, it.timeline.toList()) },
            order = order.toList(),
            turnIndex = turnIndex,
            round = round,
            turn = turnView,
            winnerId = winnerId,
            finished = finished,
            now = now(),
        )
    }

    companion object {
        const val MAX_TOKENS = 5

        /** True when `year` can sit at `slot` (0..timeline.size). Equal years may sit on either side. */
        fun fits(timeline: List<Card>, slot: Int, year: Int): Boolean {
            val left = if (slot > 0) timeline[slot - 1].year ?: Int.MIN_VALUE else Int.MIN_VALUE
            val right = if (slot < timeline.size) timeline[slot].year ?: Int.MAX_VALUE else Int.MAX_VALUE
            return left <= year && year <= right
        }

        /** Insert in chronological order (stolen / bought cards). Returns the index. */
        fun insertSorted(timeline: MutableList<Card>, card: Card): Int {
            val y = card.year ?: 0
            var i = 0
            while (i < timeline.size && (timeline[i].year ?: 0) <= y) i++
            timeline.add(i, card)
            return i
        }
    }
}
