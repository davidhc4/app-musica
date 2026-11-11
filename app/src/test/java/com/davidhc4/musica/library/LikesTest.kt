package com.davidhc4.musica.library

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.mediatorScope
import com.davidhc4.musica.ui.artworkUrls
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore(var stored: List<Track> = emptyList()) : LibraryStore {
    override suspend fun load() = stored
    override suspend fun save(tracks: List<Track>) {
        stored = tracks
    }
}

private fun song(id: String) = Track(id = id, title = id, uri = "u/$id")

@OptIn(ExperimentalCoroutinesApi::class)
class LikesTest {

    @Test
    fun `dar me gusta guarda la cancion con la mas reciente primero`() = runTest {
        val store = MemoryStore()
        val likes = LikesColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(likes))

        mediator.post(AppEvent.AppStarted)
        mediator.post(AppEvent.ToggleLike(song("a")))
        mediator.post(AppEvent.ToggleLike(song("b")))
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), likes.state.value.tracks.map { it.id })
        assertEquals(setOf("a", "b"), likes.state.value.ids)
        assertEquals(likes.state.value.tracks, store.stored)
    }

    @Test
    fun `tocar de nuevo quita el me gusta`() = runTest {
        val store = MemoryStore(listOf(song("a"), song("b")))
        val likes = LikesColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(likes))

        mediator.post(AppEvent.AppStarted)
        mediator.post(AppEvent.ToggleLike(song("a")))
        advanceUntilIdle()

        assertEquals(listOf("b"), likes.state.value.tracks.map { it.id })
        assertEquals(listOf("b"), store.stored.map { it.id })
    }

    @Test
    fun `la caratula de YouTube sale del video y las demas de su direccion`() {
        val yt = Track(id = "youtube:abcdefghijk", title = "x", uri = "youtube:abcdefghijk", artworkUrl = "https://i/viejo.jpg")
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hq720.jpg", artworkUrls(yt).first())
        assertEquals("https://i/viejo.jpg", artworkUrls(yt).last())
        assertEquals(listOf("https://a/c.jpg"), artworkUrls(song("a").copy(artworkUrl = "https://a/c.jpg")))
        assertTrue(artworkUrls(song("a")).isEmpty())
    }
}
