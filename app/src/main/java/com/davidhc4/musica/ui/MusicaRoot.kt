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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import com.davidhc4.musica.MusicaApp
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.download.canDownload
import com.davidhc4.musica.library.readTrack
import com.davidhc4.musica.player.PlayerColleague
import com.davidhc4.musica.player.PlayerState
import com.davidhc4.musica.source.isYouTubeUri
import com.davidhc4.musica.update.UpdateStatus
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
    val search by app.search.state.collectAsState()
    val update by app.updates.state.collectAsState()
    val likes by app.likes.state.collectAsState()
    val downloads by app.downloads.state.collectAsState()
    val progress by rememberPlaybackProgress(app.player, player)

    var tab by rememberSaveable { mutableStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    // Versión nueva que ya se ofreció y se dejó para después; no se vuelve a preguntar por ella.
    var postponedVersion by rememberSaveable { mutableStateOf<String?>(null) }

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
    // El video de YouTube va en una sola vista que se coloca sobre el hueco del panel o del mini reproductor.
    val isVideo = isYouTubeUri(currentTrack?.uri)
    var panelVideoBounds by remember { mutableStateOf<Rect?>(null) }
    var miniVideoBounds by remember { mutableStateOf<Rect?>(null) }
    val miniSlot: (@Composable (Modifier) -> Unit)? =
        if (isVideo) ({ m -> VideoSlot(m) { miniVideoBounds = it } }) else null
    val panelSlot: (@Composable (Modifier) -> Unit)? =
        if (isVideo) ({ m -> VideoSlot(m) { panelVideoBounds = it } }) else null
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
                        liked = likes.tracks,
                        downloaded = downloads.tracks,
                        onRemoveDownload = { app.mediator.post(AppEvent.RemoveDownload(it.id)) },
                        currentId = currentTrack?.id,
                        artwork = app.artwork,
                        onAddSongs = addSongs,
                        onToggleLike = { app.mediator.post(AppEvent.ToggleLike(it)) },
                        onPlay = { tracks, index -> app.mediator.post(AppEvent.PlayTracks(tracks, index)) },
                        onRemove = { app.mediator.post(AppEvent.RemoveFromLibrary(it.id)) },
                    )

                    1 -> SearchScreen(
                        library = library.tracks,
                        search = search,
                        currentId = currentTrack?.id,
                        artwork = app.artwork,
                        onQueryChange = { app.mediator.post(AppEvent.SearchRequested(it)) },
                        onPlay = { tracks, index -> app.mediator.post(AppEvent.PlayTracks(tracks, index)) },
                        onSave = { app.mediator.post(AppEvent.AddToLibrary(listOf(it))) },
                    )

                    else -> SettingsScreen(
                        settings = settings,
                        versionName = remember(context) {
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull().orEmpty()
                        },
                        update = update,
                        onMode = { app.mediator.post(AppEvent.SetThemeMode(it)) },
                        onYouTubeKey = { app.mediator.post(AppEvent.SetYouTubeKey(it)) },
                        onCheckUpdates = { app.mediator.post(AppEvent.CheckForUpdates) },
                        onInstallUpdate = { app.mediator.post(AppEvent.InstallUpdate) },
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
                    videoSlot = miniSlot,
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
                    videoSlot = panelSlot,
                    liked = currentTrack.id in likes.ids,
                    onToggleLike = { app.mediator.post(AppEvent.ToggleLike(currentTrack)) },
                    download = when {
                        currentTrack.id in downloads.ids -> DownloadStatus.DONE
                        currentTrack.id in downloads.inProgress -> DownloadStatus.RUNNING
                        canDownload(currentTrack) -> DownloadStatus.AVAILABLE
                        else -> DownloadStatus.NONE
                    },
                    downloadProgress = downloads.inProgress[currentTrack.id] ?: 0f,
                    onDownload = {
                        app.mediator.post(
                            if (currentTrack.id in downloads.ids || currentTrack.id in downloads.inProgress) {
                                AppEvent.RemoveDownload(currentTrack.id)
                            } else {
                                AppEvent.DownloadTrack(currentTrack)
                            }
                        )
                    },
                )
            }
        }

        val videoBounds = when {
            !isVideo -> null
            playerOpen -> panelVideoBounds ?: miniVideoBounds
            else -> miniVideoBounds
        }
        if (videoBounds != null) {
            VideoOverlay(
                view = app.youtube.view,
                bounds = videoBounds,
                interactive = playerOpen && panelVideoBounds != null,
            )
        }

        val offered = update.latestVersion
        if (update.status == UpdateStatus.AVAILABLE && offered != null && offered != postponedVersion) {
            AlertDialog(
                onDismissRequest = { postponedVersion = offered },
                title = { Text("Hay una versión nueva") },
                text = { Text("Música $offered está lista para descargar. Se instala encima de la actual y no se pierde tu biblioteca.") },
                confirmButton = {
                    TextButton(onClick = {
                        postponedVersion = offered
                        app.mediator.post(AppEvent.InstallUpdate)
                    }) { Text("Actualizar") }
                },
                dismissButton = {
                    TextButton(onClick = { postponedVersion = offered }) { Text("Después") }
                },
            )
        }
    }
}
