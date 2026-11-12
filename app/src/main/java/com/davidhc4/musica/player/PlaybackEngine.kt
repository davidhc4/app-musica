package com.davidhc4.musica.player

import com.davidhc4.musica.core.Track

/** Lo mínimo que el reproductor necesita de un motor de audio. Así se puede probar sin Android. */
interface PlaybackEngine {

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onEnded()
        fun onError(message: String)
    }

    var listener: Listener?

    fun load(track: Track)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)

    /** Velocidad de reproducción: 1 es la normal. */
    fun setSpeed(speed: Float) {}

    /** Si está activo, se saltan los silencios largos dentro de la canción. */
    fun setSkipSilence(enabled: Boolean) {}
    fun positionMs(): Long
    fun durationMs(): Long
    fun release()
}
