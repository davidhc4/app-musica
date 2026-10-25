package com.davidhc4.musica

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.davidhc4.musica.ui.MusicaRoot
import com.davidhc4.musica.ui.MusicaTheme
import com.davidhc4.musica.ui.isDarkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MusicaApp
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
}
