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

/**
 * Desktop local recognition coordinator.
 *
 * Android captures mic/WebView audio and fingerprints it against AMLLDB. The
 * desktop port recognizes by title/artist metadata against NetEase search;
 * audio fingerprinting is a follow-up once a JVM extractor is wired in.
 */
class LocalRecognitionCoordinator(
    private val repository: LocalMusicRepository = LocalMusicRepository(),
) {
    suspend fun recognize(fileKey: String): LocalRecognitionOutcome {
        val local = repository.track(fileKey) ?: error("本地歌曲记录不存在")
        val query = if (local.artist.isNotBlank()) "${local.title} ${local.artist}" else local.title
        if (query.isBlank()) return LocalRecognitionOutcome(local, null)
        val matched = runCatching {
            NeteaseSearchClient().searchSongs(query, limit = 5).firstOrNull { song ->
                normalize(song.name) == normalize(local.title) &&
                    (local.artist.isBlank() || normalize(song.artists).contains(normalize(local.artist)))
            }
        }.getOrNull()
        if (matched != null) {
            repository.updateRecognition(local.fileKey, matched)
        }
        return LocalRecognitionOutcome(local, matched)
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[\\s\\p{Punct}·•，。！？、（）()\\[\\]【】]"), "")
}
