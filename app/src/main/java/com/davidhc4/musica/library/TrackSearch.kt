package com.davidhc4.musica.library

import com.davidhc4.musica.core.Track
import java.text.Normalizer

private val accents = Regex("\\p{M}+")

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(accents, "")

/**
 * Busca por título, artista o álbum sin importar mayúsculas ni acentos.
 * Cada palabra escrita debe aparecer en alguna parte; los que empiezan igual van primero.
 */
fun searchTracks(tracks: List<Track>, query: String): List<Track> {
    val words = query.normalized().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return tracks
        .filter { track ->
            val text = listOfNotNull(track.title, track.artist, track.album)
                .joinToString(" ")
                .normalized()
            words.all { it in text }
        }
        .sortedBy { if (it.title.normalized().startsWith(words.first())) 0 else 1 }
}
