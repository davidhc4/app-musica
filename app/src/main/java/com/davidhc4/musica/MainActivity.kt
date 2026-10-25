package com.davidhc4.musica

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MusicaApp
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerScreen(app)
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(app: MusicaApp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val queue by app.queue.state.collectAsState()
    val player by app.player.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val tracks = uris.map { trackFromUri(context, it) }
        if (tracks.isNotEmpty()) app.mediator.post(AppEvent.Enqueue(tracks))
    }

    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(player.track, player.isPlaying) {
        while (true) {
            if (!dragging) {
                position = app.player.positionMs().toFloat()
                duration = app.player.durationMs().toFloat()
            }
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Música", style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = { picker.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Agregar canciones") }

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(queue.tracks, key = { index, track -> "$index-${track.id}" }) { index, track ->
                val isCurrent = index == queue.currentIndex
                Text(
                    text = track.title,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            app.mediator.post(AppEvent.PlayTracks(queue.tracks, index))
                        }
                        .padding(vertical = 12.dp),
                )
            }
        }

        Text(
            text = player.track?.title ?: "Nada reproduciéndose",
            style = MaterialTheme.typography.titleMedium,
        )

        Slider(
            value = position.coerceAtMost(duration.coerceAtLeast(1f)),
            onValueChange = {
                dragging = true
                position = it
            },
            onValueChangeFinished = {
                app.mediator.post(AppEvent.SeekTo(position.toLong()))
                dragging = false
            },
            valueRange = 0f..duration.coerceAtLeast(1f),
            enabled = player.track != null,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { app.mediator.post(AppEvent.Previous) }) { Text("Anterior") }
            Button(onClick = {
                app.mediator.post(if (player.isPlaying) AppEvent.Pause else AppEvent.Resume)
            }) { Text(if (player.isPlaying) "Pausar" else "Reproducir") }
            OutlinedButton(onClick = { app.mediator.post(AppEvent.Next) }) { Text("Siguiente") }
        }
    }
}

private fun trackFromUri(context: Context, uri: Uri): Track {
    val name = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        ?: uri.lastPathSegment
        ?: "Canción"
    return Track(
        id = uri.toString(),
        title = name.substringBeforeLast('.'),
        uri = uri.toString(),
    )
}
