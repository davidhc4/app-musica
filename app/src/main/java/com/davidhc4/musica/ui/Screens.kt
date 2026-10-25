package com.davidhc4.musica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidhc4.musica.core.Accent
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.ThemeMode
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.searchTracks

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
) {
    var toRemove by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(
            title = "Tu música",
            subtitle = if (tracks.isEmpty()) null else "${tracks.size} canciones",
        ) {
            IconTap(AppIcons.Add, "Agregar canciones", onAddSongs, tint = MaterialTheme.colorScheme.primary)
        }

        if (tracks.isEmpty()) {
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

@Composable
fun SearchScreen(
    library: List<Track>,
    currentId: String?,
    artwork: ArtworkLoader,
    onPlay: (List<Track>, Int) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, library) { searchTracks(library, query) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(title = "Buscar")

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Canciones, artistas o álbumes") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconTap(AppIcons.Close, "Borrar búsqueda", { query = "" }, size = 40.dp, iconSize = 20.dp)
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
        Spacer(Modifier.height(12.dp))

        when {
            query.isBlank() -> EmptyState(
                icon = AppIcons.Search,
                title = "Busca en tu música",
                message = "Escribe el nombre de una canción, un artista o un álbum.",
                modifier = Modifier.weight(1f),
            )

            results.isEmpty() -> EmptyState(
                icon = AppIcons.Search,
                title = "Sin resultados",
                message = "No encontramos nada para “${query.trim()}”.",
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                itemsIndexed(results, key = { _, track -> track.id }) { index, track ->
                    SongRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        artwork = artwork,
                        onClick = { onPlay(results, index) },
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
    onMode: (ThemeMode) -> Unit,
    onAccent: (Accent) -> Unit,
    onAmoled: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTitle(title = "Ajustes")

        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionLabel("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "Sistema",
                    ThemeMode.LIGHT to "Claro",
                    ThemeMode.DARK to "Oscuro",
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

            SectionLabel("Color de acento")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accent.entries.forEach { accent ->
                    val color = Color(accent.argb)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onAccent(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (settings.accent == accent) {
                            Icon(
                                AppIcons.Check,
                                contentDescription = accent.label,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            SectionLabel("Fondo")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Negro puro", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "En el tema oscuro, usa un fondo totalmente negro.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.amoled, onCheckedChange = onAmoled)
            }

            SectionLabel("Acerca de")
            Text("Música $versionName", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            Spacer(Modifier.size(48.dp))
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
