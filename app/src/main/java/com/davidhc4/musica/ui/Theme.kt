package com.davidhc4.musica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.ThemeMode

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MusicaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val accent = Color(settings.accent.argb)
    val scheme = if (isDarkTheme(settings)) darkScheme(accent, settings.amoled) else lightScheme(accent)
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun onColor(background: Color) =
    if (background.luminance() > 0.5f) Color.Black else Color.White

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = onColor(accent),
    background = Color(0xFFF8F7FB),
    onBackground = Color(0xFF1A1A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1F),
    surfaceVariant = Color(0xFFEEECF3),
    onSurfaceVariant = Color(0xFF6B6B76),
    outline = Color(0xFFD9D7E0),
    outlineVariant = Color(0xFFE6E4EC),
)

private fun darkScheme(accent: Color, amoled: Boolean): ColorScheme {
    // En fondo oscuro el color de acento se aclara un poco para que se lea bien.
    val primary = lerp(accent, Color.White, 0.2f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        background = if (amoled) Color(0xFF000000) else Color(0xFF0F0F13),
        onBackground = Color(0xFFF2F2F5),
        surface = if (amoled) Color(0xFF0D0D10) else Color(0xFF1A1A20),
        onSurface = Color(0xFFF2F2F5),
        surfaceVariant = if (amoled) Color(0xFF1A1A1F) else Color(0xFF26262E),
        onSurfaceVariant = Color(0xFFA0A0AC),
        outline = Color(0xFF34343C),
        outlineVariant = Color(0xFF2A2A31),
    )
}
