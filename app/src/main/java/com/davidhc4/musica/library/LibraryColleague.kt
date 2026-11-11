package com.davidhc4.musica.library

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.LibrarySort
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface LibraryStore {
    suspend fun load(): List<Track>
    suspend fun save(tracks: List<Track>)
}

data class LibraryState(
    /** Las canciones en orden alfabético. */
    val tracks: List<Track> = emptyList(),
    val loaded: Boolean = false,
    /** Las mismas canciones, de la agregada más recientemente a la más antigua. */
    val recent: List<Track> = emptyList(),
)

/** Devuelve la biblioteca en el orden elegido en la pantalla. */
fun sortLibrary(state: LibraryState, sort: LibrarySort): List<Track> = when (sort) {
    LibrarySort.TITLE -> state.tracks
    LibrarySort.RECENT -> state.recent
    // Las que no tienen artista van al final.
    LibrarySort.ARTIST -> state.tracks.sortedWith(
        compareBy<Track> { it.artist == null }.thenBy { it.artist?.normalized().orEmpty() }
    )
}

/** Guarda todas las canciones del usuario y las conserva entre sesiones. */
class LibraryColleague(private val store: LibraryStore) : BaseColleague() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.AppStarted -> {
                added = store.load().playable()
                _state.value = LibraryState(sorted(added), loaded = true, recent = added.reversed())
            }

            is AppEvent.AddToLibrary -> {
                val known = added.map { it.id }.toSet()
                val fresh = event.tracks.filter { it.id !in known }.distinctBy { it.id }
                if (fresh.isEmpty()) return
                commit(added + fresh)
            }

            is AppEvent.RemoveFromLibrary -> {
                val remaining = added.filter { it.id != event.trackId }
                if (remaining.size == added.size) return
                commit(remaining)
            }

            else -> Unit
        }
    }

    /** Las canciones en el orden en que se agregaron; así se guardan. */
    private var added: List<Track> = emptyList()

    private suspend fun commit(tracks: List<Track>) {
        added = tracks
        _state.value = _state.value.copy(tracks = sorted(tracks), recent = tracks.reversed())
        store.save(tracks)
    }

    private fun sorted(tracks: List<Track>) = tracks.sortedBy { it.title.lowercase() }
}
