package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.normalized
import com.davidhc4.musica.net.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Largo del fragmento que Deezer permite escuchar de cada canción. */
private const val PREVIEW_MS = 30_000L

/**
 * Deezer: catálogo de música comercial. Su API gratuita solo permite escuchar un fragmento de
 * 30 segundos de cada canción; la canción completa solo se oye dentro de Deezer con cuenta.
 */
class DeezerSource(private val baseUrl: String = "https://api.deezer.com") : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            parseDeezerTracks(httpGetText("$baseUrl/search?q=$q&limit=60"), query, limit)
        }
}

private val junkWords = listOf("karaoke", "tribute", "made famous", "originally performed")

/**
 * Convierte la respuesta de Deezer en canciones y deja solo las que valen la pena:
 * - que coincidan de verdad con lo buscado (título, artista o álbum),
 * - sin karaokes ni tributos,
 * - una sola versión de cada canción (la más escuchada),
 * - ordenadas de la más reproducida a la menos.
 */
fun parseDeezerTracks(json: String, query: String, limit: Int): List<Track> {
    val root = JSONObject(json)
    root.optJSONObject("error")?.let { throw IOException(it.optString("message", "Error de Deezer")) }
    val data = root.optJSONArray("data") ?: return emptyList()

    val normalizedQuery = query.normalized()
    val words = normalizedQuery.split(' ').filter { it.isNotBlank() }
    val skipped = junkWords.filter { it !in normalizedQuery }

    return (0 until data.length())
        .mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("id", 0L)
            val preview = item.optString("preview")
            if (id == 0L || preview.isBlank()) return@mapNotNull null
            if (item.has("readable") && !item.optBoolean("readable", true)) return@mapNotNull null

            val album = item.optJSONObject("album")
            val track = Track(
                id = "deezer:$id",
                title = item.optString("title").ifBlank { "Sin título" },
                artist = item.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
                uri = preview,
                album = album?.optString("title")?.takeIf { it.isNotBlank() },
                durationMs = PREVIEW_MS,
                artworkUrl = album?.optString("cover_big")?.takeIf { it.isNotBlank() }
                    ?: album?.optString("cover_medium")?.takeIf { it.isNotBlank() },
                isPreview = true,
            )
            track to item.optLong("rank", 0L)
        }
        .filter { (track, _) ->
            val text = listOfNotNull(track.title, track.artist, track.album)
                .joinToString(" ")
                .normalized()
            words.all { it in text } && skipped.none { it in text }
        }
        .sortedByDescending { (_, rank) -> rank }
        .distinctBy { (track, _) -> track.title.normalized() + "|" + (track.artist ?: "").normalized() }
        .take(limit)
        .map { (track, _) -> track }
}
