package melox.provider.qqmusic

import melox.music.model.MusicAlbumDetail
import melox.music.model.MusicAlbumSummary
import melox.music.model.MusicArtistDetail
import melox.music.model.MusicArtistRef
import melox.music.model.MusicArtistSummary
import melox.music.model.MusicPage
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.MusicAlbumRef
import melox.music.model.ProviderTrackMetadata
import java.io.IOException
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** QQ Music non-song catalog APIs mapped into provider-neutral MeloX models. */
class QQMusicCatalogClient(
    private val sessionProvider: () -> QQMusicSession,
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> {
        val result = searchByType(query, 3, page, pageSize)
        val list = extractSearchItems(result, "item_songlist")
        return MusicPage(
            items = list.mapNotNull(::parsePlaylist),
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 50),
            total = searchTotal(result),
        )
    }

    suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> {
        val result = searchByType(query, 2, page, pageSize)
        val list = extractSearchItems(result, "item_album")
        return MusicPage(
            items = list.mapNotNull(::parseAlbum),
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 50),
            total = searchTotal(result),
        )
    }

    suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> {
        val result = searchByType(query, 1, page, pageSize)
        val list = extractSearchItems(result, "singer")
        return MusicPage(
            items = list.mapNotNull(::parseArtist),
            page = page.coerceAtLeast(1),
            pageSize = pageSize.coerceIn(1, 50),
            total = searchTotal(result),
        )
    }

    suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail {
        require(album.id.source == MusicSource.QQMusic)
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val albumParam = JSONObject().apply {
            val numeric = album.id.value.toLongOrNull()
            if (numeric != null) put("albumId", numeric) else put("albumMId", album.id.value)
        }
        val detailData = runCatching {
            postMusicu(
                module = "music.musichallAlbum.AlbumInfoServer",
                method = "GetAlbumDetail",
                param = albumParam,
            )
        }.getOrNull()
        val songParam = JSONObject()
            .put("begin", (safePage - 1) * safeSize)
            .put("num", safeSize)
            .apply {
                val numeric = album.id.value.toLongOrNull()
                if (numeric != null) put("albumId", numeric) else put("albumMid", album.id.value)
            }
        val songData = postMusicu(
            module = "music.musichallAlbum.AlbumSongList",
            method = "GetAlbumSongList",
            param = songParam,
        )
        val summary = detailData?.optJSONObject("basicInfo")?.let(::parseAlbum) ?: album
        val rawSongs = songData.optJSONArray("songList") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until rawSongs.length()) {
                val raw = rawSongs.optJSONObject(index) ?: continue
                val item = raw.optJSONObject("songInfo") ?: raw
                parseTrack(item)?.let(::add)
            }
        }
        return MusicAlbumDetail(
            summary = summary,
            tracks = tracks,
            totalTracks = firstLong(songData, "totalNum", "total_num", "total").takeIf { it >= 0 },
        )
    }

    suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail {
        require(artist.id.source == MusicSource.QQMusic)
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 200)
        val detailData = runCatching {
            postMusicu(
                module = "music.musichallSinger.SingerInfoInter",
                method = "GetSingerDetail",
                param = JSONObject()
                    .put("singer_mids", JSONArray().put(artist.id.value))
                    .put("group_singer", true)
                    .put("wiki_singer", true)
                    .put("ex_singer", true)
                    .put("pic", true)
                    .put("photos", true),
            )
        }.getOrNull()
        val songData = postMusicu(
            module = "musichall.song_list_server",
            method = "GetSingerSongList",
            param = JSONObject()
                .put("singerMid", artist.id.value)
                .put("order", 1)
                .put("number", safeSize)
                .put("begin", (safePage - 1) * safeSize),
        )
        val summary = detailData
            ?.let(::flattenObjects)
            ?.mapNotNull(::parseArtist)
            ?.firstOrNull { it.id.value == artist.id.value }
            ?: artist
        val rawSongs = songData.optJSONArray("songList") ?: JSONArray()
        val tracks = buildList {
            for (index in 0 until rawSongs.length()) {
                val raw = rawSongs.optJSONObject(index) ?: continue
                val item = raw.optJSONObject("songInfo") ?: raw.optJSONObject("trackInfo") ?: raw
                parseTrack(item)?.let(::add)
            }
        }
        return MusicArtistDetail(
            summary = summary,
            tracks = tracks,
            totalTracks = firstLong(songData, "totalNum", "total_num", "total").takeIf { it >= 0 },
        )
    }

    private fun searchByType(query: String, type: Int, page: Int, pageSize: Int): JSONObject {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        return postMusicu(
            module = "music.search.SearchCgiService",
            method = "DoSearchForQQMusicMobile",
            param = JSONObject()
                .put("searchid", "${System.currentTimeMillis()}${Random.nextInt(1000, 9999)}")
                .put("query", query.trim())
                .put("search_type", type)
                .put("num_per_page", safeSize)
                .put("page_num", safePage)
                .put("highlight", false)
                .put("grp", true)
                .put("selectors", JSONObject())
                .put("vec_selectors", JSONArray()),
        )
    }

    private fun extractSearchItems(data: JSONObject, key: String): List<JSONObject> {
        val body = data.optJSONObject("body") ?: data
        val direct = body.optJSONArray(key)
        if (direct != null) return direct.objects()
        val nested = body.optJSONObject(key)
        if (nested != null) {
            val list = nested.optJSONArray("list") ?: nested.optJSONArray("item") ?: nested.optJSONArray("items")
            if (list != null) return list.objects()
        }
        return flattenObjects(body)
            .filter { objectValue ->
                when (key) {
                    "item_album" -> hasAny(objectValue, "albumMid", "albumMID", "albumId", "albummid") &&
                        !hasAny(objectValue, "songmid", "songMid")
                    "singer" -> hasAny(objectValue, "singerMid", "singerMID", "mid") &&
                        hasAny(objectValue, "singerName", "name") &&
                        !hasAny(objectValue, "songmid", "albumMid")
                    else -> hasAny(objectValue, "dissid", "dissId", "playlistId", "tid")
                }
            }
            .distinctBy { firstString(it, "mid", "singerMid", "albumMid", "dissid", "playlistId", "tid", "id") }
    }

    private fun searchTotal(data: JSONObject): Long? =
        firstLong(data.optJSONObject("meta") ?: JSONObject(), "sum", "total", "total_num")
            .takeIf { it >= 0 }
            ?: firstLong(data, "sum", "total", "total_num").takeIf { it >= 0 }

    private fun parsePlaylist(item: JSONObject): MusicPlaylistSummary? {
        val id = firstString(item, "dissid", "dissId", "playlistId", "tid", "id")
            .ifBlank { firstLong(item, "dissid", "dissId", "playlistId", "tid", "id").takeIf { it > 0 }?.toString().orEmpty() }
        if (id.isBlank()) return null
        return MusicPlaylistSummary(
            id = MusicResourceId(MusicSource.QQMusic, id),
            title = firstString(item, "dissname", "title", "name").ifBlank { "QQ音乐歌单" },
            artworkUrl = firstString(item, "imgurl", "imgUrl", "picurl", "logo", "cover").takeIf(String::isNotBlank)?.let(::secureUrl),
            creatorName = firstString(item, "creatorName", "nickname", "nick", "creator").takeIf(String::isNotBlank),
            description = firstString(item, "desc", "description").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "song_count", "songnum", "songNum").takeIf { it >= 0 }?.toInt(),
            playCount = firstLong(item, "listen_num", "listennum", "playCnt").takeIf { it >= 0 },
        )
    }

    private fun parseAlbum(item: JSONObject): MusicAlbumSummary? {
        val mid = firstString(item, "albumMid", "albumMID", "albummid", "mid")
        val numeric = firstLong(item, "albumId", "albumID", "id").takeIf { it > 0 }?.toString()
        val id = mid.ifBlank { numeric.orEmpty() }
        if (id.isBlank()) return null
        val singers = item.optJSONArray("singer") ?: item.optJSONArray("singers") ?: JSONArray()
        val artists = singers.objects().mapNotNull { singer ->
            val name = firstString(singer, "name", "singerName", "title")
            if (name.isBlank()) null else MusicArtistRef(
                id = firstString(singer, "mid", "singerMid").takeIf(String::isNotBlank)
                    ?.let { MusicResourceId(MusicSource.QQMusic, it) },
                name = name,
            )
        }
        return MusicAlbumSummary(
            id = MusicResourceId(MusicSource.QQMusic, id),
            title = firstString(item, "albumName", "name", "title").ifBlank { "QQ音乐专辑" },
            artworkUrl = mid.takeIf(String::isNotBlank)?.let(::albumArtwork)
                ?: firstString(item, "picurl", "picUrl", "cover").takeIf(String::isNotBlank)?.let(::secureUrl),
            artists = artists,
            releaseDate = firstString(item, "publishDate", "time_public", "release_time").takeIf(String::isNotBlank),
            trackCount = firstLong(item, "song_count", "songnum", "songNum", "totalNum").takeIf { it >= 0 },
        )
    }

    private fun parseArtist(item: JSONObject): MusicArtistSummary? {
        val mid = firstString(item, "singerMid", "singerMID", "mid")
        if (mid.isBlank()) return null
        val name = firstString(item, "singerName", "name", "title")
        if (name.isBlank()) return null
        return MusicArtistSummary(
            id = MusicResourceId(MusicSource.QQMusic, mid),
            name = name,
            artworkUrl = firstString(item, "pic", "picUrl", "avatar", "headPic").takeIf(String::isNotBlank)?.let(::secureUrl)
                ?: "https://y.qq.com/music/photo_new/T001R300x300M000${mid}.jpg?max_age=2592000",
            description = firstString(item, "desc", "description", "wiki").takeIf(String::isNotBlank),
            songCount = firstLong(item, "songNum", "song_count", "songnum", "totalNum").takeIf { it >= 0 },
            albumCount = firstLong(item, "albumNum", "album_count", "albumnum").takeIf { it >= 0 },
        )
    }

    private fun parseTrack(item: JSONObject): MusicTrack? {
        val songMid = firstString(item, "mid", "songmid", "songMid")
            .ifBlank { item.optJSONObject("mid")?.optString("song").orEmpty() }
        if (songMid.isBlank()) return null
        val title = firstString(item, "name", "title", "songname").ifBlank { "未知歌曲" }
        val singers = item.optJSONArray("singer") ?: item.optJSONArray("singers") ?: JSONArray()
        val artists = singers.objects().mapNotNull { singer ->
            val name = firstString(singer, "name", "title", "singerName")
            if (name.isBlank()) null else MusicArtistRef(
                id = firstString(singer, "mid", "singerMid").takeIf(String::isNotBlank)
                    ?.let { MusicResourceId(MusicSource.QQMusic, it) },
                name = name,
            )
        }.ifEmpty { listOf(MusicArtistRef(name = "未知歌手")) }
        val album = item.optJSONObject("album")
        val albumMid = album?.let { firstString(it, "mid", "albumMid") }.orEmpty().ifBlank { firstString(item, "albummid", "albumMid") }
        val albumName = album?.let { firstString(it, "name", "title") }.orEmpty().ifBlank { firstString(item, "albumname", "albumName") }
        val artwork = albumMid.takeIf(String::isNotBlank)?.let(::albumArtwork)
        val mediaMid = item.optJSONObject("file")?.optString("media_mid").orEmpty()
            .ifBlank { firstString(item, "media_mid", "mediaMid") }
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
                numericSongId = firstLong(item, "id", "songid", "songId").takeIf { it > 0 },
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
            val body = response.body?.string() ?: throw IOException("空响应")
            if (!response.isSuccessful) throw IOException("QQ音乐目录请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("QQ音乐目录返回空响应")
            val root = JSONObject(body)
            val req = root.optJSONObject("req_0") ?: throw IOException("QQ音乐目录响应缺少 req_0")
            val code = req.optInt("code", 0)
            if (code != 0) throw IOException(req.optString("message").ifBlank { "QQ音乐目录错误码 $code" })
            return req.optJSONObject("data") ?: JSONObject()
        }
    }

    private fun flattenObjects(root: Any?): List<JSONObject> = buildList {
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    add(value)
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) optJSONObject(index)?.let(::add)
    }

    private fun hasAny(value: JSONObject, vararg keys: String): Boolean =
        keys.any { key -> value.has(key) && value.optString(key).isNotBlank() }

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

    private fun albumArtwork(mid: String): String =
        "https://y.qq.com/music/photo_new/T002R300x300M000${mid}.jpg?max_age=2592000"

    private fun secureUrl(value: String): String =
        if (value.startsWith("http://", true)) "https://${value.substringAfter("://")}" else value

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
