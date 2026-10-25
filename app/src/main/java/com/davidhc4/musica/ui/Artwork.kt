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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saca la carátula incrustada en el archivo de audio y guarda las usadas hace poco. */
class ArtworkLoader(private val context: Context) {

    private val cache = LruCache<String, Bitmap>(40)
    private val withoutArtwork = HashSet<String>()

    suspend fun load(uri: String): ImageBitmap? {
        cache.get(uri)?.let { return it.asImageBitmap() }
        if (uri in withoutArtwork) return null
        val bitmap = withContext(Dispatchers.IO) { decode(uri) }
        if (bitmap == null) withoutArtwork.add(uri) else cache.put(uri, bitmap)
        return bitmap?.asImageBitmap()
    }

    private fun decode(uri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1000) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
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
    val bitmap by produceState<ImageBitmap?>(null, track?.uri) {
        value = track?.let { loader.load(it.uri) }
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
