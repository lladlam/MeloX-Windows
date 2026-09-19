package melox.provider.qqmusic

import melox.music.model.MusicAlbumRef
import melox.music.model.MusicArtistRef
import melox.music.model.MusicPage
import melox.music.model.MusicRankingSummary
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class QQMusicRankingClient(
    private val sessionProvider: () -> QQMusicSession,
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    suspend fun tracks(
        ranking: MusicRankingSummary,
        page: Int = 1,
        pageSize: Int = 100,
    ): MusicPage<MusicTrack> {
        require(ranking.id.source == MusicSource.QQMusic)
        val topId = ranking.id.value.toLongOrNull()
            ?: throw IOException("QQ音乐排行榜 ID 无效：${ranking.id.value}")
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val data = postMusicu(
            module = "music.musicToplist.Toplist",
            method = "GetDetail",
            param = JSONObject()
                .put("topId", topId)
                .put("offset", (safePage - 1) * safeSize)
                .put("num", safeSize)
                .put("withTags", true),
        )
        val list = data.optJSONArray("song")
            ?: data.optJSONArray("songInfoList")
            ?: data.optJSONArray("songlist")
            ?: data.optJSONArray("songs")
            ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until list.length()) {
                val raw = list.optJSONObject(index) ?: continue
                val item = raw.optJSONObject("track_info")
                    ?: raw.optJSONObject("songInfo")
                    ?: raw.optJSONObject("song")
                    ?: raw
                parseTrack(item)?.let(::add)
            }
        }
        val info = data.optJSONObject("data")?.optJSONObject("info")
            ?: data.optJSONObject("info")
            ?: JSONObject()
        val total = firstLong(info, "totalNum", "total_num", "total", "songNum")
            .takeIf { it >= 0 }
            ?: firstLong(data, "totalNum", "total_num", "total").takeIf { it >= 0 }
        return MusicPage(
            items = tracks,
            page = safePage,
            pageSize = safeSize,
            total = total,
        )
    }

    private fun parseTrack(item: JSONObject): MusicTrack? {
        val songMid = firstString(item, "mid", "songmid")
            .ifBlank { item.optJSONObject("mid")?.optString("song").orEmpty() }
        if (songMid.isBlank()) return null
        val title = firstString(item, "name", "title", "songname").ifBlank { "未知歌曲" }
        val singers = item.optJSONArray("singer") ?: item.optJSONArray("singers") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until singers.length()) {
                val singer = singers.optJSONObject(index) ?: continue
                val name = firstString(singer, "name", "title")
                if (name.isBlank()) continue
                val mid = singer.optString("mid").takeIf(String::isNotBlank)
                add(MusicArtistRef(id = mid?.let { MusicResourceId(MusicSource.QQMusic, it) }, name = name))
            }
        }.ifEmpty { listOf(MusicArtistRef(name = "未知歌手")) }
        val album = item.optJSONObject("album")
        val albumMid = album?.optString("mid").orEmpty().ifBlank { item.optString("albummid") }
        val albumName = album?.let { firstString(it, "name", "title") }.orEmpty().ifBlank { item.optString("albumname") }
        val artwork = albumMid.takeIf(String::isNotBlank)?.let {
            "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg?max_age=2592000"
        }
        val mediaMid = item.optJSONObject("file")?.optString("media_mid").orEmpty()
            .ifBlank { item.optString("media_mid") }
            .takeIf(String::isNotBlank)
        return MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, songMid),
            title = title,
            artists = artists,
            album = albumName.takeIf(String::isNotBlank)?.let {
                MusicAlbumRef(
                    id = albumMid.takeIf(String::isNotBlank)?.let { value -> MusicResourceId(MusicSource.QQMusic, value) },
                    name = it,
                    artworkUrl = artwork,
                )
            },
            artworkUrl = artwork,
            durationMs = firstLong(item, "interval", "duration").takeIf { it > 0 }?.times(1_000L),
            providerMetadata = ProviderTrackMetadata.QQMusic(
                songMid = songMid,
                mediaMid = mediaMid,
                numericSongId = firstLong(item, "id", "songid").takeIf { it > 0 },
            ),
        )
    }

    private fun postMusicu(module: String, method: String, param: JSONObject): JSONObject {
        val session = sessionProvider()
        val gtk = hash33(session.musicKey)
        val payload = JSONObject()
            .put(
                "comm",
                JSONObject()
                    .put("ct", 24)
                    .put("cv", 4_747_474)
                    .put("platform", "yqq.json")
                    .put("chid", "0")
                    .put("uin", session.uin.toLongOrNull() ?: 0L)
                    .put("g_tk", gtk)
                    .put("g_tk_new_20200303", gtk)
                    .put("format", "json")
                    .put("inCharset", "utf-8")
                    .put("outCharset", "utf-8")
                    .put("notice", 0)
                    .put("need_new_code", 1),
            )
            .put(
                "req_0",
                JSONObject()
                    .put("module", module)
                    .put("method", method)
                    .put("param", param),
            )
        val request = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", DesktopUserAgent)
            .header("Referer", "https://y.qq.com/")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) throw IOException("QQ音乐排行榜请求失败：HTTP ${response.code}")
            val root = JSONObject(body)
            val req = root.optJSONObject("req_0") ?: throw IOException("QQ音乐排行榜响应缺少 req_0")
            val code = req.optInt("code", 0)
            if (code != 0) throw IOException(req.optString("message").ifBlank { "QQ音乐排行榜错误码 $code" })
            return req.optJSONObject("data") ?: JSONObject()
        }
    }

    private fun firstString(value: JSONObject, vararg keys: String): String =
        keys.asSequence().map(value::optString).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstLong(value: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

    private fun hash33(value: String): Long {
        var hash = 5381L
        value.forEach { char ->
            hash += (hash shl 5) + char.code
            hash = hash and 0xFFFF_FFFFL
        }
        return hash and 0x7FFF_FFFFL
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
        const val DesktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
