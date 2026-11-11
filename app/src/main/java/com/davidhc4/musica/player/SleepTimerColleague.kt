package com.davidhc4.musica.player

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SleepState(
    /** Minutos elegidos, o null si no hay temporizador por tiempo. */
    val minutes: Int? = null,
    /** Verdadero si la música debe parar cuando termine la canción que suena. */
    val atEndOfTrack: Boolean = false,
) {
    val active: Boolean get() = minutes != null || atEndOfTrack
}

/** Temporizador de apagado: pausa la música al cumplirse el tiempo o al terminar la canción. */
class SleepTimerColleague(private val scope: CoroutineScope) : BaseColleague() {

    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state

    private var job: Job? = null

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.SetSleepTimer -> {
                job?.cancel()
                val minutes = event.minutes?.takeIf { it > 0 }
                _state.value = SleepState(minutes = minutes)
                if (minutes != null) {
                    job = scope.launch {
                        delay(minutes * 60_000L)
                        _state.value = SleepState()
                        mediator.post(AppEvent.Pause)
                    }
                }
            }

            is AppEvent.SleepAtEndOfTrack -> {
                job?.cancel()
                _state.value = SleepState(atEndOfTrack = true)
            }

            // La cola ya puso la canción siguiente; se deja lista pero en pausa.
            is AppEvent.TrackEnded -> if (_state.value.atEndOfTrack) {
                _state.value = SleepState()
                mediator.post(AppEvent.Pause)
            }

            else -> Unit
        }
    }
}
