package com.davidhc4.musica.search

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.mediatorScope
import com.davidhc4.musica.source.MusicSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeSource(
    private val delayMs: Long = 0L,
    private val fail: Boolean = false,
) : MusicSource {
    val queries = mutableListOf<String>()

    override suspend fun search(query: String, limit: Int): List<Track> {
        queries += query
        delay(delayMs)
        if (fail) throw IOException("sin internet")
        return listOf(Track(id = "r:$query", title = query, uri = "https://x/$query"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchColleagueTest {

    private fun setup(source: MusicSource, scope: kotlinx.coroutines.CoroutineScope): Pair<SearchColleague, EventMediator> {
        val search = SearchColleague(scope, source, debounceMs = 300L)
        return search to EventMediator(scope, listOf(search))
    }

    @Test
    fun `una busqueda trae resultados y deja de cargar`() = runTest {
        val source = FakeSource(delayMs = 100)
        val (search, mediator) = setup(source, mediatorScope())

        mediator.post(AppEvent.SearchRequested("jazz"))
        advanceUntilIdle()

        assertEquals(listOf("jazz"), search.state.value.results.map { it.title })
        assertFalse(search.state.value.loading)
        assertFalse(search.state.value.failed)
    }

    @Test
    fun `si se sigue escribiendo solo cuenta la ultima busqueda`() = runTest {
        val source = FakeSource(delayMs = 100)
        val (search, mediator) = setup(source, mediatorScope())

        mediator.post(AppEvent.SearchRequested("ja"))
        mediator.post(AppEvent.SearchRequested("jaz"))
        mediator.post(AppEvent.SearchRequested("jazz"))
        advanceUntilIdle()

        assertEquals(listOf("jazz"), source.queries)
        assertEquals("jazz", search.state.value.query)
    }

    @Test
    fun `una busqueda lenta que ya no importa no pisa a la nueva`() = runTest {
        val source = FakeSource(delayMs = 1000)
        val (search, mediator) = setup(source, mediatorScope())

        mediator.post(AppEvent.SearchRequested("rock"))
        testScheduler.advanceTimeBy(500) // ya salió la consulta de "rock", aún sin respuesta
        testScheduler.runCurrent()
        mediator.post(AppEvent.SearchRequested("salsa"))
        advanceUntilIdle()

        assertEquals(listOf("salsa"), search.state.value.results.map { it.title })
    }

    @Test
    fun `si falla la red se avisa y no se pierde nada`() = runTest {
        val (search, mediator) = setup(FakeSource(fail = true), mediatorScope())

        mediator.post(AppEvent.SearchRequested("jazz"))
        advanceUntilIdle()

        assertTrue(search.state.value.failed)
        assertFalse(search.state.value.loading)
        assertTrue(search.state.value.results.isEmpty())
    }

    @Test
    fun `textos muy cortos no consultan y limpian los resultados`() = runTest {
        val source = FakeSource()
        val (search, mediator) = setup(source, mediatorScope())
        mediator.post(AppEvent.SearchRequested("jazz"))
        advanceUntilIdle()

        mediator.post(AppEvent.SearchRequested("j"))
        advanceUntilIdle()

        assertEquals(listOf("jazz"), source.queries)
        assertTrue(search.state.value.results.isEmpty())
        assertFalse(search.state.value.loading)
    }
}
