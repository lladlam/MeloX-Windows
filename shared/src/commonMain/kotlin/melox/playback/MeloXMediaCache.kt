package melox.playback

import melox.platform.meloXCacheDir
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Process-wide bounded playback cache used by the desktop player for replay and seeking. */
object MeloXMediaCache {
    @Volatile
    private var cacheDir: File? = null

    fun get(): File = cacheDir ?: synchronized(this) {
        cacheDir ?: File(meloXCacheDir(), "melox_media").apply { mkdirs() }.also { cacheDir = it }
    }

    fun clear() {
        val active = get()
        active.listFiles()?.forEach { it.deleteRecursively() }
    }

    private const val MEDIA_CACHE_BYTES = 512L * 1024L * 1024L
}