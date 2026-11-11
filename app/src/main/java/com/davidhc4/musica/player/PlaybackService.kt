package com.davidhc4.musica.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.davidhc4.musica.MainActivity
import com.davidhc4.musica.MusicaApp
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.Mediator

/**
 * Mantiene la música sonando con la app cerrada o la pantalla apagada, y pone los controles en la
 * notificación y en la pantalla de bloqueo. No tiene reproductor propio: usa el de la app.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MusicaApp
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, QueuePlayer(app.audio.player, app.mediator))
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si se cierra la app sin nada sonando, el servicio no tiene por qué seguir vivo.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        // Solo se suelta la sesión; el reproductor es de la app y sigue existiendo.
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * Lo que ven la notificación y la pantalla de bloqueo. El reproductor real solo conoce una canción,
 * así que "siguiente" y "anterior" se le piden a la cola a través del mediador.
 */
@OptIn(UnstableApi::class)
private class QueuePlayer(player: Player, private val mediator: Mediator) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = mediator.post(AppEvent.Next)

    override fun seekToNextMediaItem() = mediator.post(AppEvent.Next)

    override fun seekToPrevious() = mediator.post(AppEvent.Previous)

    override fun seekToPreviousMediaItem() = mediator.post(AppEvent.Previous)

    // Entre una canción y la siguiente hay un instante "terminado". Se muestra como "cargando" para
    // que Android no quite la notificación ni corte la música con la pantalla apagada.
    override fun getPlaybackState(): Int {
        val state = super.getPlaybackState()
        return if (state == Player.STATE_ENDED && playWhenReady) Player.STATE_BUFFERING else state
    }
}
