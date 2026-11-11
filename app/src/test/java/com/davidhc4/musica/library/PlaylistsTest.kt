package com.davidhc4.musica.library

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryPlaylists(var stored: List<Playlist> = emptyList()) : PlaylistStore {
    var saves = 0
    override suspend fun load() = stored
    override suspend fun save(playlists: List<Playlist>) {
        stored = playlists
        saves++
    }
}

private fun tune(id: String) = Track(id = id, title = id, artist = "x", uri = "u/$id", durationMs = 1000L)

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsTest {

    private fun ids(): () -> String {
        var n = 0
        return { "p${++n}" }
    }

    @Test
    fun `crear una lista con su primera cancion y agregar otra`() = runTest {
        val store = MemoryPlaylists()
        val lists = PlaylistsColleague(store, ids())
        val mediator = EventMediator(mediatorScope(), listOf(lists))

        mediator.post(AppEvent.AppStarted)
        mediator.post(AppEvent.CreatePlaylist("  Para correr ", tune("a")))
        mediator.post(AppEvent.AddToPlaylist("p1", tune("b")))
        mediator.post(AppEvent.AddToPlaylist("p1", tune("b")))
        advanceUntilIdle()

        val list = lists.state.value.playlists.single()
        assertEquals("Para correr", list.name)
        assertEquals(listOf("a", "b"), list.tracks.map { it.id })
        assertEquals(lists.state.value.playlists, store.stored)
        // Agregar una repetida no cambia nada ni vuelve a escribir.
        assertEquals(2, store.saves)
    }

    @Test
    fun `quitar una cancion y borrar la lista`() = runTest {
        val store = MemoryPlaylists(listOf(Playlist("p1", "Uno", listOf(tune("a"), tune("b"))), Playlist("p2", "Dos")))
        val lists = PlaylistsColleague(store, ids())
        val mediator = EventMediator(mediatorScope(), listOf(lists))

        mediator.post(AppEvent.AppStarted)
        mediator.post(AppEvent.RemoveFromPlaylist("p1", "a"))
        mediator.post(AppEvent.DeletePlaylist("p2"))
        mediator.post(AppEvent.RenamePlaylist("p1", " Nuevo nombre "))
        mediator.post(AppEvent.RenamePlaylist("p1", "  "))
        advanceUntilIdle()
        assertEquals("Nuevo nombre", lists.state.value.playlists.single().name)

        val list = lists.state.value.playlists.single()
        assertEquals(listOf("b"), list.tracks.map { it.id })
        assertEquals(listOf("p1"), store.stored.map { it.id })
    }

    @Test
    fun `se pueden reordenar las canciones de una lista`() = runTest {
        val store = MemoryPlaylists(listOf(Playlist("p1", "Uno", listOf(tune("a"), tune("b"), tune("c")))))
        val lists = PlaylistsColleague(store, ids())
        val mediator = EventMediator(mediatorScope(), listOf(lists))

        mediator.post(AppEvent.AppStarted)
        mediator.post(AppEvent.MoveInPlaylist("p1", 2, 0))
        mediator.post(AppEvent.MoveInPlaylist("p1", 5, 0))
        advanceUntilIdle()

        assertEquals(listOf("c", "a", "b"), lists.state.value.playlists.single().tracks.map { it.id })
        assertEquals(1, store.saves)
    }

    @Test
    fun `un nombre vacio no crea nada`() = runTest {
        val store = MemoryPlaylists()
        val lists = PlaylistsColleague(store, ids())
        val mediator = EventMediator(mediatorScope(), listOf(lists))

        mediator.post(AppEvent.CreatePlaylist("   "))
        advanceUntilIdle()

        assertTrue(lists.state.value.playlists.isEmpty())
        assertEquals(0, store.saves)
    }

    @Test
    fun `las listas se guardan y se leen iguales`() {
        val lists = listOf(Playlist("p1", "Uno", listOf(tune("a"), tune("b"))), Playlist("p2", "Vacía"))
        assertEquals(lists, playlistsFromJson(playlistsToJson(lists)))
    }
}
