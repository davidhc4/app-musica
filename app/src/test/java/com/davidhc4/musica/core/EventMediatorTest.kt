package com.davidhc4.musica.core

import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventMediatorTest {

    private val trackA = Track("a", "A", uri = "uri://a")

    private class Recorder(
        private val name: String,
        private val log: MutableList<String>,
        private val onEvent: suspend (AppEvent, Mediator) -> Unit = { _, _ -> },
    ) : BaseColleague() {
        override suspend fun handle(event: AppEvent) {
            log += "$name:${event::class.simpleName}"
            onEvent(event, mediator)
        }
    }

    @Test
    fun `un evento publicado dentro de un handler espera su turno`() = runTest {
        val log = mutableListOf<String>()
        val first = Recorder("1", log) { event, mediator ->
            if (event is AppEvent.Pause) mediator.post(AppEvent.Resume)
        }
        val second = Recorder("2", log)
        val mediator = EventMediator(mediatorScope(), listOf(first, second))

        mediator.post(AppEvent.Pause)
        advanceUntilIdle()

        // Pause llega a los dos componentes antes de que Resume empiece.
        assertEquals(listOf("1:Pause", "2:Pause", "1:Resume", "2:Resume"), log)
    }

    @Test
    fun `los eventos se procesan en el orden en que se publican`() = runTest {
        val log = mutableListOf<String>()
        val mediator = EventMediator(mediatorScope(), listOf(Recorder("x", log)))

        mediator.post(AppEvent.Pause)
        mediator.post(AppEvent.Resume)
        mediator.post(AppEvent.Next)
        advanceUntilIdle()

        assertEquals(listOf("x:Pause", "x:Resume", "x:Next"), log)
    }

    @Test
    fun `si un componente falla los demas siguen y el mediador no se detiene`() = runTest {
        val log = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        val broken = Recorder("roto", log) { _, _ -> error("fallo") }
        val healthy = Recorder("sano", log)
        val mediator = EventMediator(
            mediatorScope(),
            listOf(broken, healthy),
            onError = { t, _ -> errors += t },
        )

        mediator.post(AppEvent.Pause)
        mediator.post(AppEvent.Resume)
        advanceUntilIdle()

        assertTrue(log.contains("sano:Pause"))
        assertTrue(log.contains("sano:Resume"))
        assertTrue(log.contains("sano:ErrorOccurred"))
        // Dos eventos fallaron, así que hay dos ErrorOccurred. Que "roto" falle también al
        // atenderlos no genera más avisos (sin bucles).
        assertEquals(2, log.count { it == "roto:ErrorOccurred" })
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `eventos con datos llegan intactos`() = runTest {
        var received: AppEvent? = null
        val mediator = EventMediator(
            mediatorScope(),
            listOf(Recorder("x", mutableListOf()) { e, _ -> if (e is AppEvent.Enqueue) received = e }),
        )

        mediator.post(AppEvent.Enqueue(listOf(trackA)))
        advanceUntilIdle()

        assertEquals(AppEvent.Enqueue(listOf(trackA)), received)
    }
}
