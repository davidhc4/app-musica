package com.davidhc4.musica.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Descarga una dirección completa en memoria. Lanza IOException si algo falla. */
fun httpGetBytes(url: String, timeoutMs: Int = 10_000): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Musica/0.3")
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}

fun httpGetText(url: String, timeoutMs: Int = 10_000): String =
    String(httpGetBytes(url, timeoutMs), Charsets.UTF_8)

/** Descarga una dirección a un archivo, avisando el avance entre 0 y 1 cuando se conoce el tamaño. */
fun httpDownload(url: String, target: java.io.File, timeoutMs: Int = 15_000, onProgress: (Float) -> Unit = {}) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Musica/0.3")
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        val total = connection.contentLength.toLong()
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
                if (total > 0 && done != total) throw IOException("Descarga incompleta")
            }
        }
    } finally {
        connection.disconnect()
    }
}
