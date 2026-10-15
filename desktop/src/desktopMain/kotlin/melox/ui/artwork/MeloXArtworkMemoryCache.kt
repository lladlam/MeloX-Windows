package melox.ui.artwork

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import melox.cache.MeloXResourceLruCache

/** One decoded-image budget shared by list/detail artwork and the player backdrop.
 * Budget RGBA at eight bytes/pixel to also accommodate high-precision bitmaps.
 * Each visible UI owns a lease; native pixels close immediately after the last
 * cache/UI owner releases them, without forcing Java garbage collection.
 */
internal object MeloXArtworkMemoryCache {
    // A fresh home can request several covers together. Bound transient native
    // decoder memory as well as the retained cache; waiting jobs remain cancellable.
    val decodePermits = Semaphore(2)
    private val cache = MeloXResourceLruCache<String, ImageBitmap>(128, 64L * 1024 * 1024,
        { it.width.toLong() * it.height.toLong() * 8L }, { it.asSkiaBitmap().close() })

    fun acquire(key: String) = cache.acquire(key)
    fun publishAndAcquire(key: String, image: ImageBitmap) = cache.publishAndAcquire(key, image)
    fun clear() = cache.clear()
    fun stats() = cache.stats()
}

/** Owns the bitmap through the effect lifetime, including cancellation after IO.
 * Publication/lease release occurs on the UI dispatcher, outside drawing.
 */
@Composable
internal fun rememberMeloXArtworkBitmap(key: String?, decode: () -> ImageBitmap?): ImageBitmap? {
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) {
        if (key.isNullOrBlank()) return@LaunchedEffect
        var unpublished: ImageBitmap? = null
        var lease: MeloXResourceLruCache.Lease<ImageBitmap>? = null
        try {
            lease = MeloXArtworkMemoryCache.acquire(key)
            if (lease == null) {
                MeloXArtworkMemoryCache.decodePermits.withPermit {
                    // Another visible owner may have loaded this image while we waited.
                    lease = MeloXArtworkMemoryCache.acquire(key)
                    if (lease == null) {
                        withContext(Dispatchers.IO) { unpublished = decode() }
                        val decoded = unpublished ?: return@LaunchedEffect
                        lease = MeloXArtworkMemoryCache.publishAndAcquire(key, decoded)
                        unpublished = null
                    }
                }
            }
            bitmap = checkNotNull(lease).value
            awaitCancellation()
        } finally {
            lease?.close()
            // withContext propagates cancellation before returning its result.
            // A completed decode must not leak when the page/URL changed during IO.
            unpublished?.asSkiaBitmap()?.close()
        }
    }
    return bitmap
}
