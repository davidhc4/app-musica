package com.davidhc4.musica.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.davidhc4.musica.MusicaApp
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.library.readTrack
import com.davidhc4.musica.player.PlayerColleague
import com.davidhc4.musica.player.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackProgress(val positionMs: Long = 0L, val durationMs: Long = 0L) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Consulta la posición del audio unas cuantas veces por segundo para mover la barra. */
@Composable
private fun rememberPlaybackProgress(
    player: PlayerColleague,
    state: PlayerState,
): State<PlaybackProgress> =
    produceState(PlaybackProgress(), state.track?.id, state.isPlaying) {
        while (true) {
            val engineDuration = player.durationMs()
            val duration = if (engineDuration > 0) engineDuration else state.track?.durationMs ?: 0L
            value = PlaybackProgress(player.positionMs().coerceAtLeast(0L), duration)
            delay(if (state.isPlaying) 250L else 600L)
        }
    }

@Composable
fun MusicaRoot(app: MusicaApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val library by app.library.state.collectAsState()
    val queue by app.queue.state.collectAsState()
    val player by app.player.state.collectAsState()
    val settings by app.settings.state.collectAsState()
    val progress by rememberPlaybackProgress(app.player, player)

    var tab by rememberSaveable { mutableStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val tracks = withContext(Dispatchers.IO) {
                    uris.mapNotNull { runCatching { readTrack(context, it) }.getOrNull() }
                }
                if (tracks.isNotEmpty()) app.mediator.post(AppEvent.AddToLibrary(tracks))
            }
        }
    }
    val addSongs = { picker.launch(arrayOf("audio/*")) }

    val currentTrack = player.track
    BackHandler(enabled = playerOpen) { playerOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> LibraryScreen(
                        tracks = library.tracks,
                        currentId = currentTrack?.id,
                        artwork = app.artwork,
                        onAddSongs = addSongs,
                        onPlay = { tracks, index -> app.mediator.post(AppEvent.PlayTracks(tracks, index)) },
                        onRemove = { app.mediator.post(AppEvent.RemoveFromLibrary(it.id)) },
                    )

                    1 -> SearchScreen(
                        library = library.tracks,
                        currentId = currentTrack?.id,
                        artwork = app.artwork,
                        onPlay = { tracks, index -> app.mediator.post(AppEvent.PlayTracks(tracks, index)) },
                    )

                    else -> SettingsScreen(
                        settings = settings,
                        versionName = remember(context) {
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull().orEmpty()
                        },
                        onMode = { app.mediator.post(AppEvent.SetThemeMode(it)) },
                        onAccent = { app.mediator.post(AppEvent.SetAccent(it)) },
                        onAmoled = { app.mediator.post(AppEvent.SetAmoled(it)) },
                    )
                }
            }

            if (currentTrack != null) {
                MiniPlayer(
                    track = currentTrack,
                    isPlaying = player.isPlaying,
                    progress = progress.fraction,
                    artwork = app.artwork,
                    onOpen = { playerOpen = true },
                    onPlayPause = {
                        app.mediator.post(if (player.isPlaying) AppEvent.Pause else AppEvent.Resume)
                    },
                    onNext = { app.mediator.post(AppEvent.Next) },
                )
            }
            BottomBar(selected = tab, onSelect = { tab = it })
        }

        AnimatedVisibility(
            visible = playerOpen && currentTrack != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            if (currentTrack != null) {
                PlayerScreen(
                    track = currentTrack,
                    isPlaying = player.isPlaying,
                    progress = progress,
                    artwork = app.artwork,
                    onClose = { playerOpen = false },
                    onPlayPause = {
                        app.mediator.post(if (player.isPlaying) AppEvent.Pause else AppEvent.Resume)
                    },
                    onNext = { app.mediator.post(AppEvent.Next) },
                    onPrevious = { app.mediator.post(AppEvent.Previous) },
                    onSeek = { fraction ->
                        app.mediator.post(AppEvent.SeekTo((fraction * progress.durationMs).toLong()))
                    },
                )
            }
        }
    }
}
