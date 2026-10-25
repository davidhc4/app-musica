package com.davidhc4.musica.settings

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
        val store = FakeSettingsStore(AppSettings(ThemeMode.LIGHT))
        assertEquals(AppSettings(ThemeMode.LIGHT), SettingsColleague(store).state.value)
    }

    @Test
    fun `por defecto el tema es oscuro`() {
        assertEquals(ThemeMode.DARK, AppSettings().mode)
    }

    @Test
    fun `cambiar de tema se aplica y se guarda`() = runTest {
        val store = FakeSettingsStore()
        val settings = SettingsColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(settings))

        mediator.post(AppEvent.SetThemeMode(ThemeMode.LIGHT))
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, settings.state.value.mode)
        assertEquals(settings.state.value, store.stored)
        assertEquals(1, store.saves)
    }

    @Test
    fun `repetir el mismo tema no escribe de nuevo`() = runTest {
        val store = FakeSettingsStore()
        val settings = SettingsColleague(store)
        val mediator = EventMediator(mediatorScope(), listOf(settings))

        mediator.post(AppEvent.SetThemeMode(ThemeMode.DARK))
        advanceUntilIdle()

        assertEquals(0, store.saves)
    }
}
