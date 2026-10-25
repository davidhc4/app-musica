package com.davidhc4.musica.player

/** Lo mínimo que el reproductor necesita de un motor de audio. Así se puede probar sin Android. */
interface PlaybackEngine {

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onEnded()
        fun onError(message: String)
    }

    var listener: Listener?

    fun load(uri: String)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun positionMs(): Long
    fun durationMs(): Long
    fun release()
}
