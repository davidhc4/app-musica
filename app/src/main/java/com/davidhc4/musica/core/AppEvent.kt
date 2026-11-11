package com.davidhc4.musica.core

data class Track(
    val id: String,
    val title: String,
    val artist: String? = null,
    val uri: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    /** Verdadero si solo se puede escuchar un fragmento corto de la canción. */
    val isPreview: Boolean = false,
)

/**
 * Todo lo que circula por el Mediator. Los componentes nunca se llaman entre sí:
 * solo publican y reciben estos eventos.
 */
sealed interface AppEvent {

    // Arranque
    data object AppStarted : AppEvent

    // Órdenes de reproducción (las publica la interfaz)
    data class PlayTracks(val tracks: List<Track>, val startIndex: Int = 0) : AppEvent
    data class Enqueue(val tracks: List<Track>) : AppEvent
    data object ClearQueue : AppEvent
    data object Pause : AppEvent
    data object Resume : AppEvent
    data object Next : AppEvent
    data object Previous : AppEvent
    data class SeekTo(val positionMs: Long) : AppEvent
    data object ToggleShuffle : AppEvent
    data object CycleRepeat : AppEvent
    data class PlayQueueIndex(val index: Int) : AppEvent
    data class PlayNext(val track: Track) : AppEvent
    data class RemoveFromQueue(val index: Int) : AppEvent

    // Retomar lo que se estaba oyendo
    data class RestoreQueue(val tracks: List<Track>, val index: Int, val positionMs: Long) : AppEvent
    data object SaveSession : AppEvent

    // Biblioteca
    data class AddToLibrary(val tracks: List<Track>) : AppEvent
    data class RemoveFromLibrary(val trackId: String) : AppEvent
    data class ToggleLike(val track: Track) : AppEvent

    // Listas de reproducción propias
    data class CreatePlaylist(val name: String, val firstTrack: Track? = null) : AppEvent
    data class DeletePlaylist(val playlistId: String) : AppEvent
    data class AddToPlaylist(val playlistId: String, val track: Track) : AppEvent
    data class RemoveFromPlaylist(val playlistId: String, val trackId: String) : AppEvent

    // Descargas para oír sin internet
    data class DownloadTrack(val track: Track) : AppEvent
    data class RemoveDownload(val trackId: String) : AppEvent

    // Búsqueda en línea
    data class SearchRequested(val query: String) : AppEvent

    // Apariencia
    data class SetThemeMode(val mode: ThemeMode) : AppEvent

    // Fuentes de música
    data class SetYouTubeKey(val key: String) : AppEvent
    data class SetLibrarySort(val sort: LibrarySort) : AppEvent

    // Actualizaciones de la app
    data object CheckForUpdates : AppEvent
    data object InstallUpdate : AppEvent

    // Avisos (los publican los componentes)
    data class CurrentTrackChanged(
        val track: Track?,
        /** Falso cuando solo se deja lista la canción, sin empezar a sonar (al reabrir la app). */
        val autoPlay: Boolean = true,
        val startPositionMs: Long = 0L,
    ) : AppEvent
    data class QueueChanged(val tracks: List<Track>, val currentIndex: Int) : AppEvent
    data class PlaybackStateChanged(val isPlaying: Boolean) : AppEvent
    data object TrackEnded : AppEvent
    data object TrackFailed : AppEvent
    data class ErrorOccurred(val message: String) : AppEvent
}
