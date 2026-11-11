package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Busca a la vez en la fuente principal (cuando está activa) y en la secundaria, y devuelve
 * primero los resultados de la principal y luego los de la secundaria. Si una se cae, responde la
 * otra; solo falla la búsqueda si fallan las dos.
 */
class SectionedSource(
    private val primary: MusicSource,
    private val secondary: MusicSource,
    private val primaryEnabled: () -> Boolean,
) : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> = coroutineScope {
        if (!primaryEnabled()) return@coroutineScope secondary.search(query, limit)

        val first = async { runCatching { primary.search(query, limit) } }
        val second = async { runCatching { secondary.search(query, limit) } }
        val outcomes = listOf(first.await(), second.await())

        outcomes.firstNotNullOfOrNull { it.exceptionOrNull() as? CancellationException }?.let { throw it }
        if (outcomes.all { it.isFailure }) throw outcomes.firstNotNullOf { it.exceptionOrNull() }
        outcomes.flatMap { it.getOrNull().orEmpty() }.distinctBy { it.id }
    }
}
