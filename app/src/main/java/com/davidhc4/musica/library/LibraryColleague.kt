package com.davidhc4.musica.library

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface LibraryStore {
    suspend fun load(): List<Track>
    suspend fun save(tracks: List<Track>)
}

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val loaded: Boolean = false,
)

/** Guarda todas las canciones del usuario y las conserva entre sesiones. */
class LibraryColleague(private val store: LibraryStore) : BaseColleague() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.AppStarted -> {
                _state.value = LibraryState(sorted(store.load()), loaded = true)
            }

            is AppEvent.AddToLibrary -> {
                val known = _state.value.tracks.map { it.id }.toSet()
                val fresh = event.tracks.filter { it.id !in known }.distinctBy { it.id }
                if (fresh.isEmpty()) return
                commit(sorted(_state.value.tracks + fresh))
            }

            is AppEvent.RemoveFromLibrary -> {
                val remaining = _state.value.tracks.filter { it.id != event.trackId }
                if (remaining.size == _state.value.tracks.size) return
                commit(remaining)
            }

            else -> Unit
        }
    }

    private suspend fun commit(tracks: List<Track>) {
        _state.value = _state.value.copy(tracks = tracks)
        store.save(tracks)
    }

    private fun sorted(tracks: List<Track>) = tracks.sortedBy { it.title.lowercase() }
}
