package com.davidhc4.musica.core

enum class ThemeMode { LIGHT, DARK }

data class AppSettings(
    val mode: ThemeMode = ThemeMode.DARK,
)
