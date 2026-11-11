package com.davidhc4.musica.core

enum class ThemeMode { LIGHT, DARK }

data class AppSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    /** Llave de la API de YouTube. Vacía significa que no se busca en YouTube. */
    val youTubeKey: String = "",
)
