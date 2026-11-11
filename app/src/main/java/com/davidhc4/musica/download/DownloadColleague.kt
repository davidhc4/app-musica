package com.davidhc4.musica.download

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.LibraryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DownloadsState(
    /** Canciones ya guardadas en el celular, la más reciente primero. */
    val tracks: List<Track> = emptyList(),
    /** Descargas en curso: avance entre 0 y 1 por canción. */
    val inProgress: Map<String, Float> = emptyMap(),
) {
    val ids: Set<String> = tracks.map { it.id }.toSet()
}

/** Solo se puede descargar lo que viene de internet por una dirección directa (no YouTube). */
fun canDownload(track: Track): Boolean =
    track.uri.startsWith("https://") || track.uri.startsWith("http://")

/**
 * Guarda canciones en el celular para oírlas sin internet. Las descargas corren aparte, varias a
 * la vez, sin frenar al resto de la app. Si una falla, avisa y no deja archivos a medias.
 */
class DownloadColleague(
    private val scope: CoroutineScope,
    private val index: LibraryStore,
    private val dir: File,
    private val fetch: suspend (url: String, target: File, onProgress: (Float) -> Unit) -> Unit,
) : BaseColleague() {

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state

    private val jobs = HashMap<String, Job>()

    /** Dirección del archivo descargado de esa canción, o null si no está descargada. */
    fun localUri(trackId: String): String? =
        if (trackId in _state.value.ids) fileFor(trackId).takeIf { it.exists() }?.toURI()?.toString() else null

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.AppStarted -> {
                // Si alguien borró un archivo por fuera, esa canción deja de contar como descargada.
                val present = index.load().distinctBy { it.id }.filter { fileFor(it.id).exists() }
                _state.value = _state.value.copy(tracks = present)
            }

            is AppEvent.DownloadTrack -> start(event.track)

            is AppEvent.RemoveDownload -> {
                jobs.remove(event.trackId)?.cancel()
                fileFor(event.trackId).delete()
                partFor(event.trackId).delete()
                val s = _state.value
                _state.value = s.copy(
                    tracks = s.tracks.filter { it.id != event.trackId },
                    inProgress = s.inProgress - event.trackId,
                )
                index.save(_state.value.tracks)
            }

            else -> Unit
        }
    }

    private fun start(track: Track) {
        val id = track.id
        if (!canDownload(track) || id in _state.value.ids || id in jobs) return
        _state.value = _state.value.copy(inProgress = _state.value.inProgress + (id to 0f))
        jobs[id] = scope.launch {
            val part = partFor(id)
            try {
                dir.mkdirs()
                fetch(track.uri, part) { progress ->
                    val s = _state.value
                    if (id in s.inProgress) _state.value = s.copy(inProgress = s.inProgress + (id to progress))
                }
                if (!part.renameTo(fileFor(id))) throw java.io.IOException("No se pudo guardar")
                val s = _state.value
                _state.value = s.copy(tracks = listOf(track) + s.tracks, inProgress = s.inProgress - id)
                index.save(_state.value.tracks)
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (_: Exception) {
                part.delete()
                _state.value = _state.value.copy(inProgress = _state.value.inProgress - id)
                mediator.post(AppEvent.ErrorOccurred("No se pudo descargar “${track.title}”"))
            } finally {
                jobs.remove(id)
            }
        }
    }

    private fun safeName(trackId: String) = trackId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private fun fileFor(trackId: String) = File(dir, safeName(trackId) + ".audio")

    private fun partFor(trackId: String) = File(dir, safeName(trackId) + ".tmp")
}
