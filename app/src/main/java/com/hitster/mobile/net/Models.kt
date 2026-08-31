package com.hitster.mobile.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

// ---------------------------------------------------------------- server → client

@Serializable
data class ServerMessage(
    val type: String,
    val roomCode: String? = null,
    val playerId: String? = null,
    val isHost: Boolean? = null,
    val room: Room? = null,
    val events: List<GameEvent>? = null,
    val message: String? = null,
    val now: Long? = null,
)

@Serializable
data class Room(
    val code: String,
    val hostId: String? = null,
    val phase: String = "lobby",
    val players: List<PlayerInfo> = emptyList(),
    val decks: List<String> = emptyList(),
    val availableDecks: List<DeckSummary> = emptyList(),
    val options: GameOptions = GameOptions(),
    val game: GameView? = null,
)

@Serializable
data class PlayerInfo(val id: String, val name: String, val color: String = "#FF2D8F", val connected: Boolean = true)

@Serializable
data class DeckSummary(val sku: String, val name: String, val subtitle: String? = null, val count: Int = 0)

@Serializable
data class GameOptions(
    val challengeSeconds: Int = 12,
    val voteSeconds: Int = 25,
    val resultSeconds: Int = 15,
    val cardsToWin: Int = 10,
    val startTokens: Int = 2,
)

@Serializable
data class GameView(
    val options: GameOptions = GameOptions(),
    val deckCount: Int = 0,
    val players: List<GamePlayer> = emptyList(),
    val order: List<String> = emptyList(),
    val turnIndex: Int = 0,
    val round: Int = 1,
    val turn: Turn? = null,
    val winnerId: String? = null,
    val finished: Boolean = false,
    val log: List<JsonObject> = emptyList(),
    val now: Long = 0,
) {
    fun player(id: String?) = players.firstOrNull { it.id == id }
    val currentPlayer get() = player(turn?.playerId)
}

@Serializable
data class GamePlayer(
    val id: String,
    val name: String,
    val color: String = "#FF2D8F",
    val tokens: Int = 0,
    val timeline: List<Card> = emptyList(),
)

@Serializable
data class Card(
    val id: String,
    val n: Int? = null,
    val title: String? = null,
    val artist: String? = null,
    val year: Int? = null,
    val preview: String? = null,
    val cover: String? = null,
    val deck: String? = null,
)

@Serializable
data class Turn(
    val playerId: String,
    val card: Card? = null,
    val phase: String = "listen",
    val slot: Int? = null,
    val challenges: List<Challenge> = emptyList(),
    val passed: List<String> = emptyList(),
    val votes: Map<String, Boolean> = emptyMap(),
    val deadline: Long? = null,
    val result: TurnResult? = null,
    val skips: Int = 0,
)

@Serializable
/** A HITSTER shout: a bet that the active player placed the card wrong. `correct` = the bet paid off. */
data class Challenge(val playerId: String, val slot: Int? = null, val correct: Boolean? = null)

@Serializable
data class TurnResult(
    val correct: Boolean,
    val stolenBy: String? = null,
    val challenges: List<Challenge> = emptyList(),
    val tokenEarned: Boolean? = null,
)

@Serializable
data class GameEvent(
    val kind: String,
    val playerId: String? = null,
    val name: String? = null,
    val slot: Int? = null,
    val card: Card? = null,
    val correct: Boolean? = null,
    val stolenBy: String? = null,
    val earned: Boolean? = null,
    val winnerId: String? = null,
    val reason: String? = null,
    val index: Int? = null,
)

object Phase {
    const val LISTEN = "listen"
    const val CHALLENGE = "challenge"
    const val VOTE = "vote"
    const val RESULT = "result"
}

// ---------------------------------------------------------------- client → server

@Serializable
data class Action(
    val type: String,
    val slot: Int? = null,
    val value: Boolean? = null,
)

@Serializable
data class ClientMessage(
    val type: String,
    val name: String? = null,
    val color: String? = null,
    /** join/kick: who. `start`: who plays first (null = random). */
    val playerId: String? = null,
    val code: String? = null,
    val decks: List<String>? = null,
    val options: GameOptions? = null,
    val action: Action? = null,
)
