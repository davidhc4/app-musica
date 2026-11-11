package com.davidhc4.musica.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.davidhc4.musica.core.Track

/** Motor real de audio. Debe usarse siempre desde el hilo principal. */
class Media3Engine(context: Context) : PlaybackEngine {

    /**
     * El reproductor de fondo. Pide el foco de audio (se calla si entra una llamada), se pausa al
     * desconectar los audífonos y mantiene despierto el celular mientras suena con la pantalla apagada.
     */
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val main = Handler(Looper.getMainLooper())

    // Si al terminar una canción no llega otra, se deja de "querer sonar" para soltar la notificación.
    private val settleAfterEnd = Runnable {
        if (player.playbackState == Player.STATE_ENDED) player.pause()
    }

    override var listener: PlaybackEngine.Listener? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    listener?.onEnded()
                    main.removeCallbacks(settleAfterEnd)
                    main.postDelayed(settleAfterEnd, END_GRACE_MS)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(error.message ?: "No se pudo reproducir la canción")
            }
        })
    }

    override fun load(track: Track) {
        main.removeCallbacks(settleAfterEnd)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
            .build()
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uri)
                .setMediaMetadata(metadata)
                .build()
        )
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun stop() {
        main.removeCallbacks(settleAfterEnd)
        player.stop()
        player.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    /** Identificador de la salida de audio, para abrir el ecualizador del celular sobre ella. */
    val audioSessionId: Int get() = player.audioSessionId

    override fun positionMs(): Long = player.currentPosition

    override fun durationMs(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    override fun release() = player.release()

    private companion object {
        const val END_GRACE_MS = 2_000L
    }
}
