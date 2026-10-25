package com.davidhc4.musica.queue

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class QueueState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
) {
    val current: Track? get() = tracks.getOrNull(currentIndex)
}

/** Lleva la lista de reproducción y decide cuál canción sigue. No sabe nada del reproductor. */
class QueueColleague : BaseColleague() {

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.PlayTracks -> {
                if (event.tracks.isEmpty()) return
                val start = event.startIndex.coerceIn(0, event.tracks.lastIndex)
                update(QueueState(event.tracks, start))
                mediator.post(AppEvent.CurrentTrackChanged(_state.value.current))
            }

            is AppEvent.Enqueue -> {
                if (event.tracks.isEmpty()) return
                val wasIdle = _state.value.current == null
                val tracks = _state.value.tracks + event.tracks
                update(QueueState(tracks, if (wasIdle) 0 else _state.value.currentIndex))
                if (wasIdle) mediator.post(AppEvent.CurrentTrackChanged(_state.value.current))
            }

            is AppEvent.ClearQueue -> {
                update(QueueState())
                mediator.post(AppEvent.CurrentTrackChanged(null))
            }

            is AppEvent.Next, is AppEvent.TrackEnded -> moveTo(_state.value.currentIndex + 1)

            is AppEvent.Previous -> moveTo(_state.value.currentIndex - 1)

            else -> Unit
        }
    }

    private fun moveTo(index: Int) {
        val s = _state.value
        if (s.current == null || index !in s.tracks.indices) return
        update(s.copy(currentIndex = index))
        mediator.post(AppEvent.CurrentTrackChanged(_state.value.current))
    }

    private fun update(new: QueueState) {
        _state.value = new
        mediator.post(AppEvent.QueueChanged(new.tracks, new.currentIndex))
    }
}
