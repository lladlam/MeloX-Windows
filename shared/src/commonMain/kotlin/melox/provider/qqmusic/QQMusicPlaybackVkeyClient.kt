package melox.provider.qqmusic

import melox.music.model.AudioQualityTier
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.model.ProviderTrackMetadata
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * QQ Music playback resolver following the current music.vkey.GetVkey/UrlGetVkey
 * request shape. The candidate list is ordered from the requested tier down to a
 * safe fallback and the returned PlaybackResolution reports the tier that really
 * produced a purl instead of pretending the requested tier succeeded.
 */
class QQMusicPlaybackVkeyClient(
    private val sessionProvider: () -> QQMusicSession,
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    suspend fun resolve(
        track: MusicTrack,
        requestedQuality: AudioQualityTier,
    ): PlaybackResolution = withContext(Dispatchers.IO) {
        require(track.id.source == MusicSource.QQMusic)
        val metadata = (track.providerMetadata as? ProviderTrackMetadata.QQMusic)
            ?: ProviderTrackMetadata.QQMusic(songMid = track.id.value)
        val songMid = metadata.songMid.takeIf(String::isNotBlank) ?: track.id.value
        val session = sessionProvider()
        val candidates = requestedQuality.qqPlaybackCandidates()

        var lastReason: String? = null
        val businessCodes = linkedSetOf<Int>()
        for (candidate in candidates) {
            val result = runCatching {
                requestUrl(
                    songMid = songMid,
                    mediaMid = metadata.mediaMid,
                    candidate = candidate,
                    session = session,
                )
            }
            val url = result.getOrNull()
            if (!url.isNullOrBlank()) {
                return@withContext PlaybackResolution.Playable(
                    url = url,
                    requestedQuality = requestedQuality,
                    actualQuality = candidate.actualTier,
                    bitrate = candidate.bitrate,
                    format = candidate.extension.removePrefix("."),
                )
            }
            when (val failure = result.exceptionOrNull()) {
                is QQVkeyBusinessException -> {
                    businessCodes += failure.businessCode
                    lastReason = failure.message
                }
                null -> Unit
                else -> lastReason = failure.message ?: lastReason
            }
        }

        if (!session.isLoggedIn) {
            PlaybackResolution.LoginRequired
        } else {
            PlaybackResolution.Unavailable(
                reason = businessCodes.qqVkeyFinalReason()
                    ?: lastReason
                    ?: "QQ音乐没有返回可播放的 ${requestedQuality.qqDisplayName()} 音源",
            )
        }
    }

    private fun requestUrl(
        songMid: String,
        mediaMid: String?,
        candidate: QQPlaybackCandidate,
        session: QQMusicSession,
    ): String? {
        val guid = Random.nextLong(1_000_000_000L, 9_999_999_999L).toString()
        val filename = candidate.fileName(songMid, mediaMid)
        val param = JSONObject()
            .put("uin", session.uin.ifBlank { "0" })
            .put("filename", JSONArray().put(filename))
            .put("guid", guid)
            .put("songmid", JSONArray().put(songMid))
            .put("songtype", JSONArray().put(0))
            .put("ctx", 0)
        val payload = JSONObject()
            .put(
                "comm",
                JSONObject()
                    .put("ct", 11)
                    .put("cv", 14_090_008)
                    .put("v", 14_090_008)
                    .put("qq", session.uin.ifBlank { "0" })
                    .put("authst", session.musicKey)
                    .put("tmeAppID", "qqmusic")
                    .put("format", "json"),
            )
            .put(
                "req_0",
                JSONObject()
                    .put("module", "music.vkey.GetVkey")
                    .put("method", "UrlGetVkey")
                    .put("param", param),
            )

        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", "QQMusic 14090008(android 15)")
            .header("Accept", "application/json, text/plain, */*")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("空响应")
            if (!response.isSuccessful) throw IOException("QQ音乐音源请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("QQ音乐音源返回空响应")
            val root = JSONObject(body)
            val req = root.optJSONObject("req_0") ?: throw IOException("QQ音乐音源响应缺少 req_0")
            val code = req.optInt("code", 0)
            if (code != 0) {
                throw IOException(
                    req.optString("message")
                        .ifBlank { req.optString("msg") }
                        .ifBlank { "QQ音乐音源错误码 $code" },
                )
            }
            val data = req.optJSONObject("data") ?: return null
            val midUrl = data.optJSONArray("midurlinfo")?.optJSONObject(0) ?: return null
            val businessCode = midUrl.optInt("result", 0)
            if (businessCode != 0) throw QQVkeyBusinessException(businessCode)
            val purl = midUrl.optString("purl").trim()
            if (purl.isBlank()) return null
            if (purl.startsWith("https://") || purl.startsWith("http://")) return secureUrl(purl)
            val sip = data.optJSONArray("sip") ?: JSONArray()
            val domain = (0 until sip.length())
                .mapNotNull { index -> sip.optString(index).trim().takeIf(String::isNotBlank) }
                .firstOrNull { !it.startsWith("http://ws", ignoreCase = true) }
                ?: (0 until sip.length())
                    .mapNotNull { index -> sip.optString(index).trim().takeIf(String::isNotBlank) }
                    .firstOrNull()
                ?: "https://isure.stream.qqmusic.qq.com/"
            return secureUrl(domain + purl)
        }
    }

    private fun secureUrl(value: String): String =
        if (value.startsWith("http://", ignoreCase = true)) {
            "https://${value.substringAfter("://")}" 
        } else {
            value
        }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}

internal class QQVkeyBusinessException(
    val businessCode: Int,
) : IOException(qqVkeyBusinessReason(businessCode))

internal fun qqVkeyBusinessReason(code: Int): String = when (code) {
    104003 -> "当前 QQ音乐账号没有该音源的播放权限（104003）"
    104004 -> "QQ音乐 VKey 获取失败（104004）"
    104013 -> "QQ音乐当前播放设备受限（104013）"
    else -> "QQ音乐音源授权失败（$code）"
}

internal fun Set<Int>.qqVkeyFinalReason(): String? = when {
    104013 in this -> qqVkeyBusinessReason(104013)
    104003 in this -> qqVkeyBusinessReason(104003)
    104004 in this -> qqVkeyBusinessReason(104004)
    isNotEmpty() -> qqVkeyBusinessReason(first())
    else -> null
}

internal data class QQPlaybackCandidate(
    val prefix: String,
    val extension: String,
    val actualTier: AudioQualityTier,
    val bitrate: Int? = null,
) {
    /**
     * Current QQMusicApi semantics:
     * - with media_mid: prefix + media_mid + extension
     * - without media_mid: prefix + songMid + songMid + extension
     */
    fun fileName(songMid: String, mediaMid: String?): String {
        val media = mediaMid?.takeIf(String::isNotBlank)
        return if (media != null) {
            "$prefix$media$extension"
        } else {
            "$prefix$songMid$songMid$extension"
        }
    }
}

internal fun AudioQualityTier.qqPlaybackCandidates(): List<QQPlaybackCandidate> = when (this) {
    AudioQualityTier.Standard -> listOf(
        QQPlaybackCandidate("M500", ".mp3", AudioQualityTier.Standard, 128_000),
    )
    AudioQualityTier.High -> listOf(
        QQPlaybackCandidate("M800", ".mp3", AudioQualityTier.High, 320_000),
        QQPlaybackCandidate("M500", ".mp3", AudioQualityTier.Standard, 128_000),
    )
    AudioQualityTier.Lossless -> listOf(
        QQPlaybackCandidate("F000", ".flac", AudioQualityTier.Lossless),
        QQPlaybackCandidate("M800", ".mp3", AudioQualityTier.High, 320_000),
        QQPlaybackCandidate("M500", ".mp3", AudioQualityTier.Standard, 128_000),
    )
    AudioQualityTier.HiResolution -> listOf(
        // QQ does not expose a generic "Hi-Res" file code matching MeloX's
        // NetEase tier. Prefer its highest ordinary lossless representation and
        // report the actual tier honestly rather than labelling FLAC as Hi-Res.
        QQPlaybackCandidate("F000", ".flac", AudioQualityTier.Lossless),
        QQPlaybackCandidate("M800", ".mp3", AudioQualityTier.High, 320_000),
        QQPlaybackCandidate("M500", ".mp3", AudioQualityTier.Standard, 128_000),
    )
    AudioQualityTier.Immersive -> listOf(
        QQPlaybackCandidate("Q001", ".flac", AudioQualityTier.Immersive),
        QQPlaybackCandidate("Q000", ".flac", AudioQualityTier.Immersive),
        QQPlaybackCandidate("F000", ".flac", AudioQualityTier.Lossless),
        QQPlaybackCandidate("M800", ".mp3", AudioQualityTier.High, 320_000),
        QQPlaybackCandidate("M500", ".mp3", AudioQualityTier.Standard, 128_000),
    )
    AudioQualityTier.Master -> listOf(
        QQPlaybackCandidate("AI00", ".flac", AudioQualityTier.Master),
        QQPlaybackCandidate("F000", ".flac", AudioQualityTier.Lossless),
        QQPlaybackCandidate("M800", ".mp3", AudioQualityTier.High, 320_000),
        QQPlaybackCandidate("M500", ".mp3", AudioQualityTier.Standard, 128_000),
    )
}

private fun AudioQualityTier.qqDisplayName(): String = when (this) {
    AudioQualityTier.Standard -> "标准"
    AudioQualityTier.High -> "高品质"
    AudioQualityTier.Lossless -> "无损"
    AudioQualityTier.HiResolution -> "Hi-Res"
    AudioQualityTier.Immersive -> "沉浸/臻品"
    AudioQualityTier.Master -> "母带"
}
