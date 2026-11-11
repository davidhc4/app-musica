package com.davidhc4.musica.playback

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.mediatorScope
import com.davidhc4.musica.queue.QueueColleague
import com.davidhc4.musica.queue.Session
import com.davidhc4.musica.queue.SessionColleague
import com.davidhc4.musica.queue.SessionStore
import com.davidhc4.musica.queue.sessionFromJson
import com.davidhc4.musica.queue.sessionToJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class MemorySession(var stored: Session? = null) : SessionStore {
    override suspend fun load() = stored
    override suspend fun save(session: Session) {
        stored = session
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTest {

    private val songs = listOf("a", "b", "c").map { Track(it, it, uri = "uri://$it") }

    private class Setup(scope: TestScope, val store: MemorySession) {
        var position = 0L
        val queue = QueueColleague()
        val changes = mutableListOf<AppEvent.CurrentTrackChanged>()
        val mediator = EventMediator(
            scope.mediatorScope(),
            listOf(queue, SessionColleague(store) { position }, object : BaseColleague() {
                override suspend fun handle(event: AppEvent) {
                    if (event is AppEvent.CurrentTrackChanged) changes += event
                }
            }),
        )
    }

    @Test
    fun `al abrir retoma la cola en pausa y por donde iba`() = runTest {
        val s = Setup(this, MemorySession(Session(songs, 1, 42_000L)))
        s.mediator.post(AppEvent.AppStarted)
        advanceUntilIdle()

        assertEquals("b", s.queue.state.value.current?.id)
        val change = s.changes.single()
        assertEquals("b", change.track?.id)
        assertEquals(false, change.autoPlay)
        assertEquals(42_000L, change.startPositionMs)
        // Restaurar no borra la posición guardada.
        assertEquals(42_000L, s.store.stored?.positionMs)
    }

    @Test
    fun `guarda al cambiar de cancion al pausar y al salir`() = runTest {
        val s = Setup(this, MemorySession())
        s.mediator.post(AppEvent.AppStarted)
        s.mediator.post(AppEvent.PlayTracks(songs, 0))
        advanceUntilIdle()
        assertEquals(Session(songs, 0, 0L), s.store.stored)

        s.position = 30_000L
        s.mediator.post(AppEvent.PlaybackStateChanged(false))
        advanceUntilIdle()
        assertEquals(30_000L, s.store.stored?.positionMs)

        s.mediator.post(AppEvent.Next)
        advanceUntilIdle()
        assertEquals(Session(songs, 1, 0L), s.store.stored)

        s.position = 5_000L
        s.mediator.post(AppEvent.SaveSession)
        advanceUntilIdle()
        assertEquals(Session(songs, 1, 5_000L), s.store.stored)
    }

    @Test
    fun `sin nada guardado no restaura nada`() = runTest {
        val s = Setup(this, MemorySession())
        s.mediator.post(AppEvent.AppStarted)
        advanceUntilIdle()

        assertNull(s.queue.state.value.current)
        assertEquals(0, s.changes.size)
    }

    @Test
    fun `reproducir a continuacion y quitar de la cola`() = runTest {
        val s = Setup(this, MemorySession())
        val x = Track("x", "x", uri = "uri://x")
        s.mediator.post(AppEvent.PlayTracks(songs, 0))
        s.mediator.post(AppEvent.PlayNext(x))
        advanceUntilIdle()
        assertEquals(listOf("a", "x", "b", "c"), s.queue.state.value.tracks.map { it.id })
        assertEquals("a", s.queue.state.value.current?.id)

        // Quitar una anterior no cambia la que suena.
        s.mediator.post(AppEvent.Next)
        s.mediator.post(AppEvent.RemoveFromQueue(0))
        advanceUntilIdle()
        assertEquals(listOf("x", "b", "c"), s.queue.state.value.tracks.map { it.id })
        assertEquals("x", s.queue.state.value.current?.id)

        // Quitar la que suena pasa a la que queda en su lugar.
        s.mediator.post(AppEvent.RemoveFromQueue(0))
        advanceUntilIdle()
        assertEquals("b", s.queue.state.value.current?.id)
        assertEquals("b", s.changes.last().track?.id)
    }

    @Test
    fun `la sesion se guarda y se lee igual`() {
        val session = Session(songs, 2, 12_345L)
        assertEquals(session, sessionFromJson(sessionToJson(session)))
    }
}
