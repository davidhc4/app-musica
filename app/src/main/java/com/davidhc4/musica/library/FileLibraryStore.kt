package com.davidhc4.musica.library

import com.davidhc4.musica.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda la biblioteca en un archivo JSON. Escribe primero a un archivo temporal y luego lo
 * renombra, así un cierre inesperado nunca deja la biblioteca a medias.
 */
class FileLibraryStore(private val file: File) : LibraryStore {

    override suspend fun load(): List<Track> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList()
        else runCatching { tracksFromJson(JSONArray(file.readText())) }.getOrDefault(emptyList())
    }

    override suspend fun save(tracks: List<Track>) = withContext(Dispatchers.IO) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(tracksToJson(tracks).toString())
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }
}

/** Convierte canciones a JSON. Lo comparten la biblioteca, las descargas y las listas. */
fun tracksToJson(tracks: List<Track>): JSONArray {
    val array = JSONArray()
    tracks.forEach { t ->
        array.put(
            JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("artist", t.artist ?: JSONObject.NULL)
                put("album", t.album ?: JSONObject.NULL)
                put("uri", t.uri)
                put("durationMs", t.durationMs)
                put("artworkUrl", t.artworkUrl ?: JSONObject.NULL)
                put("isPreview", t.isPreview)
            }
        )
    }
    return array
}

fun tracksFromJson(array: JSONArray): List<Track> =
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Track(
            id = o.getString("id"),
            title = o.getString("title"),
            artist = o.stringOrNull("artist"),
            uri = o.getString("uri"),
            album = o.stringOrNull("album"),
            durationMs = o.optLong("durationMs", 0L),
            artworkUrl = o.stringOrNull("artworkUrl"),
            isPreview = o.optBoolean("isPreview", false),
        )
    }

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else getString(key)
