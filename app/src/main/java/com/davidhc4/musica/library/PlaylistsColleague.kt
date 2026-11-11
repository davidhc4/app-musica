package com.davidhc4.musica.library

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import com.davidhc4.musica.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(val id: String, val name: String, val tracks: List<Track> = emptyList())

interface PlaylistStore {
    suspend fun load(): List<Playlist>
    suspend fun save(playlists: List<Playlist>)
}

data class PlaylistsState(val playlists: List<Playlist> = emptyList())

/** Lleva las listas de reproducción creadas por el usuario y las conserva entre sesiones. */
class PlaylistsColleague(
    private val store: PlaylistStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : BaseColleague() {

    private val _state = MutableStateFlow(PlaylistsState())
    val state: StateFlow<PlaylistsState> = _state

    override suspend fun handle(event: AppEvent) {
        val current = _state.value.playlists
        val updated = when (event) {
            is AppEvent.AppStarted -> {
                _state.value = PlaylistsState(store.load().map { it.copy(tracks = it.tracks.playable()) })
                return
            }

            is AppEvent.CreatePlaylist -> {
                val name = event.name.trim()
                if (name.isEmpty()) return
                current + Playlist(newId(), name, listOfNotNull(event.firstTrack))
            }

            is AppEvent.DeletePlaylist -> current.filter { it.id != event.playlistId }

            is AppEvent.RenamePlaylist -> {
                val name = event.name.trim()
                if (name.isEmpty()) return
                current.map { if (it.id == event.playlistId) it.copy(name = name) else it }
            }

            is AppEvent.AddToPlaylist -> current.map { list ->
                if (list.id != event.playlistId || list.tracks.any { it.id == event.track.id }) list
                else list.copy(tracks = list.tracks + event.track)
            }

            is AppEvent.RemoveFromPlaylist -> current.map { list ->
                if (list.id != event.playlistId) list
                else list.copy(tracks = list.tracks.filter { it.id != event.trackId })
            }

            else -> return
        }
        if (updated == current) return
        _state.value = PlaylistsState(updated)
        store.save(updated)
    }
}

/** Guarda las listas en un archivo JSON, escribiendo primero a un temporal para no dejarlo a medias. */
class FilePlaylistStore(private val file: File) : PlaylistStore {

    override suspend fun load(): List<Playlist> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList()
        else runCatching { playlistsFromJson(file.readText()) }.getOrDefault(emptyList())
    }

    override suspend fun save(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(playlistsToJson(playlists))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }
}

fun playlistsToJson(playlists: List<Playlist>): String {
    val array = JSONArray()
    playlists.forEach { list ->
        array.put(
            JSONObject().apply {
                put("id", list.id)
                put("name", list.name)
                put("tracks", tracksToJson(list.tracks))
            }
        )
    }
    return array.toString()
}

fun playlistsFromJson(text: String): List<Playlist> {
    val array = JSONArray(text)
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Playlist(o.getString("id"), o.getString("name"), tracksFromJson(o.getJSONArray("tracks")))
    }
}
