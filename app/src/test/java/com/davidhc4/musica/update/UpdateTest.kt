package com.davidhc4.musica.update

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

private class FakeUpdateService(
    var release: Release? = Release("0.6.0", "https://x/Musica-0.6.0.apk"),
    var failCheck: Boolean = false,
    var failDownload: Boolean = false,
) : UpdateService {
    var checks = 0
    var downloads = 0

    override suspend fun latest(): Release? {
        checks++
        delay(50)
        if (failCheck) throw IOException("sin internet")
        return release
    }

    override suspend fun download(release: Release, onProgress: (Float) -> Unit): File {
        downloads++
        delay(50)
        if (failDownload) throw IOException("se cortó")
        onProgress(0.5f)
        return File.createTempFile("Musica-${release.version}", ".apk").apply { deleteOnExit() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateTest {

    private val installed = mutableListOf<File>()

    private fun setup(
        service: UpdateService,
        scope: CoroutineScope,
        current: String = "0.5.0",
    ): Pair<UpdateColleague, EventMediator> {
        val updates = UpdateColleague(scope, service, current) { installed += it }
        return updates to EventMediator(scope, listOf(updates))
    }

    @Test
    fun `compara versiones por numero y no por texto`() {
        assertTrue(isNewerVersion("0.6.0", "0.5.0"))
        assertTrue(isNewerVersion("v0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("1.0", "0.9.9"))
        assertFalse(isNewerVersion("0.5.0", "0.5.0"))
        assertFalse(isNewerVersion("0.4.9", "0.5.0"))
        assertFalse(isNewerVersion("rara", "0.5.0"))
        assertFalse(isNewerVersion("0.6.0", ""))
    }

    @Test
    fun `lee la version y el apk de la respuesta de GitHub`() {
        val json = """
            {"tag_name":"v0.6.0","assets":[
              {"name":"notas.txt","browser_download_url":"https://x/notas.txt"},
              {"name":"Musica-0.6.0.apk","browser_download_url":"https://x/Musica-0.6.0.apk"}
            ]}
        """
        assertEquals(Release("0.6.0", "https://x/Musica-0.6.0.apk"), parseLatestRelease(json))
        assertNull(parseLatestRelease("""{"tag_name":"v0.6.0","assets":[]}"""))
        assertNull(parseLatestRelease("""{"message":"Not Found"}"""))
    }

    @Test
    fun `al abrir la app avisa si hay una version nueva`() = runTest {
        val (updates, mediator) = setup(FakeUpdateService(), mediatorScope())

        mediator.post(AppEvent.AppStarted)
        advanceUntilIdle()

        assertEquals(UpdateState(UpdateStatus.AVAILABLE, "0.6.0"), updates.state.value)
        assertTrue(installed.isEmpty())
    }

    @Test
    fun `si ya se tiene la ultima version lo dice`() = runTest {
        val (updates, mediator) = setup(FakeUpdateService(), mediatorScope(), current = "0.6.0")

        mediator.post(AppEvent.CheckForUpdates)
        advanceUntilIdle()

        assertEquals(UpdateStatus.UP_TO_DATE, updates.state.value.status)
    }

    @Test
    fun `sin internet al abrir no molesta pero al buscar a mano si avisa`() = runTest {
        val (updates, mediator) = setup(FakeUpdateService(failCheck = true), mediatorScope())

        mediator.post(AppEvent.AppStarted)
        advanceUntilIdle()
        assertEquals(UpdateStatus.IDLE, updates.state.value.status)

        mediator.post(AppEvent.CheckForUpdates)
        advanceUntilIdle()
        assertEquals(UpdateStatus.FAILED, updates.state.value.status)
    }

    @Test
    fun `actualizar descarga y abre el instalador`() = runTest {
        val service = FakeUpdateService()
        val (updates, mediator) = setup(service, mediatorScope())

        mediator.post(AppEvent.CheckForUpdates)
        advanceUntilIdle()
        mediator.post(AppEvent.InstallUpdate)
        advanceUntilIdle()

        assertEquals(UpdateState(UpdateStatus.READY, "0.6.0", 1f), updates.state.value)
        assertEquals(1, installed.size)

        // Instalar otra vez reutiliza lo ya descargado.
        mediator.post(AppEvent.InstallUpdate)
        advanceUntilIdle()
        assertEquals(1, service.downloads)
        assertEquals(2, installed.size)
    }

    @Test
    fun `tocar actualizar varias veces descarga una sola vez`() = runTest {
        val service = FakeUpdateService()
        val (_, mediator) = setup(service, mediatorScope())

        mediator.post(AppEvent.CheckForUpdates)
        advanceUntilIdle()
        repeat(3) { mediator.post(AppEvent.InstallUpdate) }
        advanceUntilIdle()

        assertEquals(1, service.downloads)
        assertEquals(1, installed.size)
    }

    @Test
    fun `si la descarga falla se puede reintentar`() = runTest {
        val service = FakeUpdateService(failDownload = true)
        val (updates, mediator) = setup(service, mediatorScope())

        mediator.post(AppEvent.CheckForUpdates)
        advanceUntilIdle()
        mediator.post(AppEvent.InstallUpdate)
        advanceUntilIdle()
        assertEquals(UpdateState(UpdateStatus.FAILED, "0.6.0"), updates.state.value)
        assertTrue(installed.isEmpty())

        service.failDownload = false
        mediator.post(AppEvent.InstallUpdate)
        advanceUntilIdle()
        assertEquals(UpdateStatus.READY, updates.state.value.status)
        assertEquals(1, installed.size)
    }

    @Test
    fun `no instala nada si no hay version nueva`() = runTest {
        val service = FakeUpdateService()
        val (_, mediator) = setup(service, mediatorScope(), current = "0.6.0")

        mediator.post(AppEvent.CheckForUpdates)
        advanceUntilIdle()
        mediator.post(AppEvent.InstallUpdate)
        advanceUntilIdle()

        assertEquals(0, service.downloads)
        assertTrue(installed.isEmpty())
    }
}
