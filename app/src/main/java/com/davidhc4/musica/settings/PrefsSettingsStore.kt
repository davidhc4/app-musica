package com.davidhc4.musica.settings

import android.content.Context
import com.davidhc4.musica.core.Accent
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.ThemeMode

class PrefsSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    override fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            mode = runCatching { ThemeMode.valueOf(prefs.getString("modo", null) ?: "") }
                .getOrDefault(defaults.mode),
            accent = runCatching { Accent.valueOf(prefs.getString("acento", null) ?: "") }
                .getOrDefault(defaults.accent),
        )
    }

    override fun save(settings: AppSettings) {
        prefs.edit()
            .putString("modo", settings.mode.name)
            .putString("acento", settings.accent.name)
            .apply()
    }
}
