package com.davidhc4.musica.player

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
)

/** Controla el audio. No sabe de la cola: solo reacciona a "cambió la canción actual". */
class PlayerColleague(private val engine: PlaybackEngine) : BaseColleague() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    init {
        engine.listener = object : PlaybackEngine.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mediator.post(AppEvent.PlaybackStateChanged(isPlaying))
            }

            override fun onEnded() {
                mediator.post(AppEvent.TrackEnded)
            }

            override fun onError(message: String) {
                // Una canción dañada no debe congelar la cola: se avisa y se pasa a la siguiente.
                mediator.post(AppEvent.ErrorOccurred(message))
                mediator.post(AppEvent.TrackEnded)
            }
        }
    }

    fun positionMs(): Long = engine.positionMs()

    fun durationMs(): Long = engine.durationMs()

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.CurrentTrackChanged -> {
                val track = event.track
                _state.value = _state.value.copy(track = track)
                if (track == null) {
                    engine.stop()
                } else {
                    engine.load(track.uri)
                    engine.play()
                }
            }

            is AppEvent.Pause -> engine.pause()

            is AppEvent.Resume -> if (_state.value.track != null) engine.play()

            is AppEvent.SeekTo -> engine.seekTo(event.positionMs)

            is AppEvent.PlaybackStateChanged ->
                _state.value = _state.value.copy(isPlaying = event.isPlaying)

            else -> Unit
        }
    }
}
