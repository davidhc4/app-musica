package com.davidhc4.musica.core

/** SYSTEM sigue el modo claro u oscuro que tenga el celular. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    /** Llave de la API de YouTube. Vacía significa que no se busca en YouTube. */
    val youTubeKey: String = "",
)
