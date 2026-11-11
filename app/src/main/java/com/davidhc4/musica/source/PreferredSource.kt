package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import kotlinx.coroutines.CancellationException

/**
 * Usa la fuente principal cuando está disponible y da resultados; si no está, falla o no encuentra
 * nada, responde la de respaldo. Así la búsqueda nunca se queda sin nada que mostrar.
 */
class PreferredSource(
    private val primary: MusicSource,
    private val fallback: MusicSource,
    private val primaryEnabled: () -> Boolean,
) : MusicSource {

    override suspend fun search(query: String, limit: Int): List<Track> {
        if (primaryEnabled()) {
            try {
                val found = primary.search(query, limit)
                if (found.isNotEmpty()) return found
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sigue con la de respaldo.
            }
        }
        return fallback.search(query, limit)
    }
}
