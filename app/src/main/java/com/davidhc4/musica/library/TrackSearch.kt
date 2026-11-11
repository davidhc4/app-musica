package com.davidhc4.musica.library

import com.davidhc4.musica.core.Track
import java.text.Normalizer

private val accents = Regex("\\p{M}+")

fun String.normalized(): String =
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

/** Un grupo de canciones con nombre: las de un artista o las de un álbum. */
data class TrackGroup(val name: String, val tracks: List<Track>)

fun groupByArtist(tracks: List<Track>): List<TrackGroup> = groupTracks(tracks, "Artista desconocido") { it.artist }

fun groupByAlbum(tracks: List<Track>): List<TrackGroup> = groupTracks(tracks, "Álbum desconocido") { it.album }

/**
 * Agrupa sin distinguir mayúsculas ni acentos, ordena los grupos por nombre y deja al final las
 * canciones que no traen el dato.
 */
private fun groupTracks(tracks: List<Track>, unknown: String, key: (Track) -> String?): List<TrackGroup> {
    val (named, unnamed) = tracks.partition { !key(it).isNullOrBlank() }
    val groups = named.groupBy { key(it)!!.trim().normalized() }
        .map { (_, list) -> TrackGroup(list.first().let { key(it)!!.trim() }, list.sortedBy { it.title.lowercase() }) }
        .sortedBy { it.name.normalized() }
    return if (unnamed.isEmpty()) groups else groups + TrackGroup(unknown, unnamed)
}

/**
 * Quita las canciones guardadas por versiones anteriores que venían de YouTube: la app ya no las
 * puede reproducir.
 */
fun List<Track>.playable(): List<Track> = filterNot { it.uri.startsWith("youtube:") }
