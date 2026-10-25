package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Busca en varias fuentes a la vez. Si una se cae o cambia, las demás siguen dando resultados;
 * solo falla la búsqueda si todas fallan. Las listas se mezclan una a una para que ninguna domine.
 */
class CompositeSource(private val sources: List<MusicSource>) : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> = coroutineScope {
        val outcomes = sources.map { source ->
            async { runCatching { source.search(query, limit) } }
        }.map { it.await() }

        outcomes.firstNotNullOfOrNull { it.exceptionOrNull() as? CancellationException }?.let { throw it }
        val lists = outcomes.mapNotNull { it.getOrNull() }
        if (lists.isEmpty()) throw outcomes.firstNotNullOf { it.exceptionOrNull() }

        buildList {
            val longest = lists.maxOf { it.size }
            for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let(::add) }
        }.distinctBy { it.id }.take(limit)
    }
}
