package com.davidhc4.musica.player

import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeEngine(val name: String, val log: MutableList<String>) : PlaybackEngine {
    override var listener: PlaybackEngine.Listener? = null
    override fun load(uri: String) { log += "$name load $uri" }
    override fun play() { log += "$name play" }
    override fun pause() { log += "$name pause" }
    override fun stop() { log += "$name stop" }
    override fun seekTo(positionMs: Long) { log += "$name seek $positionMs" }
    override fun positionMs(): Long = if (name == "video") 2000L else 1000L
    override fun durationMs(): Long = 0L
    override fun release() { log += "$name release" }
}

class RoutingEngineTest {

    private val log = mutableListOf<String>()
    private val audio = FakeEngine("audio", log)
    private val video = FakeEngine("video", log)
    private val engine = RoutingEngine(audio, video) { it.startsWith("youtube:") }

    @Test
    fun `cada cancion va a su motor y el otro se calla`() {
        engine.load("https://x/a.mp3")
        engine.play()
        engine.load("youtube:abc")
        engine.play()
        engine.pause()

        assertEquals(
            listOf("audio load https://x/a.mp3", "audio play", "audio stop", "video load youtube:abc", "video play", "video pause"),
            log,
        )
        assertEquals(2000L, engine.positionMs())
    }

    @Test
    fun `solo llegan avisos del motor en uso`() {
        val heard = mutableListOf<String>()
        engine.listener = object : PlaybackEngine.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { heard += "playing $isPlaying" }
            override fun onEnded() { heard += "ended" }
            override fun onError(message: String) { heard += "error $message" }
        }

        engine.load("youtube:abc")
        audio.listener?.onEnded()
        audio.listener?.onIsPlayingChanged(false)
        video.listener?.onIsPlayingChanged(true)
        video.listener?.onEnded()

        assertEquals(listOf("playing true", "ended"), heard)
    }
}
