package com.davidhc4.musica

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.davidhc4.musica.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.source.isYouTubeUri
import com.davidhc4.musica.ui.MusicaRoot
import com.davidhc4.musica.ui.MusicaTheme
import com.davidhc4.musica.ui.isDarkTheme

class MainActivity : ComponentActivity() {

    // Mantiene conectado el servicio de música mientras la app está abierta. Así, cuando algo
    // empieza a sonar, Android deja que siga con la pantalla apagada.
    private var playback: ListenableFuture<MediaController>? = null

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MusicaApp
        app.youtube.useScreen(this)
        playback = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()
        // Sin este permiso la música suena igual, pero no se verían los controles en la notificación.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by app.settings.state.collectAsState()
            val dark = isDarkTheme(settings)

            // Barras del sistema transparentes, con iconos claros u oscuros según el tema elegido.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }

            MusicaTheme(settings) { MusicaRoot(app) }
        }
    }

    override fun onDestroy() {
        // Solo se suelta si esta pantalla sigue siendo la que usa el video (no una más nueva).
        if (isFinishing) (application as MusicaApp).youtube.useScreen(null)
        playback?.let { MediaController.releaseFuture(it) }
        playback = null
        super.onDestroy()
    }

    // Si una canción de YouTube estaba sonando al salir, se reanuda sola al volver.
    private var resumeOnReturn = false

    override fun onStart() {
        super.onStart()
        val app = application as MusicaApp
        app.youtube.onScreenShown()
        if (resumeOnReturn) {
            resumeOnReturn = false
            app.mediator.post(AppEvent.Resume)
        }
    }

    override fun onStop() {
        super.onStop()
        // YouTube solo permite reproducir mientras el video se ve, así que se pausa al salir.
        val app = application as MusicaApp
        if (isYouTubeUri(app.player.state.value.track?.uri)) {
            resumeOnReturn = app.player.state.value.isPlaying
            app.youtube.onScreenHidden()
        }
        // Se anota por dónde iba la canción, para retomarla si Android cierra la app.
        app.mediator.post(AppEvent.SaveSession)
    }
}
