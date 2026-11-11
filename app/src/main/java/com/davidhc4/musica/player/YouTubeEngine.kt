package com.davidhc4.musica.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.source.YOUTUBE_PREFIX
import com.davidhc4.musica.source.isYouTubeVideoId

/**
 * Reproduce videos con el reproductor oficial de YouTube dentro de una vista web. La pantalla debe
 * mostrar [view] mientras suena. Debe usarse siempre desde el hilo principal.
 */
class YouTubeEngine(private val context: Context) : PlaybackEngine {

    override var listener: PlaybackEngine.Listener? = null

    private val main = Handler(Looper.getMainLooper())
    private var created = false
    private var ready = false
    private var videoId: String? = null
    private var wantsToPlay = false

    @Volatile private var position = 0L
    @Volatile private var duration = 0L

    /** La vista del video. Se crea la primera vez que hace falta, para no gastar memoria antes. */
    val view: WebView by lazy { createView() }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createView(): WebView = WebView(context).apply {
        created = true
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        addJavascriptInterface(Bridge(), "Bridge")
        // YouTube exige que la página que incrusta el reproductor se identifique con una dirección.
        loadDataWithBaseURL("https://${context.packageName}", PAGE, "text/html", "utf-8", null)
    }

    private fun run(script: String) {
        if (ready) view.evaluateJavascript(script, null)
    }

    private fun startCurrent() {
        val id = videoId ?: return
        run(if (wantsToPlay) "player.loadVideoById('$id')" else "player.cueVideoById('$id')")
    }

    override fun load(track: Track) {
        position = 0L
        duration = 0L
        val id = track.uri.removePrefix(YOUTUBE_PREFIX)
        if (!isYouTubeVideoId(id)) {
            videoId = null
            main.post { listener?.onError("Este video no se puede reproducir") }
            return
        }
        videoId = id
        wantsToPlay = true
        view // Asegura que la vista exista; si aún no está lista, el video arranca cuando lo esté.
        startCurrent()
    }

    override fun play() {
        wantsToPlay = true
        run("player.playVideo()")
    }

    override fun pause() {
        wantsToPlay = false
        run("player.pauseVideo()")
    }

    override fun stop() {
        wantsToPlay = false
        videoId = null
        position = 0L
        duration = 0L
        if (created) run("player.stopVideo()")
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs
        run("player.seekTo(${positionMs / 1000.0}, true)")
    }

    override fun positionMs(): Long = position

    override fun durationMs(): Long = duration

    override fun release() {
        if (created) view.destroy()
    }

    /** Lo que la página avisa. Llega en otro hilo, así que todo se pasa al principal. */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() = main.post {
            ready = true
            startCurrent()
        }

        @JavascriptInterface
        fun onState(state: Int) = main.post {
            if (videoId == null) return@post
            when (state) {
                1 -> listener?.onIsPlayingChanged(true)
                2 -> listener?.onIsPlayingChanged(false)
                0 -> {
                    listener?.onIsPlayingChanged(false)
                    listener?.onEnded()
                }
            }
        }

        @JavascriptInterface
        fun onTime(currentSeconds: Double, totalSeconds: Double) {
            if (videoId == null) return
            position = (currentSeconds * 1000).toLong()
            duration = (totalSeconds * 1000).toLong()
        }

        @JavascriptInterface
        fun onError(code: Int) = main.post {
            if (videoId == null) return@post
            listener?.onIsPlayingChanged(false)
            listener?.onError(
                if (code == 101 || code == 150) "El dueño de este video no deja reproducirlo fuera de YouTube"
                else "No se pudo reproducir este video (error $code)"
            )
        }
    }

    private companion object {
        const val PAGE = """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#player{width:100%;height:100%}</style>
</head>
<body>
<div id="player"></div>
<script>
var player, ready = false;
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {
    width: '100%', height: '100%',
    playerVars: {autoplay: 1, controls: 0, playsinline: 1, rel: 0, fs: 0, origin: location.origin},
    events: {
      onReady: function() { ready = true; Bridge.onReady(); },
      onStateChange: function(e) { Bridge.onState(e.data); },
      onError: function(e) { Bridge.onError(e.data); }
    }
  });
}
setInterval(function() {
  if (ready && player.getCurrentTime) Bridge.onTime(player.getCurrentTime() || 0, player.getDuration() || 0);
}, 250);
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body>
</html>"""
    }
}
