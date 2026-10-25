package com.davidhc4.musica.settings

import com.davidhc4.musica.core.Accent
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.AppSettings
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.ThemeMode
import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeSettingsStore(var stored: AppSettings = AppSettings()) : SettingsStore {
    var saves = 0
    override fun load() = stored
    override fun save(settings: AppSettings) {
        stored = settings
        saves++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsColleagueTest {

    @Test
    fun `empieza con lo guardado`() {
        val store = FakeSettingsStore(AppSettings(ThemeMode.LIGHT, Accent.ROSA))
        assertEquals(AppSettings(ThemeMode.LIGHT, Accent.ROSA), SettingsColleague(store).state.value)
    }

    @Test
    fun `por defecto el tema es oscuro`() {
        assertEquals(ThemeMode.DARK, AppSettings().mode)
    }

    @Test
    fun `los cambios se aplican y se guardan`() = runTest {
        val store = FakeSettingsStore()
        val settings = SettingsColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(settings))

        mediator.post(AppEvent.SetThemeMode(ThemeMode.LIGHT))
        mediator.post(AppEvent.SetAccent(Accent.VERDE))
        advanceUntilIdle()

        assertEquals(AppSettings(ThemeMode.LIGHT, Accent.VERDE), settings.state.value)
        assertEquals(settings.state.value, store.stored)
        assertEquals(2, store.saves)
    }

    @Test
    fun `repetir el mismo valor no escribe de nuevo`() = runTest {
        val store = FakeSettingsStore()
        val settings = SettingsColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(settings))

        mediator.post(AppEvent.SetThemeMode(ThemeMode.DARK))
        mediator.post(AppEvent.SetAccent(Accent.VIOLETA))
        advanceUntilIdle()

        assertEquals(0, store.saves)
    }
}
