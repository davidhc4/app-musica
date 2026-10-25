package com.davidhc4.musica

import android.app.Application
import android.util.Log
import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Mediator
import com.davidhc4.musica.library.FileLibraryStore
import com.davidhc4.musica.library.LibraryColleague
import com.davidhc4.musica.player.Media3Engine
import com.davidhc4.musica.player.PlayerColleague
import com.davidhc4.musica.queue.QueueColleague
import com.davidhc4.musica.settings.PrefsSettingsStore
import com.davidhc4.musica.settings.SettingsColleague
import com.davidhc4.musica.ui.ArtworkLoader
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
    lateinit var settings: SettingsColleague
        private set
    lateinit var artwork: ArtworkLoader
        private set
    lateinit var mediator: Mediator
        private set

    override fun onCreate() {
        super.onCreate()
        queue = QueueColleague()
        player = PlayerColleague(Media3Engine(this))
        library = LibraryColleague(FileLibraryStore(File(filesDir, "biblioteca.json")))
        settings = SettingsColleague(PrefsSettingsStore(this))
        artwork = ArtworkLoader(this)
        mediator = EventMediator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            colleagues = listOf(queue, player, library, settings),
            onError = { t, event -> Log.e("Musica", "Falló al atender $event", t) },
        )
        mediator.post(AppEvent.AppStarted)
    }
}
