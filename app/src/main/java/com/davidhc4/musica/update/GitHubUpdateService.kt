package com.davidhc4.musica.update

import com.davidhc4.musica.net.httpDownload
import com.davidhc4.musica.net.httpGetText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Lee las versiones publicadas en la sección Releases del repositorio. */
class GitHubUpdateService(
    private val downloadDir: File,
    private val repo: String = "davidhc4/app-musica",
) : UpdateService {

    override suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        parseLatestRelease(httpGetText("https://api.github.com/repos/$repo/releases/latest"))
    }

    override suspend fun download(release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            // Se borran las descargas viejas para no ir llenando el celular.
            downloadDir.deleteRecursively()
            downloadDir.mkdirs()
            val partial = File(downloadDir, "descarga.tmp")
            httpDownload(release.apkUrl, partial, onProgress = onProgress)
            val apk = File(downloadDir, "Musica-${release.version}.apk")
            if (!partial.renameTo(apk)) throw java.io.IOException("No se pudo guardar la descarga")
            apk
        }
}

/** Saca el número de versión y el APK de la respuesta de GitHub. */
fun parseLatestRelease(json: String): Release? {
    val root = JSONObject(json)
    val version = root.optString("tag_name").removePrefix("v")
    if (version.isBlank()) return null
    val assets = root.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val url = asset.optString("browser_download_url")
        if (asset.optString("name").endsWith(".apk") && url.startsWith("https://")) {
            return Release(version, url)
        }
    }
    return null
}
