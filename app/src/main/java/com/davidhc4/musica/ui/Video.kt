package com.davidhc4.musica.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/**
 * Hueco donde debe verse el video. No dibuja el video: solo avisa dónde está, y [VideoOverlay] lo
 * coloca encima. Así hay una sola vista de video que se mueve entre el panel y el mini reproductor
 * sin reiniciarse.
 */
@Composable
fun VideoSlot(modifier: Modifier, onBounds: (Rect?) -> Unit) {
    DisposableEffect(Unit) { onDispose { onBounds(null) } }
    Box(modifier.onGloballyPositioned { onBounds(Rect(it.positionInRoot(), it.size.toSize())) })
}

/**
 * No se recorta con esquinas redondeadas a propósito: en algunos celulares eso deja el video en negro.
 */
/** Coloca la vista del video sobre el hueco indicado. Solo responde a toques si [interactive]. */
@Composable
fun VideoOverlay(view: View, bounds: Rect, interactive: Boolean) {
    val density = LocalDensity.current
    AndroidView(
        factory = {
            (view.parent as? ViewGroup)?.removeView(view)
            view
        },
        update = { it.setOnTouchListener { _, _ -> !interactive } },
        modifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() }),
    )
}
