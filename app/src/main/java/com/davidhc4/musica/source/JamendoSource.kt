package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import com.davidhc4.musica.net.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Jamendo: música con licencia libre, canciones completas, ordenadas por popularidad. Pide un client_id gratuito. */
class JamendoSource(
    private val clientId: String,
    private val baseUrl: String = "https://api.jamendo.com/v3.0",
) : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/tracks/?client_id=$clientId&format=json&limit=$limit&search=$q" +
                "&boost=popularity_total&audioformat=mp32&imagesize=500&include=musicinfo"
            parseJamendoTracks(httpGetText(url))
        }
}

/** Convierte la respuesta de Jamendo en canciones. Un error de la API se informa como fallo de red. */
fun parseJamendoTracks(json: String): List<Track> {
    val root = JSONObject(json)
    val headers = root.optJSONObject("headers")
    if (headers != null && headers.optString("status") == "failed") {
        throw IOException("Jamendo: " + headers.optString("error_message", "error desconocido"))
    }
    val results = root.optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { index ->
        val item = results.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        val audio = item.optString("audio")
        if (id.isBlank() || audio.isBlank()) return@mapNotNull null
        Track(
            id = "jamendo:$id",
            title = item.optString("name").ifBlank { "Sin título" },
            artist = item.optString("artist_name").takeIf { it.isNotBlank() },
            album = item.optString("album_name").takeIf { it.isNotBlank() },
            uri = audio,
            durationMs = item.optLong("duration", 0L) * 1000L,
            artworkUrl = item.optString("image").takeIf { it.isNotBlank() }
                ?: item.optString("album_image").takeIf { it.isNotBlank() },
        )
    }
}
