package com.davidhc4.musica.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.widget.Toast
import com.davidhc4.musica.library.newDeviceTracks
import com.davidhc4.musica.library.scanDeviceMusic
import com.davidhc4.musica.library.sortLibrary
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
import com.davidhc4.musica.core.Track
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
    val playlists by app.playlists.state.collectAsState()
    val sleep by app.sleepTimer.state.collectAsState()

    // Diálogo abierto desde el menú del panel: "sleep", "speed" o ninguno.
    var dialog by remember { mutableStateOf<String?>(null) }

    // Ecualizador que trae el celular, si tiene uno.
    val equalizerIntent = remember {
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, app.audio.audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    val hasEqualizer = remember { equalizerIntent.resolveActivity(context.packageManager) != null }
    val equalizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val openEqualizer: () -> Unit = { runCatching { equalizer.launch(equalizerIntent) } }
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

    // Busca toda la música guardada en el celular y agrega la que falte en la biblioteca.
    val scanDevice: () -> Unit = {
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { scanDeviceMusic(context) }.getOrDefault(emptyList())
            }
            val fresh = newDeviceTracks(found, app.library.state.value.tracks)
            if (fresh.isNotEmpty()) app.mediator.post(AppEvent.AddToLibrary(fresh))
            val message = when {
                fresh.size == 1 -> "Se agregó 1 canción"
                fresh.isNotEmpty() -> "Se agregaron ${fresh.size} canciones"
                else -> "No se encontró música nueva en el celular"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    val askAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanDevice()
        } else {
            Toast.makeText(context, "Sin ese permiso no se puede buscar la música del celular", Toast.LENGTH_LONG).show()
        }
    }
    val findDeviceMusic: () -> Unit = {
        if (context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) scanDevice()
        else askAudio.launch(audioPermission)
    }

    // Opciones del menú de tres puntos, iguales para una canción en cualquier pantalla.
    var pickListFor by remember { mutableStateOf<Track?>(null) }
    val menuFor: (Track) -> List<MenuAction> = { track ->
        buildList {
            add(MenuAction("Reproducir a continuación") { app.mediator.post(AppEvent.PlayNext(track)) })
            add(MenuAction("Agregar a la cola") { app.mediator.post(AppEvent.Enqueue(listOf(track))) })
            add(MenuAction("Agregar a una lista") { pickListFor = track })
            add(
                MenuAction(if (track.id in likes.ids) "Quitar me gusta" else "Me gusta") {
                    app.mediator.post(AppEvent.ToggleLike(track))
                }
            )
            when (downloadStatus(track, downloads)) {
                DownloadStatus.AVAILABLE -> add(MenuAction("Descargar") { app.mediator.post(AppEvent.DownloadTrack(track)) })
                DownloadStatus.RUNNING -> add(MenuAction("Cancelar la descarga") { app.mediator.post(AppEvent.RemoveDownload(track.id)) })
                DownloadStatus.DONE -> add(MenuAction("Quitar la descarga") { app.mediator.post(AppEvent.RemoveDownload(track.id)) })
                DownloadStatus.NONE -> Unit
            }
        }
    }

    // Un mismo botón descarga la canción o, si ya está bajada o bajando, la quita.
    val toggleDownload: (Track) -> Unit = { track ->
        app.mediator.post(
            if (track.id in downloads.ids || track.id in downloads.inProgress) {
                AppEvent.RemoveDownload(track.id)
            } else {
                AppEvent.DownloadTrack(track)
            }
        )
    }

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
                        tracks = remember(library, settings.librarySort) { sortLibrary(library, settings.librarySort) },
                        menuFor = menuFor,
                        sort = settings.librarySort,
                        onSort = { app.mediator.post(AppEvent.SetLibrarySort(it)) },
                        onScanDevice = findDeviceMusic,
                        liked = likes.tracks,
                        downloaded = downloads.tracks,
                        playlists = playlists.playlists,
                        onCreatePlaylist = { app.mediator.post(AppEvent.CreatePlaylist(it)) },
                        onDeletePlaylist = { app.mediator.post(AppEvent.DeletePlaylist(it)) },
                        onRenamePlaylist = { id, name -> app.mediator.post(AppEvent.RenamePlaylist(id, name)) },
                        onRemoveFromPlaylist = { id, track ->
                            app.mediator.post(AppEvent.RemoveFromPlaylist(id, track.id))
                        },
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
                        downloads = downloads,
                        onDownload = toggleDownload,
                        menuFor = menuFor,
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
                    queue = queue,
                    panelMenu = buildList {
                        add(MenuAction("Agregar a una lista") { pickListFor = currentTrack })
                        add(
                            MenuAction(if (sleep.active) "Temporizador de apagado · activo" else "Temporizador de apagado") {
                                dialog = "sleep"
                            }
                        )
                        add(MenuAction("Velocidad · ${speedLabel(player.speed)}") { dialog = "speed" })
                        // El ecualizador del celular solo actúa sobre el audio propio, no sobre YouTube.
                        if (!isVideo && hasEqualizer) add(MenuAction("Ecualizador") { openEqualizer() })
                    },
                    onShuffle = { app.mediator.post(AppEvent.ToggleShuffle) },
                    onRepeat = { app.mediator.post(AppEvent.CycleRepeat) },
                    onPlayIndex = { app.mediator.post(AppEvent.PlayQueueIndex(it)) },
                    onRemoveFromQueue = { app.mediator.post(AppEvent.RemoveFromQueue(it)) },
                    download = downloadStatus(currentTrack, downloads),
                    downloadProgress = downloads.inProgress[currentTrack.id] ?: 0f,
                    onDownload = { toggleDownload(currentTrack) },
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

        when (dialog) {
            "sleep" -> ChoiceDialog(
                title = "Temporizador de apagado",
                options = listOf(
                    "15 minutos" to { app.mediator.post(AppEvent.SetSleepTimer(15)) },
                    "30 minutos" to { app.mediator.post(AppEvent.SetSleepTimer(30)) },
                    "60 minutos" to { app.mediator.post(AppEvent.SetSleepTimer(60)) },
                    "Al terminar la canción" to { app.mediator.post(AppEvent.SleepAtEndOfTrack) },
                    "Desactivado" to { app.mediator.post(AppEvent.SetSleepTimer(null)) },
                ),
                selected = when {
                    sleep.atEndOfTrack -> "Al terminar la canción"
                    sleep.minutes != null -> "${sleep.minutes} minutos"
                    else -> "Desactivado"
                },
                onDismiss = { dialog = null },
            )

            "speed" -> ChoiceDialog(
                title = "Velocidad",
                options = listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { value ->
                    speedLabel(value) to { app.mediator.post(AppEvent.SetSpeed(value)) }
                },
                selected = speedLabel(player.speed),
                onDismiss = { dialog = null },
            )
        }

        pickListFor?.let { track ->
            PlaylistPicker(
                playlists = playlists.playlists,
                trackId = track.id,
                onPick = {
                    app.mediator.post(AppEvent.AddToPlaylist(it, track))
                    pickListFor = null
                },
                onCreate = {
                    app.mediator.post(AppEvent.CreatePlaylist(it, track))
                    pickListFor = null
                },
                onDismiss = { pickListFor = null },
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

/** Escribe la velocidad como "1×" o "1,25×". */
private fun speedLabel(speed: Float): String =
    (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"
