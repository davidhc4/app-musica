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
        else runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    override suspend fun save(tracks: List<Track>) = withContext(Dispatchers.IO) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(toJson(tracks))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }

    private fun toJson(tracks: List<Track>): String {
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
                }
            )
        }
        return array.toString()
    }

    private fun parse(text: String): List<Track> {
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Track(
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.stringOrNull("artist"),
                uri = o.getString("uri"),
                album = o.stringOrNull("album"),
                durationMs = o.optLong("durationMs", 0L),
            )
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)
}
