package com.davidhc4.musica.playback

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.mediatorScope
import com.davidhc4.musica.queue.QueueColleague
import com.davidhc4.musica.queue.RepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueModesTest {

    private val songs = listOf("a", "b", "c", "d").map { Track(it, it, uri = "uri://$it") }

    private class Setup(scope: TestScope) {
        // "Mezclar" aquí es invertir, para que la prueba dé siempre lo mismo.
        val queue = QueueColleague(shuffler = { it.reversed() })
        val played = mutableListOf<String?>()
        val mediator = EventMediator(
            scope.mediatorScope(),
            listOf(queue, object : BaseColleague() {
                override suspend fun handle(event: AppEvent) {
                    if (event is AppEvent.CurrentTrackChanged) played += event.track?.id
                }
            }),
        )
        val order get() = queue.state.value.tracks.map { it.id }
        val current get() = queue.state.value.current?.id
    }

    @Test
    fun `aleatorio deja primero la que suena y al apagarlo vuelve el orden`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(songs, 1))
        s.mediator.post(AppEvent.ToggleShuffle)
        advanceUntilIdle()

        assertEquals(listOf("b", "d", "c", "a"), s.order)
        assertEquals("b", s.current)
        assertTrue(s.queue.state.value.shuffle)
        // Cambiar el modo no reinicia la canción que suena.
        assertEquals(listOf<String?>("b"), s.played)

        s.mediator.post(AppEvent.Next)
        s.mediator.post(AppEvent.ToggleShuffle)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c", "d"), s.order)
        assertEquals("d", s.current)
        assertFalse(s.queue.state.value.shuffle)
    }

    @Test
    fun `con aleatorio encendido una lista nueva empieza por la elegida`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.ToggleShuffle)
        s.mediator.post(AppEvent.PlayTracks(songs, 2))
        advanceUntilIdle()

        assertEquals(listOf("c", "d", "b", "a"), s.order)
        assertEquals("c", s.current)
    }

    @Test
    fun `repetir una vuelve a poner la misma al terminar pero siguiente si avanza`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(songs, 0))
        s.mediator.post(AppEvent.CycleRepeat)
        s.mediator.post(AppEvent.CycleRepeat)
        advanceUntilIdle()
        assertEquals(RepeatMode.ONE, s.queue.state.value.repeat)

        s.mediator.post(AppEvent.TrackEnded)
        advanceUntilIdle()
        assertEquals(listOf<String?>("a", "a"), s.played)

        s.mediator.post(AppEvent.Next)
        advanceUntilIdle()
        assertEquals("b", s.current)
    }

    @Test
    fun `repetir todas da la vuelta al final y al principio`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(songs, 3))
        s.mediator.post(AppEvent.CycleRepeat)
        s.mediator.post(AppEvent.TrackEnded)
        advanceUntilIdle()
        assertEquals("a", s.current)

        s.mediator.post(AppEvent.Previous)
        advanceUntilIdle()
        assertEquals("d", s.current)
    }

    @Test
    fun `sin repetir la lista se detiene al final`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(songs, 3))
        s.mediator.post(AppEvent.TrackEnded)
        advanceUntilIdle()

        assertEquals("d", s.current)
        assertEquals(listOf<String?>("d"), s.played)
    }

    @Test
    fun `una cancion danada no se repite ni da la vuelta`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(songs, 3))
        s.mediator.post(AppEvent.CycleRepeat)
        s.mediator.post(AppEvent.TrackFailed)
        advanceUntilIdle()

        assertEquals("d", s.current)
        assertEquals(listOf<String?>("d"), s.played)
    }

    @Test
    fun `se puede saltar a cualquier cancion de la cola`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(songs, 0))
        s.mediator.post(AppEvent.PlayQueueIndex(2))
        s.mediator.post(AppEvent.PlayQueueIndex(99))
        advanceUntilIdle()

        assertEquals("c", s.current)
    }
}
