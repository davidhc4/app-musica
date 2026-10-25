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

private class FakeLibraryStore(var stored: List<Track> = emptyList()) : LibraryStore {
    var saves = 0
    override suspend fun load() = stored
    override suspend fun save(tracks: List<Track>) {
        stored = tracks
        saves++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAndSearchTest {

    private fun track(id: String, title: String, artist: String? = null, album: String? = null) =
        Track(id = id, title = title, artist = artist, uri = "uri://$id", album = album)

    @Test
    fun `al arrancar carga lo guardado en orden alfabetico`() = runTest {
        val store = FakeLibraryStore(listOf(track("2", "Zeta"), track("1", "alfa")))
        val library = LibraryColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(library))

        mediator.post(AppEvent.AppStarted)
        advanceUntilIdle()

        assertTrue(library.state.value.loaded)
        assertEquals(listOf("alfa", "Zeta"), library.state.value.tracks.map { it.title })
    }

    @Test
    fun `agregar guarda sin duplicar y repetir no vuelve a escribir`() = runTest {
        val store = FakeLibraryStore()
        val library = LibraryColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(library))
        mediator.post(AppEvent.AppStarted)

        mediator.post(AppEvent.AddToLibrary(listOf(track("1", "B"), track("1", "B"), track("2", "A"))))
        advanceUntilIdle()
        assertEquals(listOf("A", "B"), store.stored.map { it.title })
        assertEquals(1, store.saves)

        mediator.post(AppEvent.AddToLibrary(listOf(track("1", "B"))))
        advanceUntilIdle()
        assertEquals(1, store.saves)
    }

    @Test
    fun `quitar borra de la biblioteca y del archivo`() = runTest {
        val store = FakeLibraryStore(listOf(track("1", "A"), track("2", "B")))
        val library = LibraryColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(library))
        mediator.post(AppEvent.AppStarted)

        mediator.post(AppEvent.RemoveFromLibrary("1"))
        advanceUntilIdle()

        assertEquals(listOf("B"), library.state.value.tracks.map { it.title })
        assertEquals(listOf("B"), store.stored.map { it.title })
    }

    private val songs = listOf(
        track("1", "Canción de Cuna", "José Álvarez", "Noches"),
        track("2", "Mañana", "Los Verdes"),
        track("3", "Cuna de Piedra", "Ana", "Canciones"),
    )

    @Test
    fun `la busqueda ignora acentos y mayusculas`() {
        assertEquals(listOf("Mañana"), searchTracks(songs, "MANANA").map { it.title })
        assertEquals(listOf("Canción de Cuna"), searchTracks(songs, "jose alvarez").map { it.title })
    }

    @Test
    fun `todas las palabras deben coincidir y los titulos que empiezan igual van primero`() {
        assertEquals(
            listOf("Cuna de Piedra", "Canción de Cuna"),
            searchTracks(songs, "cuna").map { it.title },
        )
        assertEquals(listOf("Canción de Cuna"), searchTracks(songs, "cuna noches").map { it.title })
    }

    @Test
    fun `busqueda vacia o sin resultados devuelve lista vacia`() {
        assertTrue(searchTracks(songs, "   ").isEmpty())
        assertTrue(searchTracks(songs, "zzz").isEmpty())
    }
}
