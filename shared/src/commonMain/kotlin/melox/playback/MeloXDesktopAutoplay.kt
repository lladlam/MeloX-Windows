package melox.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.model.SearchSong
import melox.music.model.MusicSource
import melox.network.NeteaseSearchClient
import melox.platform.elapsedRealtime
import melox.platform.logWarn

/** Pinned service's real NetEase recommendation bridge; appended tracks retain their NetEase identity. */
internal open class DesktopAutoplaySource {
    open suspend fun recommendations(song: SearchSong): List<SearchSong> {
        val cookie = { NeteaseSessionStore.readCookie() }
        val providerId = song.providerTrack?.id
        val neteaseId = when {
            providerId == null -> song.id.takeIf { it > 0L }
            providerId.source == MusicSource.Netease -> providerId.value.toLongOrNull()
            else -> null
        } ?: coroutineScope {
            val search = NeteaseSearchClient(cookieProvider = cookie)
            val candidates = listOf(song.name, "${song.name} ${song.artists}".trim()).distinct().map { query ->
                async {
                    try { search.searchSongs(query, limit = 20) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { emptyList() }
                }
            }.awaitAll().flatten().distinctBy(SearchSong::id)
            selectNeteaseAutoplaySeed(song.name, song.artists, song.durationMs, candidates)
        } ?: error("未找到可用于无尽播放的网易云匹配歌曲")
        return withContext(Dispatchers.IO) { NeteaseLibraryClient(cookieProvider = cookie).similarSongsBlocking(neteaseId, 30) }
    }
}

/** Cancellable per-controller recommendation lifecycle, with the source 15s retry budget. */
internal class DesktopAutoplayCoordinator(
    private val scope: CoroutineScope,
    private val source: DesktopAutoplaySource,
    private val enabled: () -> Boolean,
    private val current: () -> QueueEntry?,
    private val append: (List<SearchSong>) -> Unit,
    private val resumeEnded: () -> Unit,
    private val nowMs: () -> Long = ::elapsedRealtime,
) {
    private var selectedEntryId: String? = null
    private var requestedEntryId: String? = null
    private var retryAfterMs: Long = 0L
    private var job: Job? = null
    private var generation = 0

    fun selectionChanged() {
        val id = current()?.entryId
        if (selectedEntryId == id) return
        cancel()
        selectedEntryId = id
    }

    fun ensure() {
        if (!enabled()) return
        resumeEnded()
        val entry = current() ?: return
        if (job?.isActive == true || requestedEntryId == entry.entryId || nowMs() < retryAfterMs) return
        val token = generation
        requestedEntryId = entry.entryId
        val nextJob = scope.launch(start = CoroutineStart.LAZY) {
            var committed = false
            try {
                val recommendations = source.recommendations(entry.song)
                if (generation != token || !enabled() || current()?.entryId != entry.entryId) return@launch
                if (recommendations.isNotEmpty()) {
                    append(recommendations)
                    committed = true
                    retryAfterMs = 0L
                    resumeEnded()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation == token) logWarn("MeloXPlayback", "Autoplay recommendations failed: ${error.message}")
            } finally {
                if (generation == token) {
                    if (!committed) {
                        requestedEntryId = null
                        retryAfterMs = nowMs() + RetryMs
                    }
                    job = null
                }
            }
        }
        job = nextJob
        nextJob.start()
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        requestedEntryId = null
        retryAfterMs = 0L
    }

    companion object {
        const val PreloadMs = 60_000L
        const val RetryMs = 15_000L
    }
}

// Exact metadata gates from pinned MeloXPlaybackService.selectNeteaseAutoplaySeed.
internal fun selectNeteaseAutoplaySeed(title: String, artist: String, durationMs: Long, candidates: List<SearchSong>): Long? {
    val titleKey = normalizeAutoplayMetadata(title)
    val artistKeys = autoplayArtistKeys(artist)
    return candidates.asSequence()
        .filter { normalizeAutoplayMetadata(it.name) == titleKey }
        .filter { candidate -> artistKeys.isEmpty() || autoplayArtistKeys(candidate.artists).any(artistKeys::contains) }
        .filter { candidate -> durationMs <= 0L || candidate.durationMs > 0L && kotlin.math.abs(durationMs - candidate.durationMs) <= 3_000L }
        .maxByOrNull { candidate ->
            (if (autoplayArtistKeys(candidate.artists) == artistKeys) 100 else 50) -
                (if (durationMs > 0L) kotlin.math.abs(durationMs - candidate.durationMs) / 1_000L else 0L).toInt()
        }?.id
}

private fun normalizeAutoplayMetadata(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
private fun autoplayArtistKeys(value: String): Set<String> = value.split(Regex("\\s*(?:/|、|,|，|&|;|；)\\s*"))
    .map(::normalizeAutoplayMetadata).filter(String::isNotBlank).toSet()
