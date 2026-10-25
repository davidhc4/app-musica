package com.davidhc4.musica.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.davidhc4.musica.core.Track

/** Lee título, artista, álbum y duración del archivo. Si no los tiene, usa el nombre del archivo. */
fun readTrack(context: Context, uri: Uri): Track {
    // Permite volver a abrir el archivo después de reiniciar la app.
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (_: Exception) {
    }

    val fileName = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment
        ?: "Canción"

    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var duration = 0L

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
    } finally {
        retriever.release()
    }

    return Track(
        id = uri.toString(),
        title = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
        artist = artist?.takeIf { it.isNotBlank() },
        uri = uri.toString(),
        album = album?.takeIf { it.isNotBlank() },
        durationMs = duration,
    )
}
