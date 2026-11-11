package com.davidhc4.musica.download

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.LibraryStore
import com.davidhc4.musica.mediatorScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

private class MemoryIndex(var stored: List<Track> = emptyList()) : LibraryStore {
    override suspend fun load() = stored
    override suspend fun save(tracks: List<Track>) {
        stored = tracks
    }
}

private fun online(id: String) = Track(id = "audius:$id", title = id, uri = "https://x/$id")

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var fail = false
    private var fetches = 0

    private val fetch: suspend (String, File, (Float) -> Unit) -> Unit = { _, target, onProgress ->
        fetches++
        onProgress(0.5f)
        delay(100)
        if (fail) throw IOException("sin internet")
        target.writeText("audio")
    }

    private fun setup(scope: CoroutineScope, index: MemoryIndex = MemoryIndex()): Pair<DownloadColleague, EventMediator> {
        val downloads = DownloadColleague(scope, index, folder.root, fetch)
        return downloads to EventMediator(scope, listOf(downloads))
    }

    @Test
    fun `descargar guarda el archivo y lo recuerda`() = runTest {
        val index = MemoryIndex()
        val (downloads, mediator) = setup(mediatorScope(), index)

        mediator.post(AppEvent.AppStarted)
        mediator.post(AppEvent.DownloadTrack(online("a")))
        advanceTimeBy(50)
        assertEquals(0.5f, downloads.state.value.inProgress["audius:a"])
        advanceUntilIdle()

        assertEquals(listOf("audius:a"), downloads.state.value.tracks.map { it.id })
        assertTrue(downloads.state.value.inProgress.isEmpty())
        assertEquals(listOf("audius:a"), index.stored.map { it.id })
        val uri = downloads.localUri("audius:a")
        assertNotNull(uri)
        assertTrue(uri!!.startsWith("file:"))
    }

    @Test
    fun `guarda la caratula con la descarga y la borra al quitarla`() = runTest {
        val scope = mediatorScope()
        val downloads = DownloadColleague(scope, MemoryIndex(), folder.root, fetch) { _, target -> target.writeText("foto") }
        val mediator = EventMediator(scope, listOf(downloads))

        mediator.post(AppEvent.DownloadTrack(online("a")))
        advanceUntilIdle()
        assertNotNull(downloads.artworkFile("audius:a"))

        mediator.post(AppEvent.RemoveDownload("audius:a"))
        advanceUntilIdle()
        assertNull(downloads.artworkFile("audius:a"))
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `si la caratula falla la cancion queda descargada igual`() = runTest {
        val scope = mediatorScope()
        val downloads = DownloadColleague(scope, MemoryIndex(), folder.root, fetch) { _, _ -> throw IOException("sin foto") }
        val mediator = EventMediator(scope, listOf(downloads))

        mediator.post(AppEvent.DownloadTrack(online("a")))
        advanceUntilIdle()

        assertEquals(listOf("audius:a"), downloads.state.value.tracks.map { it.id })
        assertNull(downloads.artworkFile("audius:a"))
    }

    @Test
    fun `tocar descargar varias veces baja una sola vez`() = runTest {
        val (_, mediator) = setup(mediatorScope())

        repeat(3) { mediator.post(AppEvent.DownloadTrack(online("a"))) }
        advanceUntilIdle()
        mediator.post(AppEvent.DownloadTrack(online("a")))
        advanceUntilIdle()

        assertEquals(1, fetches)
    }

    @Test
    fun `si la descarga falla no queda nada a medias y avisa`() = runTest {
        fail = true
        val (downloads, mediator) = setup(mediatorScope())

        mediator.post(AppEvent.DownloadTrack(online("a")))
        advanceUntilIdle()

        assertTrue(downloads.state.value.tracks.isEmpty())
        assertTrue(downloads.state.value.inProgress.isEmpty())
        assertNull(downloads.localUri("audius:a"))
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `quitar una descarga borra el archivo`() = runTest {
        val index = MemoryIndex()
        val (downloads, mediator) = setup(mediatorScope(), index)

        mediator.post(AppEvent.DownloadTrack(online("a")))
        advanceUntilIdle()
        mediator.post(AppEvent.RemoveDownload("audius:a"))
        advanceUntilIdle()

        assertTrue(downloads.state.value.tracks.isEmpty())
        assertTrue(index.stored.isEmpty())
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `no descarga archivos que ya estan en el celular`() = runTest {
        val (downloads, mediator) = setup(mediatorScope())

        mediator.post(AppEvent.DownloadTrack(Track(id = "local", title = "b", uri = "content://media/1")))
        advanceUntilIdle()

        assertEquals(0, fetches)
        assertFalse(canDownload(Track(id = "local", title = "b", uri = "content://media/1")))
        assertTrue(downloads.state.value.tracks.isEmpty())
    }

    @Test
    fun `al abrir olvida las descargas cuyo archivo ya no existe`() = runTest {
        val (downloads, mediator) = setup(mediatorScope(), MemoryIndex(listOf(online("perdida"))))

        mediator.post(AppEvent.AppStarted)
        advanceUntilIdle()

        assertTrue(downloads.state.value.tracks.isEmpty())
    }
}
