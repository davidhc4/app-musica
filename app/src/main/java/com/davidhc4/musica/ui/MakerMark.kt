package com.davidhc4.musica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sello de autor: una D clara sobre un cuadro oscuro redondeado. Es el mismo dibujo en todas las
 * apps del autor, hecho sobre un lienzo de 100x100 que se escala al tamaño pedido.
 */
@Composable
fun MakerMark(size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 100f, pivot = Offset.Zero) {
            val ink = Color(0xFFF2F3F5)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF181A20), Color(0xFF0E0F13)), startY = 0f, endY = 100f),
                size = Size(100f, 100f),
                cornerRadius = CornerRadius(24f),
            )
            drawRoundRect(
                color = Color(0xFF3A3F4B),
                topLeft = Offset(2.25f, 2.25f),
                size = Size(95.5f, 95.5f),
                cornerRadius = CornerRadius(21.75f),
                style = Stroke(width = 1.5f),
            )
            // Tronco de la D.
            drawRoundRect(
                color = ink,
                topLeft = Offset(30f, 27f),
                size = Size(11f, 46f),
                cornerRadius = CornerRadius(2f),
            )
            // Panza de la D.
            drawArc(
                color = ink,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(28f, 32.5f),
                size = Size(38f, 35f),
                style = Stroke(width = 11f),
            )
        }
    }
}
