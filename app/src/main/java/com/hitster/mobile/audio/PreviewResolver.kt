package com.hitster.mobile.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves the 30‑second Spotify preview MP3 for a track – the same clip the Spotify embed player
 * uses, and the same length the physical HITSTER game plays.
 *
 * Spotify removed `preview_url` from the public Web API (Nov 2024), but the embed page
 * https://open.spotify.com/embed/track/<id> still ships the preview inside its __NEXT_DATA__ JSON:
 *   entity.audioPreview.url = https://p.scdn.co/mp3-preview/<hash>
 * No API key or login is needed. The catalog also stores the URL captured at build time as a fallback.
 */
class PreviewResolver {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val cache = ConcurrentHashMap<String, String>()
    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolve(trackId: String, fallback: String? = null): String? = withContext(Dispatchers.IO) {
        cache[trackId]?.let { return@withContext it }
        val fresh = runCatching { fetchFromEmbed(trackId) }.getOrNull()
        val url = fresh ?: fallback
        if (url != null) cache[trackId] = url
        url
    }

    private fun fetchFromEmbed(trackId: String): String? {
        val req = Request.Builder()
            .url("https://open.spotify.com/embed/track/$trackId")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val html = res.body?.string() ?: return null
            val start = html.indexOf("<script id=\"__NEXT_DATA__\" type=\"application/json\">")
            if (start >= 0) {
                val from = html.indexOf('>', start) + 1
                val end = html.indexOf("</script>", from)
                val data = lenient.parseToJsonElement(html.substring(from, end)).jsonObject
                val entity = data["props"]?.jsonObject?.get("pageProps")?.jsonObject
                    ?.get("state")?.jsonObject?.get("data")?.jsonObject?.get("entity")?.jsonObject
                val url = entity?.get("audioPreview")?.jsonObject?.get("url")?.jsonPrimitive?.content
                if (!url.isNullOrBlank()) return url
            }
            // Last resort: a plain regex over the page.
            return Regex("https://p\\.scdn\\.co/mp3-preview/[A-Za-z0-9]+").find(html)?.value
        }
    }
}
