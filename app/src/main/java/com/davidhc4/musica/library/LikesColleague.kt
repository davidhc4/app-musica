package com.davidhc4.musica.library

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LikesState(val tracks: List<Track> = emptyList()) {
    val ids: Set<String> = tracks.map { it.id }.toSet()
}

/** Lleva las canciones marcadas con "me gusta", de la más reciente a la más antigua. */
class LikesColleague(private val store: LibraryStore) : BaseColleague() {

    private val _state = MutableStateFlow(LikesState())
    val state: StateFlow<LikesState> = _state

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.AppStarted -> _state.value = LikesState(store.load().distinctBy { it.id })

            is AppEvent.ToggleLike -> {
                val current = _state.value.tracks
                val updated = if (event.track.id in _state.value.ids) {
                    current.filter { it.id != event.track.id }
                } else {
                    listOf(event.track) + current
                }
                _state.value = LikesState(updated)
                store.save(updated)
            }

            else -> Unit
        }
    }
}
