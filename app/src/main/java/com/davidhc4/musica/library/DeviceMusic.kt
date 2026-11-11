package com.davidhc4.musica.library

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.davidhc4.musica.core.Track

/**
 * Lee del sistema toda la música guardada en el celular. Deja fuera tonos, notas de voz y audios
 * de menos de medio minuto. Necesita el permiso de lectura de audio.
 */
fun scanDeviceMusic(context: Context): List<Track> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val columns = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
    )
    val found = ArrayList<Track>()
    context.contentResolver.query(
        collection,
        columns,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        null,
    )?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (cursor.moveToNext()) {
            val durationMs = cursor.getLong(duration)
            if (durationMs < 30_000L) continue
            val uri = ContentUris.withAppendedId(collection, cursor.getLong(id)).toString()
            found += Track(
                id = uri,
                title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: "Sin título",
                artist = cursor.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" },
                uri = uri,
                album = cursor.getString(album)?.takeIf { it.isNotBlank() && it != "<unknown>" },
                durationMs = durationMs,
            )
        }
    }
    return found
}

/**
 * De lo encontrado en el celular, deja solo lo que aún no está en la biblioteca. Una canción
 * agregada antes a mano tiene otra dirección, así que también se compara por título y duración.
 */
fun newDeviceTracks(found: List<Track>, library: List<Track>): List<Track> {
    fun fingerprint(t: Track) = t.title.normalized() + "|" + t.durationMs / 2000
    val ids = library.map { it.id }.toSet()
    val known = library.map(::fingerprint).toSet()
    return found.filter { it.id !in ids && fingerprint(it) !in known }.distinctBy { it.id }
}
