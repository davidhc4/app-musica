package com.davidhc4.musica

import android.app.Application
import android.util.Log
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Mediator
import com.davidhc4.musica.library.FileLibraryStore
import com.davidhc4.musica.library.LibraryColleague
import com.davidhc4.musica.library.LikesColleague
import com.davidhc4.musica.player.Media3Engine
import com.davidhc4.musica.player.PlayerColleague
import com.davidhc4.musica.player.RoutingEngine
import com.davidhc4.musica.player.YouTubeEngine
import com.davidhc4.musica.queue.QueueColleague
import com.davidhc4.musica.search.SearchColleague
import com.davidhc4.musica.source.AudiusSource
import com.davidhc4.musica.source.CompositeSource
import com.davidhc4.musica.source.JamendoConfig
import com.davidhc4.musica.source.JamendoSource
import com.davidhc4.musica.source.PreferredSource
import com.davidhc4.musica.source.YouTubeSource
import com.davidhc4.musica.source.isYouTubeUri
import com.davidhc4.musica.settings.PrefsSettingsStore
import com.davidhc4.musica.settings.SettingsColleague
import com.davidhc4.musica.ui.ArtworkLoader
import com.davidhc4.musica.update.GitHubUpdateService
import com.davidhc4.musica.update.UpdateColleague
import com.davidhc4.musica.update.openApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Aquí se arma todo una sola vez: los componentes y el mediador que los conecta. */
class MusicaApp : Application() {

    lateinit var queue: QueueColleague
        private set
    lateinit var player: PlayerColleague
        private set
    lateinit var library: LibraryColleague
        private set
    lateinit var likes: LikesColleague
        private set
    lateinit var settings: SettingsColleague
        private set
    lateinit var search: SearchColleague
        private set
    lateinit var audio: Media3Engine
        private set
    lateinit var youtube: YouTubeEngine
        private set
    lateinit var updates: UpdateColleague
        private set
    lateinit var artwork: ArtworkLoader
        private set
    lateinit var mediator: Mediator
        private set

    override fun onCreate() {
        super.onCreate()
        queue = QueueColleague()
        youtube = YouTubeEngine(this)
        audio = Media3Engine(this)
        player = PlayerColleague(RoutingEngine(audio, youtube, ::isYouTubeUri))
        library = LibraryColleague(FileLibraryStore(File(filesDir, "biblioteca.json")))
        likes = LikesColleague(FileLibraryStore(File(filesDir, "megusta.json")))
        settings = SettingsColleague(PrefsSettingsStore(this))
        artwork = ArtworkLoader(this)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val freeMusic = CompositeSource(
            listOfNotNull(
                AudiusSource(),
                JamendoConfig.CLIENT_ID.takeIf { it.isNotBlank() }?.let { JamendoSource(it) },
            )
        )
        // Con llave de YouTube se busca ahí; sin llave, o si YouTube falla, en la música libre.
        search = SearchColleague(
            appScope,
            PreferredSource(
                primary = YouTubeSource(key = { settings.state.value.youTubeKey }),
                fallback = freeMusic,
                primaryEnabled = { settings.state.value.youTubeKey.isNotBlank() },
            ),
            // Cada búsqueda en YouTube gasta cupo, así que se espera un poco más a que termines de escribir.
            debounceMs = 600L,
        )
        updates = UpdateColleague(
            scope = appScope,
            service = GitHubUpdateService(File(cacheDir, "actualizaciones")),
            currentVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                .getOrNull().orEmpty(),
            install = { apk ->
                runCatching { openApkInstaller(this, apk) }
                    .onFailure { Log.e("Musica", "No se pudo abrir el instalador", it) }
            },
        )
        mediator = EventMediator(
            scope = appScope,
            colleagues = listOf(queue, player, library, likes, settings, search, updates),
            onError = { t, event -> Log.e("Musica", "Falló al atender $event", t) },
        )
        mediator.post(AppEvent.AppStarted)
    }
}
