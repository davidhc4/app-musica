package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import com.davidhc4.musica.net.httpGetText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Las canciones de YouTube se guardan como "youtube:ID" y las reproduce el reproductor oficial. */
const val YOUTUBE_PREFIX = "youtube:"

fun isYouTubeUri(uri: String?): Boolean = uri?.startsWith(YOUTUBE_PREFIX) == true

/**
 * Busca videos de música en YouTube con su API oficial. Necesita una llave gratuita de Google,
 * que cada persona escribe en Ajustes. Cada búsqueda gasta parte del cupo diario de esa llave,
 * así que las búsquedas repetidas se responden de memoria.
 */
class YouTubeSource(
    private val key: () -> String,
    private val baseUrl: String = "https://www.googleapis.com/youtube/v3",
    private val fetch: (String) -> String = { httpGetText(it) },
) : MusicSource {

    private val cache = object : LinkedHashMap<String, List<Track>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Track>>?) = size > 30
    }

    override suspend fun search(query: String, limit: Int): List<Track> {
        val apiKey = key().trim()
        check(apiKey.isNotEmpty()) { "Falta la llave de YouTube" }
        val cacheKey = "$apiKey|${query.trim().lowercase()}|$limit"
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val result = withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val k = URLEncoder.encode(apiKey, "UTF-8")
            val found = parseYouTubeSearch(
                fetch(
                    "$baseUrl/search?part=snippet&type=video&videoCategoryId=10&videoEmbeddable=true" +
                        "&videoSyndicated=true&maxResults=${limit.coerceIn(1, 50)}&q=$q&key=$k"
                )
            )
            if (found.isEmpty()) return@withContext found

            // La búsqueda no trae la duración; se pide aparte. Si falla, se muestran sin ella.
            val durations = try {
                val ids = found.joinToString(",") { it.id.removePrefix(YOUTUBE_PREFIX) }
                parseYouTubeDurations(fetch("$baseUrl/videos?part=contentDetails&id=$ids&key=$k"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (durations == null) found else withDurations(found, durations)
        }
        synchronized(cache) { cache[cacheKey] = result }
        return result
    }
}

/** Pone la duración a cada canción y quita lo que no es una canción: directos, clips y mezclas largas. */
fun withDurations(tracks: List<Track>, durations: Map<String, Long>): List<Track> =
    tracks.map { it.copy(durationMs = durations[it.id.removePrefix(YOUTUBE_PREFIX)] ?: 0L) }
        .filter { it.durationMs in 60_000L..900_000L }

fun parseYouTubeSearch(json: String): List<Track> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull { index ->
        val item = items.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
        if (!isYouTubeVideoId(id)) return@mapNotNull null
        val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
        val (title, artist) = splitVideoTitle(
            decodeHtml(snippet.optString("title")),
            decodeHtml(snippet.optString("channelTitle")),
        )
        val thumbs = snippet.optJSONObject("thumbnails")
        Track(
            id = "$YOUTUBE_PREFIX$id",
            title = title,
            artist = artist,
            uri = "$YOUTUBE_PREFIX$id",
            artworkUrl = listOf("high", "medium", "default")
                .firstNotNullOfOrNull { size -> thumbs?.optJSONObject(size)?.optString("url")?.takeIf { it.isNotBlank() } },
        )
    }.distinctBy { it.id }
}

fun parseYouTubeDurations(json: String): Map<String, Long> {
    val items = JSONObject(json).optJSONArray("items") ?: return emptyMap()
    return (0 until items.length()).mapNotNull { index ->
        val item = items.optJSONObject(index) ?: return@mapNotNull null
        val duration = item.optJSONObject("contentDetails")?.optString("duration").orEmpty()
        item.optString("id") to parseIsoDuration(duration)
    }.toMap()
}

fun isYouTubeVideoId(id: String): Boolean = Regex("[A-Za-z0-9_-]{11}").matches(id)

private val isoDuration = Regex("P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?")

/** Convierte duraciones como "PT3M53S" a milisegundos. Devuelve 0 si no se entiende. */
fun parseIsoDuration(text: String): Long {
    val match = isoDuration.matchEntire(text) ?: return 0L
    val (days, hours, minutes, seconds) = match.groupValues.drop(1).map { it.toLongOrNull() ?: 0L }
    return (((days * 24 + hours) * 60 + minutes) * 60 + seconds) * 1000L
}

private val noise = Regex(
    "\\s*[(\\[][^)\\]]*(official|oficial|video|v[ií]deo|lyric|letra|audio|visualizer|4k|hd)[^)\\]]*[)\\]]",
    RegexOption.IGNORE_CASE,
)

/**
 * Los videos suelen llamarse "Artista - Canción (Video Oficial)". Esto separa artista y canción y
 * quita los añadidos, para que se lea como en un reproductor de música.
 */
fun splitVideoTitle(rawTitle: String, channel: String): Pair<String, String?> {
    val clean = rawTitle.replace(noise, "").trim().ifBlank { rawTitle.trim() }
    val channelName = channel.removeSuffix(" - Topic").removeSuffix("VEVO").trim().takeIf { it.isNotBlank() }
    val parts = clean.split(Regex("\\s[-–—]\\s"), limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        parts[1].trim() to parts[0].trim()
    } else {
        clean.ifBlank { "Sin título" } to channelName
    }
}

fun decodeHtml(text: String): String = text
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
