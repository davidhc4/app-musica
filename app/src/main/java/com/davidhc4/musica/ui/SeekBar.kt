package com.davidhc4.musica.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Línea de avance fina y discreta: una barra de 4 dp con un punto pequeño que crece al arrastrar.
 * Se puede tocar en cualquier lugar para saltar, o arrastrar.
 */
@Composable
fun SlimSeekBar(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var held by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableIntStateOf(1) }
    val currentOnSeek by rememberUpdatedState(onSeek)

    // Tras saltar, la barra se queda en el nuevo punto un instante para no rebotar al anterior.
    val seek: (Float) -> Unit = { value ->
        held = value
        currentOnSeek(value)
    }
    LaunchedEffect(held) {
        if (held != null) {
            delay(600)
            held = null
        }
    }

    val fraction = (dragValue ?: held ?: progress).coerceIn(0f, 1f)
    val thumbSize by animateDpAsState(if (dragValue != null) 14.dp else 10.dp, label = "thumb")
    val active = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    seek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragValue?.let { seek(it) }
                        dragValue = null
                    },
                    onDragCancel = { dragValue = null },
                ) { change, _ ->
                    change.consume()
                    dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(track)
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(active)
        )
        if (enabled) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (fraction * widthPx - thumbSize.toPx() / 2f).roundToInt(),
                            y = 0,
                        )
                    }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(active)
            )
        }
    }
}
