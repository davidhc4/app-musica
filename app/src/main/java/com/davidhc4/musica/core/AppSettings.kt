package com.davidhc4.musica.core

enum class ThemeMode { LIGHT, DARK }

enum class Accent(val label: String, val argb: Long) {
    VIOLETA("Violeta", 0xFF7B5CF0),
    AZUL("Azul", 0xFF2F7DF6),
    VERDE("Verde", 0xFF1DB070),
    ROSA("Rosa", 0xFFEC4899),
    NARANJA("Naranja", 0xFFF28A1F),
    ROJO("Rojo", 0xFFE5484D),
}

data class AppSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    val accent: Accent = Accent.VIOLETA,
)
