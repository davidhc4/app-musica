package com.davidhc4.musica.settings

import android.content.Context
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.LibrarySort
import com.davidhc4.musica.core.ThemeMode

class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    override fun load(): AppSettings = AppSettings(
        mode = runCatching { ThemeMode.valueOf(prefs.getString("modo", null) ?: "") }
            .getOrDefault(AppSettings().mode),
        librarySort = runCatching { LibrarySort.valueOf(prefs.getString("orden", null) ?: "") }
            .getOrDefault(AppSettings().librarySort),
    )

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putString("modo", settings.mode.name)
            // La llave de YouTube de versiones anteriores ya no se usa; se borra del celular.
            .remove("llave_youtube")
            .putString("orden", settings.librarySort.name)
            .apply()
    }
}
