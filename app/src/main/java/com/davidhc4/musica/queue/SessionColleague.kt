package com.davidhc4.musica.queue

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.library.playable
import com.davidhc4.musica.library.tracksFromJson
import com.davidhc4.musica.library.tracksToJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Lo que se estaba oyendo: la cola, cuál canción y por dónde iba. */
data class Session(
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
)

interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
}

/**
 * Recuerda lo que se estaba oyendo para retomarlo al volver a abrir la app. Guarda cuando cambia
 * la cola, al pausar y al salir de la app; al arrancar pide a la cola que lo restaure en pausa.
 */
class SessionColleague(
    private val store: SessionStore,
    private val position: () -> Long,
) : BaseColleague() {

    private var tracks: List<Track> = emptyList()
    private var index = -1
    private var shuffle = false
    private var repeat = RepeatMode.OFF
    private var loaded = false

    // Posición que trae la sesión restaurada; vale hasta que cambie la canción.
    private var restoredPositionMs: Long? = null

    override suspend fun handle(event: AppEvent) {
        when (event) {
            is AppEvent.AppStarted -> {
                val saved = store.load()?.let { session ->
                    // Si había canciones que ya no se pueden reproducir, se empieza por la primera que quede.
                    val kept = session.tracks.playable()
                    if (kept.size == session.tracks.size) session else session.copy(tracks = kept, index = 0, positionMs = 0L)
                }
                loaded = true
                if (saved != null && saved.tracks.isNotEmpty() && tracks.isEmpty()) {
                    restoredPositionMs = saved.positionMs
                    shuffle = saved.shuffle
                    repeat = saved.repeat
                    mediator.post(
                        AppEvent.RestoreQueue(saved.tracks, saved.index, saved.positionMs, saved.shuffle, saved.repeat)
                    )
                }
            }

            is AppEvent.QueueChanged -> {
                val sameTrack = event.tracks.getOrNull(event.currentIndex)?.id == tracks.getOrNull(index)?.id
                val restoring = restoredPositionMs != null && tracks.isEmpty()
                tracks = event.tracks
                index = event.currentIndex
                shuffle = event.shuffle
                repeat = event.repeat
                if (!loaded) return
                when {
                    restoring -> save(restoredPositionMs ?: 0L)
                    sameTrack -> save(position())
                    else -> {
                        restoredPositionMs = null
                        save(0L)
                    }
                }
            }

            is AppEvent.PlaybackStateChanged -> if (loaded && !event.isPlaying && tracks.isNotEmpty()) save(position())

            is AppEvent.SaveSession -> if (loaded && tracks.isNotEmpty()) save(position())

            else -> Unit
        }
    }

    private suspend fun save(positionMs: Long) {
        store.save(Session(tracks, index, positionMs.coerceAtLeast(0L), shuffle, repeat))
    }
}

class FileSessionStore(private val file: File) : SessionStore {

    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else runCatching { sessionFromJson(file.readText()) }.getOrNull()
    }

    override suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(sessionToJson(session))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }
}

fun sessionToJson(session: Session): String = JSONObject().apply {
    put("index", session.index)
    put("positionMs", session.positionMs)
    put("shuffle", session.shuffle)
    put("repeat", session.repeat.name)
    put("tracks", tracksToJson(session.tracks))
}.toString()

fun sessionFromJson(text: String): Session {
    val o = JSONObject(text)
    return Session(
        tracks = tracksFromJson(o.getJSONArray("tracks")),
        index = o.optInt("index", 0),
        positionMs = o.optLong("positionMs", 0L),
        shuffle = o.optBoolean("shuffle", false),
        repeat = runCatching { RepeatMode.valueOf(o.optString("repeat")) }.getOrDefault(RepeatMode.OFF),
    )
}
