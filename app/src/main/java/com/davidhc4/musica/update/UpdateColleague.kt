package com.davidhc4.musica.update

import com.davidhc4.musica.core.AppEvent
import com.davidhc4.musica.core.BaseColleague
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Una versión publicada: su número y la dirección del APK. */
data class Release(val version: String, val apkUrl: String)

/** De dónde salen las versiones nuevas. */
interface UpdateService {
    /** La última versión publicada, o null si no hay ninguna con APK. */
    suspend fun latest(): Release?

    /** Descarga el APK y devuelve el archivo listo para instalar. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File
}

enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, FAILED }

data class UpdateState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val latestVersion: String? = null,
    val progress: Float = 0f,
)

/**
 * Busca versiones nuevas, las descarga y abre el instalador de Android, que instala encima de la
 * app actual sin borrar nada. El trabajo de red corre aparte para no frenar al resto de la app.
 */
class UpdateColleague(
    private val scope: CoroutineScope,
    private val service: UpdateService,
    private val currentVersion: String,
    private val install: (File) -> Unit,
) : BaseColleague() {

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    private var job: Job? = null
    private var release: Release? = null
    private var apk: File? = null

    override suspend fun handle(event: AppEvent) {
        when (event) {
            AppEvent.AppStarted -> check(silent = true)
            AppEvent.CheckForUpdates -> check(silent = false)
            AppEvent.InstallUpdate -> downloadAndInstall()
            else -> Unit
        }
    }

    private fun busy() = job?.isActive == true

    private fun check(silent: Boolean) {
        if (busy()) return
        val before = _state.value
        // Al arrancar no se molesta a nadie: si no hay internet, simplemente no pasa nada.
        if (!silent) _state.value = before.copy(status = UpdateStatus.CHECKING)
        job = scope.launch {
            val found = try {
                service.latest()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = if (silent) before else before.copy(status = UpdateStatus.FAILED)
                return@launch
            }
            if (found != null && isNewerVersion(found.version, currentVersion)) {
                if (found != release) apk = null
                release = found
                _state.value = UpdateState(
                    status = if (apk != null) UpdateStatus.READY else UpdateStatus.AVAILABLE,
                    latestVersion = found.version,
                )
            } else {
                release = null
                apk = null
                _state.value = UpdateState(UpdateStatus.UP_TO_DATE)
            }
        }
    }

    private fun downloadAndInstall() {
        if (busy()) return
        val target = release ?: return
        apk?.takeIf { it.exists() }?.let { return install(it) }

        _state.value = UpdateState(UpdateStatus.DOWNLOADING, target.version)
        job = scope.launch {
            val file = try {
                service.download(target) { progress ->
                    _state.value = UpdateState(UpdateStatus.DOWNLOADING, target.version, progress)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = UpdateState(UpdateStatus.FAILED, target.version)
                return@launch
            }
            apk = file
            _state.value = UpdateState(UpdateStatus.READY, target.version, 1f)
            install(file)
        }
    }
}

/** Verdadero si [candidate] es una versión mayor que [current]. Acepta "v1.2.3" y "1.2.3". */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = versionParts(candidate) ?: return false
    val b = versionParts(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun versionParts(version: String): List<Int>? {
    val parts = version.trim().removePrefix("v").split('.').map { it.toIntOrNull() }
    return if (parts.isEmpty() || parts.any { it == null }) null else parts.filterNotNull()
}
