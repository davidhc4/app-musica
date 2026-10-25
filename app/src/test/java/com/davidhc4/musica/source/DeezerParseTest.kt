package com.davidhc4.musica.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DeezerParseTest {

    private val sample = """
        {"data":[
          {"id":1,"readable":true,"title":"Monaco","duration":267,"rank":900000,
           "preview":"https://p/1.mp3","artist":{"name":"Bad Bunny"},
           "album":{"title":"Nadie Sabe Lo Que Va a Pasar Mañana","cover_medium":"https://c/m1.jpg","cover_big":"https://c/b1.jpg"}},
          {"id":2,"readable":true,"title":"Monaco (Remix)","duration":200,"rank":100,
           "preview":"https://p/2.mp3","artist":{"name":"DJ Random"},"album":{"title":"Remixes"}},
          {"id":3,"readable":true,"title":"Tití Me Preguntó","duration":243,"rank":950000,
           "preview":"https://p/3.mp3","artist":{"name":"Bad Bunny"},"album":{"title":"Un Verano Sin Ti"}},
          {"id":4,"readable":true,"title":"Tití Me Preguntó","duration":243,"rank":500000,
           "preview":"https://p/4.mp3","artist":{"name":"Bad Bunny"},"album":{"title":"Un Verano Sin Ti (Deluxe)"}},
          {"id":5,"readable":true,"title":"Bad Bunny Hits","duration":100,"rank":800000,
           "preview":"https://p/5.mp3","artist":{"name":"Karaoke Kings"},"album":{"title":"Karaoke Version"}},
          {"id":6,"readable":true,"title":"Sin vista previa","duration":100,"rank":999999,
           "preview":"","artist":{"name":"Bad Bunny"},"album":{"title":"X"}}
        ]}
    """.trimIndent()

    @Test
    fun `solo deja lo que coincide y ordena por reproducciones`() {
        val ids = parseDeezerTracks(sample, "bad bunny", 10).map { it.id }

        // Primero la más escuchada; sin el remix de otro artista, sin karaoke, sin la repetida
        // y sin la que no tiene fragmento para escuchar.
        assertEquals(listOf("deezer:3", "deezer:1"), ids)
    }

    @Test
    fun `buscar una cancion trae tambien sus versiones ordenadas por popularidad`() {
        val ids = parseDeezerTracks(sample, "monaco", 10).map { it.id }

        assertEquals(listOf("deezer:1", "deezer:2"), ids)
    }

    @Test
    fun `convierte los datos y marca el fragmento`() {
        val track = parseDeezerTracks(sample, "monaco", 10).first()

        assertEquals("Monaco", track.title)
        assertEquals("Bad Bunny", track.artist)
        assertEquals("Nadie Sabe Lo Que Va a Pasar Mañana", track.album)
        assertEquals("https://p/1.mp3", track.uri)
        assertEquals("https://c/b1.jpg", track.artworkUrl)
        assertEquals(30_000L, track.durationMs)
        assertTrue(track.isPreview)
    }

    @Test
    fun `respeta el limite de resultados`() {
        assertEquals(1, parseDeezerTracks(sample, "bad bunny", 1).size)
    }

    @Test
    fun `si se busca karaoke no se filtra`() {
        val ids = parseDeezerTracks(sample, "karaoke", 10).map { it.id }

        assertEquals(listOf("deezer:5"), ids)
    }

    @Test
    fun `un error de la api se informa como fallo`() {
        val error = """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""

        assertThrows(IOException::class.java) { parseDeezerTracks(error, "bad bunny", 10) }
    }

    @Test
    fun `una respuesta sin datos devuelve lista vacia`() {
        assertTrue(parseDeezerTracks("{}", "bad bunny", 10).isEmpty())
    }
}
