package com.davidhc4.musica.source

import com.davidhc4.musica.core.Track
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val SEARCH_JSON = """
{"items":[
  {"id":{"kind":"youtube#video","videoId":"aaaaaaaaaaa"},
   "snippet":{"title":"Trueno - DANCE CRIP (Video Oficial)","channelTitle":"Trueno",
     "thumbnails":{"default":{"url":"https://i/d.jpg"},"high":{"url":"https://i/h.jpg"}}}},
  {"id":{"kind":"youtube#video","videoId":"bbbbbbbbbbb"},
   "snippet":{"title":"Rock &amp; Roll &#39;90","channelTitle":"Banda - Topic","thumbnails":{}}},
  {"id":{"kind":"youtube#video","videoId":"ccccccccccc"},
   "snippet":{"title":"En vivo ahora","channelTitle":"Canal","thumbnails":{}}},
  {"id":{"kind":"youtube#channel","channelId":"x"},"snippet":{"title":"Un canal"}}
]}
"""

private const val VIDEOS_JSON = """
{"items":[
  {"id":"aaaaaaaaaaa","contentDetails":{"duration":"PT3M5S"}},
  {"id":"bbbbbbbbbbb","contentDetails":{"duration":"PT1H2M"}},
  {"id":"ccccccccccc","contentDetails":{"duration":"P0D"}}
]}
"""

class YouTubeTest {

    @Test
    fun `entiende las duraciones de YouTube`() {
        assertEquals(233_000L, parseIsoDuration("PT3M53S"))
        assertEquals(3_723_000L, parseIsoDuration("PT1H2M3S"))
        assertEquals(45_000L, parseIsoDuration("PT45S"))
        assertEquals(0L, parseIsoDuration("P0D"))
        assertEquals(0L, parseIsoDuration("cualquier cosa"))
    }

    @Test
    fun `separa artista y cancion y quita los anadidos`() {
        assertEquals("DANCE CRIP" to "Trueno", splitVideoTitle("Trueno - DANCE CRIP (Video Oficial)", "TruenoVEVO"))
        assertEquals("Hello" to "Adele", splitVideoTitle("Adele - Hello [Official Music Video]", "Adele"))
        assertEquals("Solo titulo" to "Banda", splitVideoTitle("Solo titulo (Letra)", "Banda - Topic"))
        assertEquals("Tema (Remix)" to "Uno", splitVideoTitle("Uno - Tema (Remix)", "Canal"))
    }

    @Test
    fun `convierte la busqueda en canciones de YouTube`() {
        val tracks = parseYouTubeSearch(SEARCH_JSON)

        assertEquals(listOf("youtube:aaaaaaaaaaa", "youtube:bbbbbbbbbbb", "youtube:ccccccccccc"), tracks.map { it.id })
        assertEquals("DANCE CRIP", tracks[0].title)
        assertEquals("Trueno", tracks[0].artist)
        assertEquals("https://i/h.jpg", tracks[0].artworkUrl)
        assertEquals("youtube:aaaaaaaaaaa", tracks[0].uri)
        assertEquals("Rock & Roll '90", tracks[1].title)
        assertEquals("Banda", tracks[1].artist)
        assertTrue(isYouTubeUri(tracks[0].uri))
        assertFalse(isYouTubeUri("https://x/a.mp3"))
    }

    @Test
    fun `quita directos y mezclas largas y guarda la duracion`() = runTest {
        val calls = mutableListOf<String>()
        val source = YouTubeSource(key = { "LLAVE" }, baseUrl = "https://yt", fetch = { url ->
            calls += url
            if ("/search" in url) SEARCH_JSON else VIDEOS_JSON
        })

        val tracks = source.search("trueno", 25)

        assertEquals(listOf("youtube:aaaaaaaaaaa"), tracks.map { it.id })
        assertEquals(185_000L, tracks[0].durationMs)
        assertTrue(calls[0].contains("q=trueno") && calls[0].contains("key=LLAVE"))
    }

    @Test
    fun `repetir la misma busqueda no gasta cupo`() = runTest {
        var calls = 0
        val source = YouTubeSource(key = { "LLAVE" }, fetch = { url ->
            calls++
            if ("/search" in url) SEARCH_JSON else VIDEOS_JSON
        })

        source.search("trueno", 25)
        source.search("Trueno ", 25)

        assertEquals(2, calls)
    }

    @Test
    fun `si no se puede leer la duracion se muestran igual`() = runTest {
        val source = YouTubeSource(key = { "LLAVE" }, fetch = { url ->
            if ("/search" in url) SEARCH_JSON else throw IOException("HTTP 500")
        })

        assertEquals(3, source.search("trueno", 25).size)
    }

    @Test
    fun `con llave se usa YouTube y sin llave la musica libre`() = runTest {
        var enabled = true
        val yt = object : MusicSource {
            override suspend fun search(query: String, limit: Int) = listOf(Track("youtube:a", "a", uri = "youtube:a"))
        }
        val free = object : MusicSource {
            override suspend fun search(query: String, limit: Int) = listOf(Track("audius:b", "b", uri = "https://b"))
        }
        val source = PreferredSource(yt, free) { enabled }

        assertEquals("youtube:a", source.search("x", 10).single().id)
        enabled = false
        assertEquals("audius:b", source.search("x", 10).single().id)
    }

    @Test
    fun `si YouTube falla o no trae nada responde la musica libre`() = runTest {
        val free = object : MusicSource {
            override suspend fun search(query: String, limit: Int) = listOf(Track("audius:b", "b", uri = "https://b"))
        }
        val broken = object : MusicSource {
            override suspend fun search(query: String, limit: Int): List<Track> = throw IOException("HTTP 403")
        }
        val empty = object : MusicSource {
            override suspend fun search(query: String, limit: Int) = emptyList<Track>()
        }

        assertEquals("audius:b", PreferredSource(broken, free) { true }.search("x", 10).single().id)
        assertEquals("audius:b", PreferredSource(empty, free) { true }.search("x", 10).single().id)
    }
}
