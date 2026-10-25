package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.normalized
import com.davidhc4.musica.net.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Una fuente de música en línea. Se pueden sumar otras (Jamendo, Internet Archive) sin tocar lo demás. */
interface MusicSource {
    suspend fun search(query: String, limit: Int = 25): List<Track>
}

/** Audius: plataforma abierta de música independiente, con API gratuita para reproducir. */
class AudiusSource(
    private val appName: String = "Musica",
    private val baseUrl: String = "https://api.audius.co",
) : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/v1/tracks/search?query=$q&limit=$limit&app_name=$appName"
            rankAudiusTracks(httpGetText(url), baseUrl, appName, query, limit)
        }
}

/** Una canción de Audius junto con lo que sirve para ordenarla. */
private class Candidate(val track: Track, val verified: Boolean, val plays: Long)

/**
 * Deja solo canciones de verdad (no mezclas de horas ni fragmentos de segundos) que coinciden con lo
 * buscado, con los artistas verificados primero y luego las más reproducidas.
 */
fun rankAudiusTracks(json: String, baseUrl: String, appName: String, query: String, limit: Int): List<Track> {
    val words = query.normalized().split(' ').filter { it.isNotBlank() }
    return candidates(json, baseUrl, appName)
        .filter { it.track.durationMs in 60_000L..900_000L }
        .filter { c ->
            val text = "${c.track.title} ${c.track.artist.orEmpty()}".normalized()
            words.all { it in text }
        }
        .sortedWith(compareByDescending<Candidate> { it.verified }.thenByDescending { it.plays })
        .distinctBy { "${it.track.title.normalized()}|${it.track.artist.orEmpty().normalized()}" }
        .take(limit)
        .map { it.track }
}

/** Convierte la respuesta de búsqueda de Audius en canciones. Ignora las que no se pueden reproducir. */
fun parseAudiusTracks(json: String, baseUrl: String, appName: String): List<Track> =
    candidates(json, baseUrl, appName).map { it.track }

private fun candidates(json: String, baseUrl: String, appName: String): List<Candidate> {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    return (0 until data.length()).mapNotNull { index ->
        val item = data.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) return@mapNotNull null
        if (item.has("is_streamable") && !item.optBoolean("is_streamable", true)) return@mapNotNull null
        val access = item.optJSONObject("access")
        if (access != null && !access.optBoolean("stream", true)) return@mapNotNull null

        val artwork = item.optJSONObject("artwork")
        val track = Track(
            id = "audius:$id",
            title = item.optString("title").ifBlank { "Sin título" },
            artist = item.optJSONObject("user")?.optString("name")?.takeIf { it.isNotBlank() },
            uri = "$baseUrl/v1/tracks/$id/stream?app_name=$appName",
            durationMs = item.optLong("duration", 0L) * 1000L,
            artworkUrl = artwork?.optString("480x480")?.takeIf { it.isNotBlank() }
                ?: artwork?.optString("150x150")?.takeIf { it.isNotBlank() },
        )
        Candidate(
            track,
            verified = item.optJSONObject("user")?.optBoolean("is_verified", false) ?: false,
            plays = item.optLong("play_count", 0L),
        )
    }
}
