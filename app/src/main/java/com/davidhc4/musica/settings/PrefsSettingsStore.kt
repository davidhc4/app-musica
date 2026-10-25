package com.davidhc4.musica.settings

import android.content.Context
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.ThemeMode

class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    override fun load(): AppSettings = AppSettings(
        mode = runCatching { ThemeMode.valueOf(prefs.getString("modo", null) ?: "") }
            .getOrDefault(AppSettings().mode),
    )

    override fun save(settings: AppSettings) {
        prefs.edit().putString("modo", settings.mode.name).apply()
    }
}
