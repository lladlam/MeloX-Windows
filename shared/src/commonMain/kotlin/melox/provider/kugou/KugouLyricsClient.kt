package melox.provider.kugou

import melox.lyrics.KugouKrcLyricsParser
import melox.lyrics.LrcLyricsParser
import melox.lyrics.LyricsDocument
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import java.io.IOException
import java.util.Base64
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kugou lyric transport kept separate from playback/search so lyric endpoint
 * compatibility changes cannot disturb the rest of the provider.
 *
 * Preferred path mirrors the current Android KuGouMusicApi request shape:
 * /v1/search -> signed Android /download -> encrypted KRC.
 *
 * lyrics.kugou.com occasionally returns HTTP 200 with an empty entity to the
 * Android-flavoured download request. In that case retry the same candidate via
 * the long-standing PC request shape. KRC is still preferred; plain LRC is only
 * the final compatibility fallback.
 */
internal class KugouLyricsClient(
    sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    private val requests = KugouRequestClient(sessionProvider, httpClient)

    suspend fun lyrics(track: MusicTrack): LyricsDocument {
        require(track.id.source == MusicSource.Kugou) {
            "KugouLyricsClient cannot handle ${track.id.source.storageValue} track"
        }
        val metadata = (track.providerMetadata as? ProviderTrackMetadata.Kugou)
            ?: ProviderTrackMetadata.Kugou(hash = track.id.value)
        val candidate = searchCandidate(track, metadata)
            ?: return LyricsDocument(emptyList())

        // Keep the current Android KRC path first because it preserves real
        // per-word timing. A blank/transport failure is not treated as "no lyric".
        download(
            candidate = candidate,
            client = "android",
            format = "krc",
            includeDefaults = true,
            sign = true,
        )?.let { return it }

        // Compatibility path used by the lyrics service for many years. It is
        // deliberately unsigned and does not inject gateway-only device params.
        download(
            candidate = candidate,
            client = "pc",
            format = "krc",
            includeDefaults = false,
            sign = false,
        )?.let { return it }

        // Last fallback keeps lyrics usable even when the service refuses KRC.
        return download(
            candidate = candidate,
            client = "pc",
            format = "lrc",
            includeDefaults = false,
            sign = false,
        ) ?: LyricsDocument(emptyList())
    }

    private fun searchCandidate(
        track: MusicTrack,
        metadata: ProviderTrackMetadata.Kugou,
    ): LyricCandidate? {
        val keyword = "${track.artistText} - ${track.title}"
        val durationSeconds = ((track.durationMs ?: 0L) / 1_000L).coerceAtLeast(0L)

        val modern = runCatching {
            requests.get(
                baseUrl = LyricsBaseUrl,
                path = "/v1/search",
                params = mapOf(
                    "album_audio_id" to (metadata.albumAudioId ?: 0L).toString(),
                    "appid" to KugouRequestClient.AppId.toString(),
                    "clientver" to KugouRequestClient.ClientVersion.toString(),
                    "duration" to durationSeconds.toString(),
                    "hash" to metadata.hash,
                    "keyword" to keyword,
                    "lrctxt" to "1",
                    "man" to "no",
                ),
                includeDefaults = false,
                sign = false,
            )
        }.getOrNull()

        parseCandidate(modern)?.let { return it }

        // Legacy search is intentionally only a fallback. Some edge/CDN routes
        // still answer this shape when /v1/search returns a blank entity.
        val legacy = runCatching {
            requests.get(
                baseUrl = LyricsBaseUrl,
                path = "/search",
                params = mapOf(
                    "ver" to "1",
                    "man" to "yes",
                    "client" to "pc",
                    "keyword" to keyword,
                    "duration" to durationSeconds.toString(),
                    "hash" to metadata.hash,
                ),
                includeDefaults = false,
                sign = false,
            )
        }.getOrNull()
        return parseCandidate(legacy)
    }

    private fun parseCandidate(response: JSONObject?): LyricCandidate? {
        response ?: return null
        val candidates = response.optJSONArray("candidates")
            ?: response.optJSONObject("data")?.optJSONArray("candidates")
            ?: JSONArray()
        val value = (0 until candidates.length())
            .mapNotNull(candidates::optJSONObject)
            .maxByOrNull { it.optInt("score", 0) }
            ?: return null
        val id = value.optString("id")
            .ifBlank { value.optLong("id", 0L).toString() }
        val accessKey = value.optString("accesskey")
            .ifBlank { value.optString("access_key") }
        if (id.isBlank() || id == "0" || accessKey.isBlank()) return null
        return LyricCandidate(id, accessKey)
    }

    private fun download(
        candidate: LyricCandidate,
        client: String,
        format: String,
        includeDefaults: Boolean,
        sign: Boolean,
    ): LyricsDocument? {
        val response = runCatching {
            requests.get(
                baseUrl = LyricsBaseUrl,
                path = "/download",
                params = mapOf(
                    "ver" to "1",
                    "client" to client,
                    "id" to candidate.id,
                    "accesskey" to candidate.accessKey,
                    "fmt" to format,
                    "charset" to "utf8",
                ),
                includeDefaults = includeDefaults,
                sign = sign,
            )
        }.getOrNull() ?: return null

        val data = response.optJSONObject("data")
        val content = response.optString("content")
            .ifBlank { data?.optString("content").orEmpty() }
        if (content.isBlank()) return null
        val contentType = response.optInt(
            "contenttype",
            data?.optInt("contenttype", if (format == "krc") 0 else 1)
                ?: if (format == "krc") 0 else 1,
        )

        return runCatching {
            if (format == "krc" && contentType == 0) {
                KugouKrcLyricsParser.decodeAndParse(content)
            } else {
                val decoded = String(Base64.getDecoder().decode(content), Charsets.UTF_8)
                LrcLyricsParser.parse(decoded)
            }
        }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
    }

    private data class LyricCandidate(
        val id: String,
        val accessKey: String,
    )

    private companion object {
        const val LyricsBaseUrl = "https://lyrics.kugou.com"
    }
}
