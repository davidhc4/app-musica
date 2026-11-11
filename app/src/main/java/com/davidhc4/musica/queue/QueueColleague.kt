package com.davidhc4.musica.queue

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode { OFF, ALL, ONE }

data class QueueState(
    /** Las canciones en el orden en que van a sonar. */
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
) {
    val current: Track? get() = tracks.getOrNull(currentIndex)
}

/** Lleva la lista de reproducción y decide cuál canción sigue. No sabe nada del reproductor. */
class QueueColleague(
    private val shuffler: (List<Track>) -> List<Track> = { it.shuffled() },
) : BaseColleague() {

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state

    /** El orden original, para volver a él al apagar el modo aleatorio. */
    private var ordered: List<Track> = emptyList()

    override suspend fun handle(event: AppEvent) {
        val s = _state.value
        when (event) {
            is AppEvent.PlayTracks -> {
                if (event.tracks.isEmpty()) return
                val startIndex = event.startIndex.coerceIn(0, event.tracks.lastIndex)
                ordered = event.tracks
                if (s.shuffle) {
                    update(s.copy(tracks = shuffledFrom(ordered[startIndex], ordered), currentIndex = 0))
                } else {
                    update(s.copy(tracks = ordered, currentIndex = startIndex))
                }
                announce()
            }

            is AppEvent.Enqueue -> {
                if (event.tracks.isEmpty()) return
                val wasIdle = s.current == null
                ordered = ordered + event.tracks
                update(s.copy(tracks = s.tracks + event.tracks, currentIndex = if (wasIdle) 0 else s.currentIndex))
                if (wasIdle) announce()
            }

            is AppEvent.ClearQueue -> {
                ordered = emptyList()
                update(s.copy(tracks = emptyList(), currentIndex = -1))
                announce()
            }

            is AppEvent.ToggleShuffle -> {
                val current = s.current
                when {
                    current == null -> update(s.copy(shuffle = !s.shuffle))
                    // Se enciende: la canción que suena queda primera y el resto se mezcla.
                    !s.shuffle -> update(s.copy(tracks = shuffledFrom(current, ordered), currentIndex = 0, shuffle = true))
                    // Se apaga: vuelve el orden original, sin cortar la canción que suena.
                    else -> update(s.copy(tracks = ordered, currentIndex = ordered.indexOf(current), shuffle = false))
                }
            }

            is AppEvent.CycleRepeat -> update(
                s.copy(
                    repeat = when (s.repeat) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                )
            )

            is AppEvent.PlayQueueIndex -> moveTo(event.index)

            is AppEvent.PlayNext -> {
                val current = s.current
                if (current == null) {
                    ordered = listOf(event.track)
                    update(s.copy(tracks = ordered, currentIndex = 0))
                    announce()
                } else {
                    ordered = ordered.toMutableList().apply {
                        add((indexOf(current) + 1).coerceIn(0, size), event.track)
                    }
                    update(s.copy(tracks = s.tracks.toMutableList().apply { add(s.currentIndex + 1, event.track) }))
                }
            }

            is AppEvent.RemoveFromQueue -> {
                val i = event.index
                if (i !in s.tracks.indices) return
                val remaining = s.tracks.filterIndexed { at, _ -> at != i }
                ordered = ordered.toMutableList().apply { remove(s.tracks[i]) }
                when {
                    remaining.isEmpty() -> {
                        ordered = emptyList()
                        update(s.copy(tracks = emptyList(), currentIndex = -1))
                        announce()
                    }
                    i < s.currentIndex -> update(s.copy(tracks = remaining, currentIndex = s.currentIndex - 1))
                    // Se quitó la que sonaba: suena la que quedó en su lugar.
                    i == s.currentIndex -> {
                        update(s.copy(tracks = remaining, currentIndex = i.coerceAtMost(remaining.lastIndex)))
                        announce()
                    }
                    else -> update(s.copy(tracks = remaining))
                }
            }

            // Al reabrir la app: se deja todo como estaba, sin empezar a sonar.
            is AppEvent.RestoreQueue -> {
                if (event.tracks.isEmpty() || s.current != null) return
                ordered = event.tracks
                update(s.copy(tracks = ordered, currentIndex = event.index.coerceIn(0, ordered.lastIndex)))
                mediator.post(
                    AppEvent.CurrentTrackChanged(_state.value.current, autoPlay = false, startPositionMs = event.positionMs)
                )
            }

            // La canción terminó sola: aquí sí cuenta "repetir una".
            is AppEvent.TrackEnded -> when {
                s.current == null -> Unit
                s.repeat == RepeatMode.ONE -> announce()
                else -> moveTo(wrapped(s.currentIndex + 1))
            }

            is AppEvent.Next -> moveTo(wrapped(s.currentIndex + 1))

            is AppEvent.Previous -> moveTo(wrapped(s.currentIndex - 1))

            // La canción falló: se pasa a la siguiente sin repetir ni dar la vuelta, para que una
            // lista de canciones dañadas no quede intentando sin fin.
            is AppEvent.TrackFailed -> moveTo(s.currentIndex + 1)

            else -> Unit
        }
    }

    /** Con "repetir todas", pasar del final lleva al principio y al revés. */
    private fun wrapped(index: Int): Int {
        val s = _state.value
        if (s.repeat != RepeatMode.ALL || s.tracks.isEmpty()) return index
        return when {
            index > s.tracks.lastIndex -> 0
            index < 0 -> s.tracks.lastIndex
            else -> index
        }
    }

    private fun shuffledFrom(first: Track, all: List<Track>): List<Track> {
        val at = all.indexOf(first)
        val rest = all.filterIndexed { i, _ -> i != at }
        return listOf(first) + shuffler(rest)
    }

    private fun moveTo(index: Int) {
        val s = _state.value
        if (s.current == null || index !in s.tracks.indices) return
        update(s.copy(currentIndex = index))
        announce()
    }

    private fun announce() {
        mediator.post(AppEvent.CurrentTrackChanged(_state.value.current))
    }

    private fun update(new: QueueState) {
        _state.value = new
        mediator.post(AppEvent.QueueChanged(new.tracks, new.currentIndex))
    }
}
