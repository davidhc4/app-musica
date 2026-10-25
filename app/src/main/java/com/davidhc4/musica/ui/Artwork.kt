package com.davidhc4.musica.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.davidhc4.musica.core.Track
import com.davidhc4.musica.net.httpGetBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Obtiene la carátula (incrustada en el archivo o descargada) y guarda las usadas hace poco. */
class ArtworkLoader(private val context: Context) {

    private val cache = LruCache<String, Bitmap>(40)
    private val withoutArtwork = HashSet<String>()

    suspend fun load(track: Track): ImageBitmap? {
        val key = track.artworkUrl ?: track.uri
        cache.get(key)?.let { return it.asImageBitmap() }
        if (key in withoutArtwork) return null

        val remote = track.artworkUrl != null
        val bitmap = withContext(Dispatchers.IO) {
            try {
                if (remote) decodeBytes(httpGetBytes(track.artworkUrl!!)) else decodeEmbedded(track.uri)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            cache.put(key, bitmap)
        } else if (!remote) {
            // Una descarga fallida puede ser pasajera (sin señal), así que solo se recuerda lo local.
            withoutArtwork.add(key)
        }
        return bitmap?.asImageBitmap()
    }

    private fun decodeEmbedded(uri: String): Bitmap? {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "content" && parsed.scheme != "file") return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, parsed)
            retriever.embeddedPicture?.let { decodeBytes(it) }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1000) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

/**
 * Muestra la carátula de la canción. Si el archivo no trae ninguna, dibuja una portada propia
 * con colores que salen del título, así cada canción siempre se ve igual y distinta de las demás.
 */
@Composable
fun TrackArtwork(
    track: Track?,
    loader: ArtworkLoader,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, track?.id) {
        value = track?.let { loader.load(it) }
    }
    Box(modifier.clip(RoundedCornerShape(cornerRadius))) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(track?.title.orEmpty())
        }
    }
}

@Composable
private fun GeneratedCover(seed: String) {
    val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    val brush = Brush.linearGradient(
        listOf(
            Color.hsv(hue, 0.55f, 0.85f),
            Color.hsv((hue + 40f) % 360f, 0.65f, 0.55f),
        )
    )
    Box(Modifier.fillMaxSize().background(brush), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = AppIcons.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize(0.45f),
        )
    }
}
