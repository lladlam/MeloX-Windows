package melox.provider.local

import melox.model.SearchSong
import melox.network.NeteaseSearchClient
import melox.lyrics.AmlldbLyricsClient
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

data class LocalRecognitionOutcome(
    val local: LocalTrackRecord,
    val matched: SearchSong?,
)

/** Pinned Android three-segment acoustic recognition; metadata is enrichment only. */
class LocalRecognitionCoordinator(
    private val repository: LocalMusicRepository = LocalMusicRepository(),
) {
    suspend fun recognize(fileKey: String): LocalRecognitionOutcome {
        val local = repository.track(fileKey) ?: error("本地歌曲记录不存在")
        val recognized = melox.recognition.SongRecognitionClient().use { recognition ->
            val lastStart = (local.durationMs - 9_000L).coerceAtLeast(0L)
            var lastFailure: Exception? = null
            var matched: SearchSong? = null
            for (start in listOf(lastStart / 2, 0L, lastStart).distinct()) {
                try {
                    matched = recognition.recognizeLocal(local.contentUri, local.durationMs, start).firstOrNull()?.song
                    if (matched != null) break
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { lastFailure = failure }
            }
            if (matched == null && lastFailure != null) throw lastFailure
            matched
        }
        val search = NeteaseSearchClient()
        val matched = recognized?.let { song ->
            val candidates = search.searchSongs("${song.name} ${song.artists}", limit = 20)
            candidates.filter { candidate ->
                candidate.name.equals(song.name, true) &&
                    candidate.artists.split(" / ").any { it.equals(song.artists, true) } &&
                    (local.durationMs <= 0 || candidate.durationMs <= 0 || abs(candidate.durationMs - local.durationMs) <= 8_000)
            }.minByOrNull { if (local.durationMs > 0 && it.durationMs > 0) abs(it.durationMs - local.durationMs) else Long.MAX_VALUE }
                ?: candidates.firstOrNull()
        }?.let { search.ensureArtwork(it) }
        if (matched != null) {
            repository.updateRecognition(fileKey, matched)
            try { AmlldbLyricsClient().lyrics(matched.id)?.let { repository.updateLyrics(fileKey, it) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Fingerprint match remains valid when optional lyrics are unavailable. */ }
        }
        return LocalRecognitionOutcome(repository.track(fileKey) ?: local, matched)
    }
}
