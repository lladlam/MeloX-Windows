package melox.playback

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Desktop stub for the Media3-based playback cache and analysis materializer.
 *
 * The desktop player uses a simple HTTP client for prefetching instead of
 * Media3's CacheWriter/CacheDataSource. This stub preserves the API surface
 * so callers can still request prefetching.
 */
internal class MeloXMediaPrefetcher(
    private val analysisDirectory: File,
) {
    init {
        analysisDirectory.mkdirs()
    }

    fun cache(mediaItem: Any) {
        // Desktop prefetch is a no-op; the player fetches URLs on demand.
    }

    suspend fun materialize(mediaItem: Any): File {
        val target = analysisFile(mediaItem)
        if (target.isFile && target.length() > 0L) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        // Desktop has no Media3 cache; create an empty placeholder so callers
        // don't crash on the materialize path.
        target.parentFile?.mkdirs()
        target.createNewFile()
        return target
    }

    fun clearAnalysisFiles() {
        analysisDirectory.listFiles()?.forEach(File::delete)
    }

    fun remove(mediaItem: Any) {
        val target = analysisFile(mediaItem)
        target.delete()
    }

    private fun analysisFile(mediaItem: Any): File {
        val identity = mediaItem.toString()
        return File(analysisDirectory, "${identity.hashCode().toUInt().toString(16)}.media")
    }

    private fun trimAnalysisFiles(keep: Int) {
        analysisDirectory.listFiles()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.drop(keep)
            ?.forEach(File::delete)
    }
}