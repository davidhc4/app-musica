package com.davidhc4.musica.search

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.source.MusicSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Track> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Busca en línea sin trabar al resto de la app: la consulta corre aparte, así los botones del
 * reproductor siguen respondiendo mientras tanto. Solo cuenta la última búsqueda escrita.
 */
class SearchColleague(
    private val scope: CoroutineScope,
    private val source: MusicSource,
    private val debounceMs: Long = 350L,
) : BaseColleague() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    private var job: Job? = null

    override suspend fun handle(event: AppEvent) {
        if (event !is AppEvent.SearchRequested) return

        job?.cancel()
        val query = event.query.trim()
        if (query.length < MIN_LENGTH) {
            _state.value = SearchState(query = query)
            return
        }

        // Mientras llega la respuesta nueva se siguen mostrando los resultados anteriores.
        _state.value = _state.value.copy(query = query, loading = true, failed = false)
        job = scope.launch {
            delay(debounceMs)
            val found = try {
                source.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.value = if (found != null) {
                SearchState(query = query, results = found)
            } else {
                _state.value.copy(loading = false, failed = true)
            }
        }
    }

    private companion object {
        const val MIN_LENGTH = 2
    }
}
