package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private fun t(id: String) = Track(id = id, title = id, uri = "u/$id")

private class Fixed(val tracks: List<Track>) : MusicSource {
    override suspend fun search(query: String, limit: Int) = tracks
}

private class Broken : MusicSource {
    override suspend fun search(query: String, limit: Int): List<Track> = throw IOException("caída")
}

class SourcesTest {

    @Test
    fun `mezcla las fuentes una a una`() = runTest {
        val source = CompositeSource(listOf(Fixed(listOf(t("a1"), t("a2"))), Fixed(listOf(t("b1"), t("b2")))))

        assertEquals(listOf("a1", "b1", "a2", "b2"), source.search("x", 10).map { it.id })
    }

    @Test
    fun `si una fuente se cae las otras siguen`() = runTest {
        val source = CompositeSource(listOf(Broken(), Fixed(listOf(t("b1")))))

        assertEquals(listOf("b1"), source.search("x", 10).map { it.id })
    }

    @Test
    fun `si todas se caen la busqueda falla`() = runTest {
        val source = CompositeSource(listOf(Broken(), Broken()))

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { source.search("x", 10) } }
    }

    @Test
    fun `respeta el limite`() = runTest {
        val source = CompositeSource(listOf(Fixed(listOf(t("a"), t("b"), t("c")))))

        assertEquals(2, source.search("x", 2).size)
    }

    private val jamendo = """
        {"headers":{"status":"success"},"results":[
          {"id":"7","name":"Canción","artist_name":"Banda","album_name":"Disco","duration":180,
           "audio":"https://s/7.mp3","image":"https://i/7.jpg"},
          {"id":"8","name":"Sin audio","audio":""}
        ]}
    """.trimIndent()

    @Test
    fun `convierte canciones de Jamendo`() {
        val tracks = parseJamendoTracks(jamendo)

        assertEquals(1, tracks.size)
        assertEquals("jamendo:7", tracks[0].id)
        assertEquals("Banda", tracks[0].artist)
        assertEquals(180_000L, tracks[0].durationMs)
        assertEquals("https://s/7.mp3", tracks[0].uri)
    }

    @Test
    fun `un error de Jamendo se informa como fallo`() {
        val error = """{"headers":{"status":"failed","error_message":"suspendida","code":11},"results":[]}"""

        assertThrows(IOException::class.java) { parseJamendoTracks(error) }
        assertTrue(parseJamendoTracks("""{"headers":{"status":"success"},"results":[]}""").isEmpty())
    }
}
