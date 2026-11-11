package com.davidhc4.musica.ui

import androidx.activity.compose.BackHandler
import com.davidhc4.musica.library.Playlist
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.davidhc4.musica.core.LibrarySort
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
import com.davidhc4.musica.download.DownloadsState
import com.davidhc4.musica.download.canDownload
import com.davidhc4.musica.library.groupByAlbum
import com.davidhc4.musica.library.groupByArtist
import com.davidhc4.musica.library.searchTracks
import com.davidhc4.musica.queue.QueueState
import com.davidhc4.musica.queue.RepeatMode
import com.davidhc4.musica.search.SearchState
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
    playlists: List<Playlist> = emptyList(),
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (String) -> Unit = {},
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    onRemoveFromPlaylist: (String, Track) -> Unit = { _, _ -> },
    onMoveInPlaylist: (String, Int, Int) -> Unit = { _, _, _ -> },
    menuFor: (Track) -> List<MenuAction> = { emptyList() },
    onDownloadAll: (List<Track>) -> Unit = {},
    sort: LibrarySort = LibrarySort.TITLE,
    onSort: (LibrarySort) -> Unit = {},
    onScanDevice: () -> Unit = {},
) {
    var toRemove by remember { mutableStateOf<Track?>(null) }
    var naming by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Playlist?>(null) }
    var listOptions by remember { mutableStateOf<Playlist?>(null) }
    var toRename by remember { mutableStateOf<Playlist?>(null) }
    // Qué se ve bajo las listas: 0 canciones, 1 artistas, 2 álbumes.
    var view by rememberSaveable { mutableStateOf(0) }
    val artists = remember(tracks) { groupByArtist(tracks) }
    val albums = remember(tracks) { groupByAlbum(tracks) }
    // Álbum abierto: "liked", "downloads" o ninguno.
    var album by rememberSaveable { mutableStateOf<String?>(null) }

    // Una lista propia se abre como "pl:<id>". Si se borró mientras estaba abierta, se vuelve atrás.
    val openList = playlists.firstOrNull { "pl:${it.id}" == album }
    if (album?.startsWith("pl:") == true && openList == null) album = null
    // Un artista se abre como "ar:<nombre>" y un álbum como "al:<nombre>".
    val openGroup = artists.firstOrNull { "ar:${it.name}" == album } ?: albums.firstOrNull { "al:${it.name}" == album }
    if ((album?.startsWith("ar:") == true || album?.startsWith("al:") == true) && openGroup == null) album = null

    if (album != null) {
        BackHandler { album = null }
        if (openList != null) {
            AlbumScreen(
                title = openList.name,
                tracks = openList.tracks,
                currentId = currentId,
                artwork = artwork,
                emptyIcon = AppIcons.QueueMusic,
                emptyMessage = "Toca el botón de agregar a una lista en el reproductor para llenarla.",
                rowIcon = AppIcons.Close,
                rowAction = "Quitar de la lista",
                onBack = { album = null },
                onPlay = onPlay,
                menuFor = menuFor,
                onDownloadAll = onDownloadAll,
                onMove = { from, to -> onMoveInPlaylist(openList.id, from, to) },
                onRowAction = { onRemoveFromPlaylist(openList.id, it) },
            )
        } else if (openGroup != null) {
            AlbumScreen(
                title = openGroup.name,
                tracks = openGroup.tracks,
                currentId = currentId,
                artwork = artwork,
                emptyIcon = AppIcons.MusicNote,
                emptyMessage = "No hay canciones aquí.",
                rowIcon = null,
                rowAction = "",
                onBack = { album = null },
                onPlay = onPlay,
                menuFor = menuFor,
                onRowAction = {},
            )
        } else if (album == "liked") {
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
                menuFor = menuFor,
                onDownloadAll = onDownloadAll,
                onRowAction = onToggleLike,
            )
        } else {
            AlbumScreen(
                title = "Descargadas",
                tracks = downloaded,
                currentId = currentId,
                artwork = artwork,
                emptyIcon = AppIcons.Download,
                emptyMessage = "Aquí queda la música libre que bajes con la flecha de descarga, para oírla sin internet.",
                rowIcon = AppIcons.DownloadDone,
                rowAction = "Quitar la descarga",
                onBack = { album = null },
                onPlay = onPlay,
                menuFor = menuFor,
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
            Row {
                IconTap(AppIcons.Sync, "Buscar música en el celular", onScanDevice)
                IconTap(AppIcons.Add, "Agregar canciones", onAddSongs, tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (tracks.isEmpty() && liked.isEmpty() && downloaded.isEmpty() && playlists.isEmpty()) {
            EmptyState(
                icon = AppIcons.MusicNote,
                title = "Tu biblioteca está vacía",
                message = "Agrega canciones de tu celular y escúchalas sin conexión.",
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PillButton("Buscar música en el celular", onScanDevice)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onAddSongs) { Text("Elegir archivos a mano") }
                }
            }
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
                items(playlists, key = { "pl:${it.id}" }) { list ->
                    AlbumRow(
                        title = list.name,
                        count = list.tracks.size,
                        icon = AppIcons.QueueMusic,
                        colors = listOf(Color(0xFF55596B), Color(0xFF2F3240)),
                        onLongClick = { listOptions = list },
                    ) { album = "pl:${list.id}" }
                }
                item(key = "album-new") {
                    AlbumRow(
                        title = "Nueva lista",
                        count = null,
                        icon = AppIcons.Add,
                        colors = listOf(Color(0xFF3A3D4A), Color(0xFF3A3D4A)),
                    ) { naming = true }
                }
                if (tracks.isNotEmpty()) {
                    item(key = "header-songs") {
                        Row(
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Canciones", "Artistas", "Álbumes").forEachIndexed { index, label ->
                                    val selected = view == index
                                    Text(
                                        label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { view = index }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                            }
                            if (view == 0) SortMenu(sort, onSort)
                        }
                    }
                }
                if (view == 1) {
                    items(artists, key = { "ar:${it.name}" }) { group ->
                        AlbumRow(
                            title = group.name,
                            count = group.tracks.size,
                            icon = AppIcons.Person,
                            colors = listOf(Color(0xFF55596B), Color(0xFF2F3240)),
                        ) { album = "ar:${group.name}" }
                    }
                }
                if (view == 2) {
                    items(albums, key = { "al:${it.name}" }) { group ->
                        AlbumRow(
                            title = group.name,
                            count = group.tracks.size,
                            icon = AppIcons.Album,
                            colors = listOf(Color(0xFF55596B), Color(0xFF2F3240)),
                        ) { album = "al:${group.name}" }
                    }
                }
                itemsIndexed(if (view == 0) tracks else emptyList(), key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        artwork = artwork,
                        onClick = { onPlay(tracks, index) },
                        onLongClick = { toRemove = track },
                        menu = menuFor(track) + MenuAction("Quitar de la biblioteca") { toRemove = track },
                    )
                }
            }
        }
    }

    if (naming) {
        NameDialog(
            title = "Nueva lista",
            confirm = "Crear",
            onConfirm = {
                onCreatePlaylist(it)
                naming = false
            },
            onDismiss = { naming = false },
        )
    }

    listOptions?.let { list ->
        ChoiceDialog(
            title = list.name,
            options = listOf(
                "Cambiar el nombre" to { toRename = list },
                "Borrar la lista" to { toDelete = list },
            ),
            selected = null,
            onDismiss = { listOptions = null },
        )
    }

    toRename?.let { list ->
        NameDialog(
            title = "Cambiar el nombre",
            confirm = "Guardar",
            initial = list.name,
            onConfirm = {
                onRenamePlaylist(list.id, it)
                toRename = null
            },
            onDismiss = { toRename = null },
        )
    }

    toDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Borrar la lista") },
            text = { Text("“${list.name}” se borrará. Las canciones no se borran de tu biblioteca.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(list.id)
                    toDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
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

private fun sortLabel(sort: LibrarySort) = when (sort) {
    LibrarySort.TITLE -> "Por título"
    LibrarySort.ARTIST -> "Por artista"
    LibrarySort.RECENT -> "Recientes"
}

/** Deja elegir en qué orden se ven las canciones de la biblioteca. */
@Composable
private fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Sort,
                contentDescription = "Ordenar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(sortLabel(sort), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                )
            }
        }
    }
}

/** Flechas para subir o bajar una canción dentro de una lista. */
@Composable
private fun MoveArrows(index: Int, lastIndex: Int, onMove: (Int, Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row {
        IconTap(
            icon = AppIcons.ChevronUp,
            description = "Subir",
            onClick = { if (index > 0) onMove(index, index - 1) },
            size = 36.dp,
            iconSize = 22.dp,
            tint = colors.onSurfaceVariant.copy(alpha = if (index > 0) 1f else 0.3f),
        )
        IconTap(
            icon = AppIcons.ChevronDown,
            description = "Bajar",
            onClick = { if (index < lastIndex) onMove(index, index + 1) },
            size = 36.dp,
            iconSize = 22.dp,
            tint = colors.onSurfaceVariant.copy(alpha = if (index < lastIndex) 1f else 0.3f),
        )
    }
}

/** Diálogo con una lista de opciones para elegir una; la marcada es la que está en uso. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    selected: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, action) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                action()
                                onDismiss()
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
                        if (label == selected) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = "En uso",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Diálogo para escribir un nombre, por ejemplo el de una lista nueva. */
@Composable
private fun NameDialog(
    title: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Nombre") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Diálogo para meter la canción que suena en una lista existente o en una nueva. */
@Composable
fun PlaylistPicker(
    playlists: List<Playlist>,
    trackId: String,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a una lista") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { list ->
                    val already = list.tracks.any { it.id == trackId }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !already) { onPick(list.id) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            list.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (already) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = "Ya está en esta lista",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Nombre de una lista nueva") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Crear y agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/** Fila que abre un álbum fijo de la biblioteca, como "te gustan" o "descargadas". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    title: String,
    count: Int?,
    icon: ImageVector,
    colors: List<Color>,
    onLongClick: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
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
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    if (count == 1) "1 canción" else "$count canciones",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    rowIcon: ImageVector?,
    rowAction: String,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onRowAction: (Track) -> Unit,
    menuFor: (Track) -> List<MenuAction> = { emptyList() },
    onDownloadAll: ((List<Track>) -> Unit)? = null,
    /** Si se da, las filas muestran flechas para reordenar y el botón propio pasa al menú. */
    onMove: ((Int, Int) -> Unit)? = null,
) {
    val liked = tracks
    // Botón propio de cada fila (quitar me gusta, quitar descarga...). Algunas listas no tienen.
    val rowButton: (Track) -> (@Composable () -> Unit)? = { track ->
        if (rowIcon == null) {
            null
        } else {
            {
                IconTap(
                    icon = rowIcon,
                    description = rowAction,
                    onClick = { onRowAction(track) },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
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
            // Baja de una vez toda la música en línea de esta lista, para oírla sin internet.
            if (onDownloadAll != null && tracks.any { canDownload(it) }) {
                IconTap(AppIcons.Download, "Descargar todas", { onDownloadAll(tracks) })
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
                        trailing = if (onMove != null) {
                            { MoveArrows(index, liked.lastIndex, onMove) }
                        } else {
                            rowButton(track)
                        },
                        menu = menuFor(track) + listOfNotNull(
                            if (onMove != null && rowIcon != null) MenuAction(rowAction) { onRowAction(track) } else null
                        ),
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
    downloads: DownloadsState = DownloadsState(),
    onDownload: (Track) -> Unit = {},
    menuFor: (Track) -> List<MenuAction> = { emptyList() },
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
                            menu = menuFor(track),
                        )
                    }
                }

                if (onlineActive && online.isEmpty()) {
                    item(key = "header-online") { ListHeader("En línea") }
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
                listOf("Música libre · se puede descargar" to online).forEach { (title, group) ->
                    if (group.isEmpty()) return@forEach
                    item(key = "header:$title") { ListHeader(title) }
                    itemsIndexed(group, key = { _, track -> "online:${track.id}" }) { index, track ->
                        val saved = track.id in savedIds
                        SongRow(
                            track = track,
                            isCurrent = track.id == currentId,
                            artwork = artwork,
                            onClick = { onPlay(group, index) },
                            // A la vista queda lo principal: descargar si se puede y, si no, guardar.
                            // Lo demás está en el menú de tres puntos.
                            trailing = {
                                val status = downloadStatus(track, downloads)
                                if (status != DownloadStatus.NONE) {
                                    DownloadControl(
                                        status = status,
                                        progress = downloads.inProgress[track.id] ?: 0f,
                                        onClick = { onDownload(track) },
                                    )
                                } else {
                                    IconTap(
                                        icon = if (saved) AppIcons.Check else AppIcons.Add,
                                        description = if (saved) "Guardada en tu biblioteca" else "Guardar en tu biblioteca",
                                        onClick = { if (!saved) onSave(track) },
                                        tint = if (saved) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            menu = menuFor(track) + listOfNotNull(
                                if (saved) null else MenuAction("Guardar en tu biblioteca") { onSave(track) }
                            ),
                        )
                    }
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
    backgroundAllowed: Boolean = true,
    onAllowBackground: () -> Unit = {},
    downloads: DownloadsState = DownloadsState(),
    onDownloadLimit: (Int) -> Unit = {},
    onClearDownloads: () -> Unit = {},
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
            SectionLabel("Música en segundo plano")
            BackgroundSection(backgroundAllowed, onAllowBackground)

            Spacer(Modifier.height(16.dp))
            SectionLabel("Descargas")
            DownloadsSection(downloads, settings.downloadLimitMb, onDownloadLimit, onClearDownloads)

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

private fun sizeLabel(megabytes: Int): String = when {
    megabytes <= 0 -> "Sin límite"
    megabytes >= 1024 && megabytes % 1024 == 0 -> "${megabytes / 1024} GB"
    else -> "$megabytes MB"
}

private fun usedLabel(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024) String.format(java.util.Locale.US, "%.1f GB", megabytes / 1024).replace('.', ',')
    else "${megabytes.toInt()} MB"
}

/** Cuánto ocupan las descargas, el tope de espacio y el botón para borrarlas todas. */
@Composable
private fun DownloadsSection(
    downloads: DownloadsState,
    limitMb: Int,
    onLimit: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val count = downloads.tracks.size

    Text(
        (if (count == 1) "1 canción descargada" else "$count canciones descargadas") +
            " · ocupan ${usedLabel(downloads.usedBytes)}" +
            (if (limitMb > 0) " de ${sizeLabel(limitMb)}" else ""),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { choosing = true }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Límite de espacio: ${sizeLabel(limitMb)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    if (count > 0) {
        TextButton(onClick = { confirming = true }) { Text("Borrar todas las descargas") }
    }

    if (choosing) {
        ChoiceDialog(
            title = "Límite de espacio para descargas",
            options = listOf(0, 500, 1024, 2048, 5120).map { mb -> sizeLabel(mb) to { onLimit(mb) } },
            selected = sizeLabel(limitMb),
            onDismiss = { choosing = false },
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Borrar todas las descargas") },
            text = { Text("Se borran del celular las $count canciones descargadas. Siguen en tu biblioteca y en tus listas, pero necesitarán internet para sonar.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirming = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancelar") } },
        )
    }
}

/** Explica si Android deja a la app seguir sonando con la pantalla apagada, y cómo permitirlo. */
@Composable
private fun BackgroundSection(allowed: Boolean, onAllow: () -> Unit) {
    Text(
        if (allowed) {
            "Permitido: Android no cierra la app para ahorrar batería mientras suena la música."
        } else {
            "Android puede cortar la música con la pantalla apagada para ahorrar batería. Toca el botón para permitir que siga sonando."
        },
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!allowed) {
        Spacer(Modifier.height(10.dp))
        PillButton("Permitir música en segundo plano", onAllow, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "En celulares Xiaomi, Redmi o POCO revisa también: Ajustes del celular → Aplicaciones → Música → Ahorro de batería → Sin restricciones.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Qué puede hacerse con la descarga de una canción. */
enum class DownloadStatus { NONE, AVAILABLE, RUNNING, DONE }

fun downloadStatus(track: Track, downloads: DownloadsState): DownloadStatus = when {
    track.id in downloads.ids -> DownloadStatus.DONE
    track.id in downloads.inProgress -> DownloadStatus.RUNNING
    canDownload(track) -> DownloadStatus.AVAILABLE
    else -> DownloadStatus.NONE
}

/** Botón de descarga: flecha para bajar, círculo mientras baja y marca cuando ya está en el celular. */
@Composable
fun DownloadControl(status: DownloadStatus, progress: Float, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        DownloadStatus.NONE -> Unit
        DownloadStatus.RUNNING -> Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // Algunas fuentes no dicen el tamaño; entonces el círculo gira sin porcentaje.
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        else -> IconTap(
            icon = if (status == DownloadStatus.DONE) AppIcons.DownloadDone else AppIcons.Download,
            description = if (status == DownloadStatus.DONE) "Quitar la descarga" else "Descargar",
            onClick = onClick,
            iconSize = 26.dp,
            tint = if (status == DownloadStatus.DONE) colors.primary else colors.onSurfaceVariant,
        )
    }
}

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
    liked: Boolean = false,
    onToggleLike: () -> Unit = {},
    download: DownloadStatus = DownloadStatus.NONE,
    downloadProgress: Float = 0f,
    onDownload: () -> Unit = {},
    queue: QueueState = QueueState(),
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onPlayIndex: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    panelMenu: List<MenuAction> = emptyList(),
) {
    var showQueue by rememberSaveable { mutableStateOf(false) }

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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceVariant,
            )
            IconTap(
                icon = AppIcons.QueueMusic,
                description = if (showQueue) "Ver la carátula" else "Ver la cola",
                onClick = { showQueue = !showQueue },
                tint = if (showQueue) colors.primary else colors.onSurface,
            )
            // Opciones menos frecuentes: agregar a una lista, temporizador, velocidad, ecualizador.
            if (panelMenu.isNotEmpty()) SongMenu(panelMenu)
        }

        if (showQueue) {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                item(key = "queue-title") {
                    Text(
                        "En cola · ${queue.tracks.size} canciones",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                }
                itemsIndexed(queue.tracks) { index, item ->
                    SongRow(
                        track = item,
                        isCurrent = index == queue.currentIndex,
                        artwork = artwork,
                        onClick = { onPlayIndex(index) },
                        trailing = { MoveArrows(index, queue.tracks.lastIndex, onMoveInQueue) },
                        menu = listOf(
                            MenuAction("Mover al principio") { onMoveInQueue(index, 0) },
                            MenuAction("Mover al final") { onMoveInQueue(index, queue.tracks.lastIndex) },
                            MenuAction("Quitar de la cola") { onRemoveFromQueue(index) },
                        ),
                    )
                }
            }
        } else Box(
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
                // Cuadrada y tan grande como quepa a lo ancho y a lo alto, sin salirse de los márgenes.
                modifier = Modifier
                    .aspectRatio(1f)
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
            DownloadControl(download, downloadProgress, onDownload)
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTap(
                icon = AppIcons.Shuffle,
                description = if (queue.shuffle) "Quitar aleatorio" else "Aleatorio",
                onClick = onShuffle,
                tint = if (queue.shuffle) colors.primary else colors.onSurfaceVariant,
            )
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
            IconTap(
                icon = if (queue.repeat == RepeatMode.ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                description = when (queue.repeat) {
                    RepeatMode.OFF -> "Repetir todas"
                    RepeatMode.ALL -> "Repetir esta canción"
                    RepeatMode.ONE -> "No repetir"
                },
                onClick = onRepeat,
                tint = if (queue.repeat == RepeatMode.OFF) colors.onSurfaceVariant else colors.primary,
            )
        }
    }
}
