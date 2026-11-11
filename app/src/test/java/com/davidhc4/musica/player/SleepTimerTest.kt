package com.davidhc4.musica.player

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.Playlist
import com.davidhc4.musica.library.groupByAlbum
import com.davidhc4.musica.library.groupByArtist
import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private class Setup(scope: TestScope) {
        private val shared = scope.mediatorScope()
        val timer = SleepTimerColleague(shared)
        var pauses = 0
        val mediator = EventMediator(
            shared,
            listOf(timer, object : BaseColleague() {
                override suspend fun handle(event: AppEvent) {
                    if (event is AppEvent.Pause) pauses++
                }
            }),
        )
    }

    @Test
    fun `pausa al cumplirse el tiempo y no antes`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.SetSleepTimer(15))
        advanceTimeBy(14 * 60_000L)
        assertEquals(0, s.pauses)
        assertTrue(s.timer.state.value.active)

        advanceTimeBy(2 * 60_000L)
        assertEquals(1, s.pauses)
        assertFalse(s.timer.state.value.active)
    }

    @Test
    fun `desactivarlo o cambiarlo cancela el anterior`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.SetSleepTimer(15))
        s.mediator.post(AppEvent.SetSleepTimer(null))
        advanceUntilIdle()

        assertEquals(0, s.pauses)
        assertFalse(s.timer.state.value.active)
    }

    @Test
    fun `al terminar la cancion pausa una sola vez`() = runTest {
        val s = Setup(this)
        s.mediator.post(AppEvent.SleepAtEndOfTrack)
        s.mediator.post(AppEvent.TrackEnded)
        s.mediator.post(AppEvent.TrackEnded)
        advanceUntilIdle()

        assertEquals(1, s.pauses)
        assertFalse(s.timer.state.value.active)
    }

    @Test
    fun `agrupa por artista y por album con los desconocidos al final`() {
        val tracks = listOf(
            Track("1", "B", artist = "Zoé", uri = "u1", album = "Uno"),
            Track("2", "A", artist = "Ana", uri = "u2", album = "Uno"),
            Track("3", "C", artist = null, uri = "u3", album = null),
            Track("4", "D", artist = "ana", uri = "u4", album = "Dos"),
        )

        val artists = groupByArtist(tracks)
        assertEquals(listOf("Ana", "Zoé", "Artista desconocido"), artists.map { it.name })
        assertEquals(listOf("2", "4"), artists[0].tracks.map { it.id })

        val albums = groupByAlbum(tracks)
        assertEquals(listOf("Dos", "Uno", "Álbum desconocido"), albums.map { it.name })
        assertEquals(Playlist("x", "y"), Playlist("x", "y"))
    }
}
