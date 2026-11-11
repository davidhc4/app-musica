package com.davidhc4.musica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.ThemeMode
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.searchTracks
import com.davidhc4.musica.search.SearchState
import com.davidhc4.musica.source.isYouTubeUri
import com.davidhc4.musica.update.UpdateState
import com.davidhc4.musica.update.UpdateStatus

@Composable
private fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    currentId: String?,
    artwork: ArtworkLoader,
    onAddSongs: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onRemove: (Track) -> Unit,
    liked: List<Track> = emptyList(),
    onToggleLike: (Track) -> Unit = {},
    downloaded: List<Track> = emptyList(),
    onRemoveDownload: (Track) -> Unit = {},
) {
    var toRemove by remember { mutableStateOf<Track?>(null) }
    // Álbum abierto: "liked", "downloads" o ninguno.
    var album by rememberSaveable { mutableStateOf<String?>(null) }

    if (album != null) {
        BackHandler { album = null }
        if (album == "liked") {
            AlbumScreen(
                title = "Te gustan",
                tracks = liked,
                currentId = currentId,
                artwork = artwork,
                emptyIcon = AppIcons.FavoriteBorder,
                emptyMessage = "Toca el corazón en el reproductor y la canción se guarda aquí.",
                rowIcon = AppIcons.Favorite,
                rowAction = "Quitar de las que te gustan",
                onBack = { album = null },
                onPlay = onPlay,
                onRowAction = onToggleLike,
            )
        } else {
            AlbumScreen(
                title = "Descargadas",
                tracks = downloaded,
                currentId = currentId,
                artwork = artwork,
                emptyIcon = AppIcons.Download,
                emptyMessage = "Toca la flecha de descarga en el reproductor y la canción suena sin internet.",
                rowIcon = AppIcons.DownloadDone,
                rowAction = "Quitar la descarga",
                onBack = { album = null },
                onPlay = onPlay,
                onRowAction = onRemoveDownload,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            title = "Tu música",
            subtitle = if (tracks.isEmpty()) null else "${tracks.size} canciones",
        ) {
            IconTap(AppIcons.Add, "Agregar canciones", onAddSongs, tint = MaterialTheme.colorScheme.primary)
        }

        if (tracks.isEmpty() && liked.isEmpty() && downloaded.isEmpty()) {
            EmptyState(
                icon = AppIcons.MusicNote,
                title = "Tu biblioteca está vacía",
                message = "Agrega canciones de tu celular y escúchalas sin conexión.",
                modifier = Modifier.weight(1f),
            ) { PillButton("Agregar canciones", onAddSongs) }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                item(key = "album-liked") {
                    AlbumRow(
                        title = "Canciones que te gustan",
                        count = liked.size,
                        icon = AppIcons.Favorite,
                        colors = listOf(Color(0xFF7B5CF0), Color(0xFFE0569B)),
                    ) { album = "liked" }
                }
                item(key = "album-downloads") {
                    AlbumRow(
                        title = "Descargadas",
                        count = downloaded.size,
                        icon = AppIcons.Download,
                        colors = listOf(Color(0xFF2E9E6B), Color(0xFF2A7FD0)),
                    ) { album = "downloads" }
                }
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        artwork = artwork,
                        onClick = { onPlay(tracks, index) },
                        onLongClick = { toRemove = track },
                    )
                }
            }
        }
    }

    toRemove?.let { track ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Quitar de la biblioteca") },
            text = { Text("“${track.title}” dejará de aparecer aquí. El archivo no se borra de tu celular.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(track)
                    toRemove = null
                }) { Text("Quitar") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancelar") } },
        )
    }
}

/** Fila que abre un álbum fijo de la biblioteca, como "te gustan" o "descargadas". */
@Composable
private fun AlbumRow(title: String, count: Int, icon: ImageVector, colors: List<Color>, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                if (count == 1) "1 canción" else "$count canciones",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlbumScreen(
    title: String,
    tracks: List<Track>,
    currentId: String?,
    artwork: ArtworkLoader,
    emptyIcon: ImageVector,
    emptyMessage: String,
    rowIcon: ImageVector,
    rowAction: String,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onRowAction: (Track) -> Unit,
) {
    val liked = tracks
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(AppIcons.ArrowBack, "Volver", onBack)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (liked.size == 1) "1 canción" else "${liked.size} canciones",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (liked.isEmpty()) {
            EmptyState(
                icon = emptyIcon,
                title = "Aún no hay canciones",
                message = emptyMessage,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                itemsIndexed(liked, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        artwork = artwork,
                        onClick = { onPlay(liked, index) },
                        trailing = {
                            IconTap(
                                icon = rowIcon,
                                description = rowAction,
                                onClick = { onRowAction(track) },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun SearchScreen(
    library: List<Track>,
    search: SearchState,
    currentId: String?,
    artwork: ArtworkLoader,
    onQueryChange: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onSave: (Track) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val local = remember(query, library) { searchTracks(library, query) }
    val savedIds = remember(library) { library.map { it.id }.toSet() }
    val online = if (query.isBlank()) emptyList() else search.results
    val onlineActive = query.isNotBlank() && (search.loading || search.failed || online.isNotEmpty())

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(title = "Buscar")

        TextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            singleLine = true,
            placeholder = { Text("Canciones, artistas o álbumes") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconTap(
                        AppIcons.Close,
                        "Borrar búsqueda",
                        {
                            query = ""
                            onQueryChange("")
                        },
                        size = 40.dp,
                        iconSize = 20.dp,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        when {
            query.isBlank() -> EmptyState(
                icon = AppIcons.Search,
                title = "Busca música",
                message = "Escribe una canción, un artista o un álbum y escúchalo al instante, sin tenerlo en tu celular.",
                modifier = Modifier.weight(1f),
            )

            local.isEmpty() && !onlineActive -> EmptyState(
                icon = AppIcons.Search,
                title = "Sin resultados",
                message = "No encontramos nada para “${query.trim()}”.",
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (local.isNotEmpty()) {
                    item(key = "header-local") { ListHeader("En tu biblioteca") }
                    itemsIndexed(local, key = { _, track -> "local:${track.id}" }) { index, track ->
                        SongRow(
                            track = track,
                            isCurrent = track.id == currentId,
                            artwork = artwork,
                            onClick = { onPlay(local, index) },
                        )
                    }
                }

                if (onlineActive) {
                    val fromYouTube = online.firstOrNull()?.let { isYouTubeUri(it.uri) } == true
                    item(key = "header-online") { ListHeader(if (fromYouTube) "En YouTube" else "En línea") }
                }
                if (search.loading && online.isEmpty()) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (search.failed && online.isEmpty()) {
                    item(key = "failed") {
                        Text(
                            "No se pudo buscar en línea. Revisa tu conexión a internet.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }
                }
                itemsIndexed(online, key = { _, track -> "online:${track.id}" }) { index, track ->
                    val saved = track.id in savedIds
                    SongRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        artwork = artwork,
                        onClick = { onPlay(online, index) },
                        trailing = {
                            IconTap(
                                icon = if (saved) AppIcons.Check else AppIcons.Add,
                                description = if (saved) "Guardada en tu biblioteca" else "Guardar en tu biblioteca",
                                onClick = { if (!saved) onSave(track) },
                                tint = if (saved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp, top = 8.dp),
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    versionName: String,
    update: UpdateState,
    onMode: (ThemeMode) -> Unit,
    onYouTubeKey: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(title = "Ajustes")

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            SectionLabel("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.DARK to "Oscuro",
                    ThemeMode.SYSTEM to "Automático",
                    ThemeMode.LIGHT to "Claro",
                ).forEach { (mode, label) ->
                    val selected = settings.mode == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onMode(mode) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("YouTube")
            YouTubeKeySection(settings.youTubeKey, onYouTubeKey)

            Spacer(Modifier.height(16.dp))
            SectionLabel("Actualizaciones")
            UpdateSection(update, onCheckUpdates, onInstallUpdate)

            Spacer(Modifier.height(16.dp))
            SectionLabel("Acerca de")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MakerMark(size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text("Música", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Versión $versionName",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YouTubeKeySection(savedKey: String, onKey: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(savedKey) }
    Text(
        if (savedKey.isBlank()) "Pega aquí tu llave de YouTube para buscar y oír canciones de artistas famosos."
        else "Llave guardada. La búsqueda usa YouTube; el video se ve mientras suena y se pausa al salir de la app.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    TextField(
        value = text,
        onValueChange = {
            text = it.trim()
            onKey(text)
        },
        singleLine = true,
        placeholder = { Text("Llave de YouTube") },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconTap(
                    AppIcons.Close,
                    "Borrar llave",
                    {
                        text = ""
                        onKey("")
                    },
                    size = 40.dp,
                    iconSize = 20.dp,
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UpdateSection(update: UpdateState, onCheck: () -> Unit, onInstall: () -> Unit) {
    val version = update.latestVersion.orEmpty()
    val message = when (update.status) {
        UpdateStatus.IDLE -> "Toca para buscar una versión nueva."
        UpdateStatus.CHECKING -> "Buscando…"
        UpdateStatus.UP_TO_DATE -> "Tienes la última versión."
        UpdateStatus.AVAILABLE -> "Hay una versión nueva: $version"
        UpdateStatus.DOWNLOADING -> "Descargando $version… ${(update.progress * 100).toInt()}%"
        UpdateStatus.READY -> "Versión $version descargada. Falta instalarla."
        UpdateStatus.FAILED -> "No se pudo. Revisa tu conexión e intenta de nuevo."
    }
    // Si falló la descarga se reintenta la descarga; si falló la búsqueda, la búsqueda.
    val canInstall = update.latestVersion != null &&
        update.status in setOf(UpdateStatus.AVAILABLE, UpdateStatus.READY, UpdateStatus.FAILED)
    val busy = update.status == UpdateStatus.CHECKING || update.status == UpdateStatus.DOWNLOADING
    val label = when {
        update.status == UpdateStatus.READY -> "Instalar"
        update.status == UpdateStatus.FAILED -> "Reintentar"
        canInstall -> "Actualizar"
        else -> "Buscar actualizaciones"
    }

    Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(
                if (canInstall) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = !busy) { if (canInstall) onInstall() else onCheck() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = (if (canInstall) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface).copy(alpha = if (busy) 0.5f else 1f),
        )
    }
}

/** Qué puede hacerse con la descarga de la canción que suena. */
enum class DownloadStatus { NONE, AVAILABLE, RUNNING, DONE }

@Composable
fun PlayerScreen(
    track: Track,
    isPlaying: Boolean,
    progress: PlaybackProgress,
    artwork: ArtworkLoader,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    videoSlot: (@Composable (Modifier) -> Unit)? = null,
    liked: Boolean = false,
    onToggleLike: () -> Unit = {},
    download: DownloadStatus = DownloadStatus.NONE,
    downloadProgress: Float = 0f,
    onDownload: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val tint = colors.primary.copy(alpha = 0.24f).compositeOver(colors.background)

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, colors.background)))
            // Evita que los toques atraviesen el panel y lleguen a la pantalla de atrás.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(AppIcons.ChevronDown, "Cerrar", onClose, iconSize = 28.dp)
            Text(
                "Reproduciendo",
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
            if (videoSlot != null) {
                // YouTube pide que su reproductor siga a la vista mientras suena.
                videoSlot(Modifier.size(width = 80.dp, height = 45.dp))
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            TrackArtwork(
                track = track,
                loader = artwork,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .shadow(24.dp, RoundedCornerShape(24.dp)),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.artistOrUnknown(),
                    fontSize = 16.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (download) {
                DownloadStatus.NONE -> Unit
                DownloadStatus.RUNNING -> Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDownload),
                    contentAlignment = Alignment.Center,
                ) {
                    // Algunas fuentes no dicen el tamaño; entonces el círculo gira sin porcentaje.
                    if (downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                else -> IconTap(
                    icon = if (download == DownloadStatus.DONE) AppIcons.DownloadDone else AppIcons.Download,
                    description = if (download == DownloadStatus.DONE) "Quitar la descarga" else "Descargar",
                    onClick = onDownload,
                    iconSize = 26.dp,
                    tint = if (download == DownloadStatus.DONE) colors.primary else colors.onSurfaceVariant,
                )
            }
            IconTap(
                icon = if (liked) AppIcons.Favorite else AppIcons.FavoriteBorder,
                description = if (liked) "Quitar de las que te gustan" else "Me gusta",
                onClick = onToggleLike,
                iconSize = 28.dp,
                tint = if (liked) colors.primary else colors.onSurfaceVariant,
            )
        }
        if (track.isPreview) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Fragmento de 30 segundos",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        Spacer(Modifier.height(20.dp))
        SlimSeekBar(progress = progress.fraction, enabled = progress.durationMs > 0, onSeek = onSeek)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(progress.positionMs), fontSize = 12.sp, color = colors.onSurfaceVariant)
            Text(formatTime(progress.durationMs), fontSize = 12.sp, color = colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(AppIcons.SkipPrevious, "Anterior", onPrevious, size = 56.dp, iconSize = 34.dp)
            IconTap(
                icon = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                description = if (isPlaying) "Pausar" else "Reproducir",
                onClick = onPlayPause,
                modifier = Modifier.background(colors.primary, CircleShape),
                size = 76.dp,
                iconSize = 40.dp,
                tint = colors.onPrimary,
            )
            IconTap(AppIcons.SkipNext, "Siguiente", onNext, size = 56.dp, iconSize = 34.dp)
        }
    }
}
