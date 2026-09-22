package melox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import melox.platform.logDebug
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop artwork loader. Android uses coil3 AsyncImage; the desktop port
 * fetches the URL on IO and caches decoded ImageBitmaps in memory.
 */
private val artworkCache = ConcurrentHashMap<String, ImageBitmap>()

@Composable
fun MeloXArtworkImage(
    url: String?,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var bitmap by remember(url) { mutableStateOf(url?.let(artworkCache::get)) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank() || artworkCache.containsKey(url)) return@LaunchedEffect
        val loaded: ImageBitmap? = runCatching {
            val stream: InputStream = URL(url).openStream()
            stream.use { androidx.compose.ui.res.loadImageBitmap(it) }
        }.getOrNull()
        if (loaded != null) {
            artworkCache[url] = loaded
            bitmap = loaded
        } else {
            logDebug("MeloXArtwork", "artwork load failed: $url")
        }
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap!!),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        // Loading/failed placeholder: source-tinted gradient (stable surface so
        // the title never detaches from its card — Android parity comment).
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    listOf(
                        fallbackColor.copy(alpha = 0.55f),
                        fallbackColor.copy(alpha = 0.25f),
                    )
                )
            ),
        )
    }
}
