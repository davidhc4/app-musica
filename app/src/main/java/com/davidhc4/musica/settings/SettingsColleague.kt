package com.davidhc4.musica.settings

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.BaseColleague
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

/** Guarda las preferencias de apariencia. Solo escribe cuando algo realmente cambió. */
class SettingsColleague(private val store: SettingsStore) : BaseColleague() {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppSettings> = _state

    override suspend fun handle(event: AppEvent) {
        val current = _state.value
        val updated = when (event) {
            is AppEvent.SetThemeMode -> current.copy(mode = event.mode)
            is AppEvent.SetAccent -> current.copy(accent = event.accent)
            is AppEvent.SetAmoled -> current.copy(amoled = event.enabled)
            else -> return
        }
        if (updated == current) return
        _state.value = updated
        store.save(updated)
    }
}
