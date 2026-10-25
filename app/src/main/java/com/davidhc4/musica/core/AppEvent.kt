package com.davidhc4.musica.core

data class Track(
    val id: String,
    val title: String,
    val artist: String? = null,
    val uri: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
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

    // Biblioteca
    data class AddToLibrary(val tracks: List<Track>) : AppEvent
    data class RemoveFromLibrary(val trackId: String) : AppEvent

    // Búsqueda en línea
    data class SearchRequested(val query: String) : AppEvent

    // Apariencia
    data class SetThemeMode(val mode: ThemeMode) : AppEvent

    // Avisos (los publican los componentes)
    data class CurrentTrackChanged(val track: Track?) : AppEvent
    data class QueueChanged(val tracks: List<Track>, val currentIndex: Int) : AppEvent
    data class PlaybackStateChanged(val isPlaying: Boolean) : AppEvent
    data object TrackEnded : AppEvent
    data class ErrorOccurred(val message: String) : AppEvent
}
