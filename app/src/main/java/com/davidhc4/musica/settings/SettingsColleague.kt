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

/** Guarda las preferencias de la app. Solo escribe cuando algo realmente cambió. */
class SettingsColleague(private val store: SettingsStore) : BaseColleague() {

    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<AppSettings> = _state

    override suspend fun handle(event: AppEvent) {
        val current = _state.value
        val updated = when (event) {
            is AppEvent.SetThemeMode -> current.copy(mode = event.mode)
            is AppEvent.SetLibrarySort -> current.copy(librarySort = event.sort)
            is AppEvent.SetSkipSilence -> current.copy(skipSilence = event.enabled)
            is AppEvent.SetResumeOnConnect -> current.copy(resumeOnConnect = event.enabled)
            is AppEvent.SetSpeed -> current.copy(speed = event.speed.coerceIn(0.5f, 2f))
            is AppEvent.SetDownloadLimit -> current.copy(downloadLimitMb = event.megabytes.coerceAtLeast(0))
            else -> return
        }
        if (updated == current) return
        _state.value = updated
        store.save(updated)
    }
}
