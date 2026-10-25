package com.davidhc4.musica

import android.app.Application
import android.util.Log
import com.davidhc4.musica.core.EventMediator
import com.davidhc4.musica.core.Mediator
import com.davidhc4.musica.player.Media3Engine
import com.davidhc4.musica.player.PlayerColleague
import com.davidhc4.musica.queue.QueueColleague
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Aquí se arma todo una sola vez: los componentes y el mediador que los conecta. */
class MusicaApp : Application() {

    lateinit var queue: QueueColleague
        private set
    lateinit var player: PlayerColleague
        private set
    lateinit var mediator: Mediator
        private set

    override fun onCreate() {
        super.onCreate()
        queue = QueueColleague()
        player = PlayerColleague(Media3Engine(this))
        mediator = EventMediator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            colleagues = listOf(queue, player),
            onError = { t, event -> Log.e("Musica", "Falló al atender $event", t) },
        )
    }
}
