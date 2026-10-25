package com.davidhc4.musica.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiusParseTest {

    private val base = "https://api.audius.co"

    private val sample = """
        {"data":[
          {"id":"abc12","title":"Noche de Verano","duration":215,
           "user":{"name":"Luna Beats","handle":"luna"},
           "artwork":{"150x150":"https://img/150.jpg","480x480":"https://img/480.jpg"},
           "is_streamable":true},
          {"id":"zzz99","title":"Solo para suscriptores","duration":100,
           "user":{"name":"X"},"access":{"stream":false,"download":false}},
          {"id":"off01","title":"No disponible","duration":90,"is_streamable":false},
          {"id":"min01","duration":0},
          {"title":"Sin id"}
        ]}
    """.trimIndent()

    @Test
    fun `convierte una cancion con todos sus datos`() {
        val track = parseAudiusTracks(sample, base, "Musica").first()

        assertEquals("audius:abc12", track.id)
        assertEquals("Noche de Verano", track.title)
        assertEquals("Luna Beats", track.artist)
        assertEquals(215_000L, track.durationMs)
        assertEquals("https://img/480.jpg", track.artworkUrl)
        assertEquals("$base/v1/tracks/abc12/stream?app_name=Musica", track.uri)
    }

    @Test
    fun `ignora las que no se pueden reproducir o no tienen id`() {
        val ids = parseAudiusTracks(sample, base, "Musica").map { it.id }

        assertEquals(listOf("audius:abc12", "audius:min01"), ids)
    }

    @Test
    fun `una cancion con pocos datos recibe valores por defecto`() {
        val track = parseAudiusTracks(sample, base, "Musica").last()

        assertEquals("Sin título", track.title)
        assertNull(track.artist)
        assertNull(track.artworkUrl)
    }

    @Test
    fun `una respuesta sin datos devuelve lista vacia`() {
        assertTrue(parseAudiusTracks("{}", base, "Musica").isEmpty())
    }
}
