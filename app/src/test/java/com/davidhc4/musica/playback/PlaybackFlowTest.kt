package com.davidhc4.musica.playback

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Mediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.player.PlaybackEngine
import com.davidhc4.musica.player.PlayerColleague
import com.davidhc4.musica.queue.QueueColleague
import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine : PlaybackEngine {
    override var listener: PlaybackEngine.Listener? = null
    var loadedUri: String? = null
    var playing = false
    val loads = mutableListOf<String>()

    override fun load(uri: String) {
        loadedUri = uri
        loads += uri
    }

    override fun play() {
        playing = true
        listener?.onIsPlayingChanged(true)
    }

    override fun pause() {
        playing = false
        listener?.onIsPlayingChanged(false)
    }

    override fun stop() {
        loadedUri = null
        pause()
    }

    override fun seekTo(positionMs: Long) = Unit
    override fun positionMs() = 0L
    override fun durationMs() = 0L
    override fun release() = Unit

    fun finishTrack() = listener?.onEnded()
    fun failTrack() = listener?.onError("archivo dañado")
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFlowTest {

    private val a = Track("a", "A", uri = "uri://a")
    private val b = Track("b", "B", uri = "uri://b")
    private val c = Track("c", "C", uri = "uri://c")

    private class Setup(scope: TestScope) {
        val engine = FakeEngine()
        val queue = QueueColleague()
        val player = PlayerColleague(engine)
        val errors = mutableListOf<String>()
        val mediator: Mediator = EventMediator(
            scope.mediatorScope(),
            listOf(queue, player, object : com.davidhc4.musica.core.BaseColleague() {
                override suspend fun handle(event: AppEvent) {
                    if (event is AppEvent.ErrorOccurred) errors += event.message
                }
            }),
        )
    }

    @Test
    fun `agregar a una cola vacia empieza a sonar la primera`() = runTest {
        val s = Setup(this)

        s.mediator.post(AppEvent.Enqueue(listOf(a, b)))
        advanceUntilIdle()

        assertEquals("uri://a", s.engine.loadedUri)
        assertTrue(s.player.state.value.isPlaying)
        assertEquals(listOf(a, b), s.queue.state.value.tracks)
    }

    @Test
    fun `agregar con algo sonando no interrumpe la cancion actual`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(listOf(a)))
        advanceUntilIdle()

        s.mediator.post(AppEvent.Enqueue(listOf(b)))
        advanceUntilIdle()

        assertEquals(listOf("uri://a"), s.engine.loads)
        assertEquals(listOf(a, b), s.queue.state.value.tracks)
    }

    @Test
    fun `al terminar una cancion pasa a la siguiente y al final se detiene`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(listOf(a, b)))
        advanceUntilIdle()

        s.engine.finishTrack()
        advanceUntilIdle()
        assertEquals("uri://b", s.engine.loadedUri)

        s.engine.finishTrack()
        advanceUntilIdle()
        assertEquals(listOf("uri://a", "uri://b"), s.engine.loads)
        assertEquals(b, s.queue.state.value.current)
    }

    @Test
    fun `una cancion dañada se salta y la cola sigue`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(listOf(a, b, c)))
        advanceUntilIdle()

        s.engine.failTrack()
        advanceUntilIdle()

        assertEquals("uri://b", s.engine.loadedUri)
        assertEquals(listOf("archivo dañado"), s.errors)
    }

    @Test
    fun `siguiente y anterior respetan los limites de la cola`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(listOf(a, b)))
        advanceUntilIdle()

        s.mediator.post(AppEvent.Previous)
        advanceUntilIdle()
        assertEquals(a, s.queue.state.value.current)

        s.mediator.post(AppEvent.Next)
        s.mediator.post(AppEvent.Next)
        advanceUntilIdle()
        assertEquals(b, s.queue.state.value.current)
        assertEquals(listOf("uri://a", "uri://b"), s.engine.loads)
    }

    @Test
    fun `el indice inicial fuera de rango se corrige`() = runTest {
        val s = Setup(this)

        s.mediator.post(AppEvent.PlayTracks(listOf(a, b), startIndex = 99))
        advanceUntilIdle()

        assertEquals(b, s.queue.state.value.current)
        assertEquals("uri://b", s.engine.loadedUri)
    }

    @Test
    fun `pausar y reanudar con cola vacia no hace nada raro`() = runTest {
        val s = Setup(this)

        s.mediator.post(AppEvent.Resume)
        advanceUntilIdle()

        assertFalse(s.engine.playing)
        assertNull(s.player.state.value.track)
    }

    @Test
    fun `vaciar la cola detiene el audio`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.PlayTracks(listOf(a, b)))
        advanceUntilIdle()

        s.mediator.post(AppEvent.ClearQueue)
        advanceUntilIdle()

        assertFalse(s.engine.playing)
        assertNull(s.engine.loadedUri)
        assertTrue(s.queue.state.value.tracks.isEmpty())
    }
}
