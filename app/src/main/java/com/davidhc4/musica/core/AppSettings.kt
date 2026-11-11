package com.davidhc4.musica.core

/** SYSTEM sigue el modo claro u oscuro que tenga el celular. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class LibrarySort { TITLE, ARTIST, RECENT }

data class AppSettings(
    val mode: ThemeMode = ThemeMode.DARK,
    val librarySort: LibrarySort = LibrarySort.TITLE,
)
