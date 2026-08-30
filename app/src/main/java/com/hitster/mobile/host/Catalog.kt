package com.hitster.mobile.host

import android.content.Context
import com.hitster.mobile.net.Card
import com.hitster.mobile.net.DeckSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Deck(val sku: String, val name: String, val subtitle: String? = null, val cards: List<Card> = emptyList()) {
    val summary get() = DeckSummary(sku, name, subtitle, cards.count { it.year != null })
}

/** The official decks bundled as assets (one JSON file per deck). */
class Catalog(val decks: Map<String, Deck>) {
    val summaries: List<DeckSummary> get() = decks.values.sortedBy { it.sku }.map { it.summary }

    /** Unique, playable cards of the chosen decks. */
    fun cardsFor(skus: Collection<String>): List<Card> {
        val seen = HashSet<String>()
        val out = ArrayList<Card>()
        for (sku in skus) for (c in decks[sku]?.cards.orEmpty()) {
            if (c.year == null || !seen.add(c.id)) continue
            out += c.copy(deck = sku)
        }
        return out
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(context: Context): Catalog {
            val am = context.assets
            val decks = LinkedHashMap<String, Deck>()
            for (name in am.list("")?.sorted().orEmpty()) {
                if (!name.endsWith(".json") || name.startsWith("_")) continue
                val deck = runCatching { am.open(name).bufferedReader().use { json.decodeFromString(Deck.serializer(), it.readText()) } }.getOrNull() ?: continue
                if (deck.cards.isNotEmpty()) decks[deck.sku] = deck
            }
            return Catalog(decks)
        }
    }
}
