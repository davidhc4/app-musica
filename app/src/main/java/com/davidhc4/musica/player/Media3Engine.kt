package com.davidhc4.musica.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Motor real de audio. Debe usarse siempre desde el hilo principal. */
class Media3Engine(context: Context) : PlaybackEngine {

    private val player = ExoPlayer.Builder(context).build()

    override var listener: PlaybackEngine.Listener? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listener?.onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(error.message ?: "No se pudo reproducir la canción")
            }
        })
    }

    override fun load(uri: String) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun positionMs(): Long = player.currentPosition

    override fun durationMs(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    override fun release() = player.release()
}
