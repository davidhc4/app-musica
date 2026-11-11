package com.davidhc4.musica.player

/**
 * Reparte cada canción al motor que sabe reproducirla (audio normal o video) y calla al otro,
 * para que nunca suenen dos cosas a la vez ni lleguen avisos de un motor que ya no está en uso.
 */
class RoutingEngine(
    private val audio: PlaybackEngine,
    private val video: PlaybackEngine,
    private val isVideo: (String) -> Boolean,
) : PlaybackEngine {

    private var active: PlaybackEngine = audio

    override var listener: PlaybackEngine.Listener? = null

    init {
        audio.listener = forwardFrom(audio)
        video.listener = forwardFrom(video)
    }

    private fun forwardFrom(engine: PlaybackEngine) = object : PlaybackEngine.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (engine === active) listener?.onIsPlayingChanged(isPlaying)
        }

        override fun onEnded() {
            if (engine === active) listener?.onEnded()
        }

        override fun onError(message: String) {
            if (engine === active) listener?.onError(message)
        }
    }

    override fun load(uri: String) {
        val next = if (isVideo(uri)) video else audio
        if (next !== active) {
            val previous = active
            active = next
            previous.stop()
        }
        active.load(uri)
    }

    override fun play() = active.play()

    override fun pause() = active.pause()

    override fun stop() {
        audio.stop()
        video.stop()
    }

    override fun seekTo(positionMs: Long) = active.seekTo(positionMs)

    override fun positionMs(): Long = active.positionMs()

    override fun durationMs(): Long = active.durationMs()

    override fun release() {
        audio.release()
        video.release()
    }
}
