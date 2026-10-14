package melox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import melox.platform.logDebug
import melox.ui.artwork.rememberMeloXArtworkBitmap

/**
 * Desktop artwork loader. Android uses coil3 AsyncImage; the desktop port
 * fetches the URL on IO and caches decoded ImageBitmaps in memory.
 */
@Composable
fun MeloXArtworkImage(
    url: String?,
    fallbackColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val revision by melox.provider.jellyfin.JellyfinSessionStore.revision.collectAsState()
    val session = remember(revision) { melox.provider.jellyfin.JellyfinSessionStore.read() }
    val cacheKey = remember(url, session) { url?.takeIf(String::isNotBlank)?.let { melox.provider.jellyfin.JellyfinArtworkAccess.cacheKey(it, session) } }
    val bitmap = rememberMeloXArtworkBitmap(cacheKey) {
        runCatching {
            val connection = melox.provider.jellyfin.JellyfinArtworkAccess.openConnection(checkNotNull(url), session).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.getInputStream().use { androidx.compose.ui.res.loadImageBitmap(it) }
        }.getOrNull().also {
            if (it == null) logDebug("MeloXArtwork", "artwork load failed")
        }
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
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
