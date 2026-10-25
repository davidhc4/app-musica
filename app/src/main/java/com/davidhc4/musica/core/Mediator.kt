package com.davidhc4.musica.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface Mediator {
    /** Seguro de llamar desde cualquier hilo. El evento se procesa después, en orden. */
    fun post(event: AppEvent)
}

interface Colleague {
    fun attach(mediator: Mediator)
    suspend fun handle(event: AppEvent)
}

abstract class BaseColleague : Colleague {
    protected lateinit var mediator: Mediator
        private set

    override fun attach(mediator: Mediator) {
        this.mediator = mediator
    }
}

/**
 * Mediador con una sola fila de eventos.
 *
 * - Procesa un evento a la vez, así que no hay dos componentes cambiando estado al mismo tiempo.
 * - Un evento publicado mientras se procesa otro espera su turno (no hay llamadas anidadas).
 * - Si un componente falla, los demás siguen y el mediador no se detiene.
 */
class EventMediator(
    scope: CoroutineScope,
    private val colleagues: List<Colleague>,
    private val onError: (Throwable, AppEvent) -> Unit = { _, _ -> },
) : Mediator {

    private val inbox = Channel<AppEvent>(Channel.UNLIMITED)

    init {
        colleagues.forEach { it.attach(this) }
        scope.launch {
            for (event in inbox) dispatch(event)
        }
    }

    override fun post(event: AppEvent) {
        inbox.trySend(event)
    }

    private suspend fun dispatch(event: AppEvent) {
        for (colleague in colleagues) {
            try {
                colleague.handle(event)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                onError(t, event)
                if (event !is AppEvent.ErrorOccurred) {
                    post(AppEvent.ErrorOccurred(t.message ?: "Error inesperado"))
                }
            }
        }
    }
}
