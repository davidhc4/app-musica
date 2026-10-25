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
