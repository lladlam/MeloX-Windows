package melox.network

import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.music.model.MusicPage
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.ProviderTrackMetadata
import melox.music.model.TrackAvailability
import melox.network.MeloXHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import okhttp3.OkHttpClient
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.forEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MeloXSearchKind(val apiType: Int, val title: String) {
    Songs(1, "歌曲"), Albums(10, "专辑"), Artists(100, "歌手"), Playlists(1000, "歌单"), Podcasts(1009, "播客"), Users(1002, "用户")
}

data class MeloXSearchMediaItem(
    val id: Long,
    val kind: MeloXSearchKind,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
)


data class MeloXPodcastHost(val id: Long = 0L, val nickname: String = "网易云主播", val avatarUrl: String? = null)
data class MeloXPodcast(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    val category: String? = null,
    val programCount: Int = 0,
    val subscriberCount: Long = 0L,
    val host: MeloXPodcastHost? = null,
    val subscribed: Boolean = false,
)
data class MeloXPodcastCategory(val id: Long, val name: String, val artworkUrl: String? = null)
data class MeloXPodcastProgram(
    val id: Long,
    val name: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val radioId: Long,
    val radioName: String,
    val playbackSong: melox.model.SearchSong? = null,
)
data class MeloXPodcastPage<T>(val values: List<T>, val hasMore: Boolean = false, val totalCount: Int = values.size)

data class MeloXCloudSong(
    val id: Long,
    val song: melox.model.SearchSong,
    val fileSize: Long = 0L,
    val bitrate: Int = 0,
)
data class MeloXCloudPage(
    val values: List<MeloXCloudSong>,
    val totalCount: Int,
    val usedBytes: Long,
    val maxBytes: Long,
    val hasMore: Boolean,
)

class NeteaseUniversalSearchClient(
    private val cookieProvider: () -> String = { "" },
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): MusicPage<MusicTrack> {
        val normalized = query.trim()
        if (normalized.isBlank()) return MusicPage(emptyList(), page, pageSize, 0)
        return runCatching {
            val response = eapi(
                uri = "/api/v1/search",
                data = JSONObject()
                    .put("s", normalized)
                    .put("offset", (page - 1).coerceAtLeast(0) * pageSize)
                    .put("limit", pageSize),
            )
            val result = response.optJSONObject("result")
                ?: response.optJSONObject("data")
                ?: return@runCatching MusicPage(emptyList(), page, pageSize, 0)
            val songs = result.optJSONArray("songs")
                ?: result.optJSONArray("list")
                ?: JSONArray()
            val tracks = buildList {
                for (index in 0 until songs.length()) {
                    val item = songs.optJSONObject(index) ?: continue
                    parseSearchTrack(item)?.let(::add)
                }
            }
            val total = result.optInt("songCount", result.optInt("total", tracks.size))
            MusicPage(tracks, page, pageSize, total.toLong())
        }.getOrDefault(MusicPage(emptyList(), page, pageSize, 0))
    }

    private fun parseSearchTrack(item: JSONObject): MusicTrack? {
        val id = item.optLong("id").takeIf { it > 0L } ?: return null
        val name = item.getString("name")
        val artists = item.optJSONArray("artists") ?: item.optJSONArray("ar")
        val artistNames = buildList {
            for (index in 0 until (artists?.length() ?: 0)) {
                val artist = artists?.optJSONObject(index) ?: continue
                artist.getString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.ifEmpty { listOf("未知歌手") }
        val album = item.optJSONObject("al") ?: item.optJSONObject("album")
        val albumName = album?.getString("name")
        val albumId = album?.optLong("id")?.takeIf { it > 0L }?.toString()
        val artworkUrl = album?.getString("picUrl")
            ?: album?.optJSONObject("pic")?.getString("url")
        val durationMs = item.optLong("dt").takeIf { it > 0L }
        val status = item.optInt("status", 0)
        return MusicTrack(
            id = MusicResourceId(MusicSource.Netease, id.toString()),
            title = name,
            artists = artistNames.map { MusicArtistRef(name = it) },
            album = albumName?.takeIf(String::isNotBlank)?.let {
                melox.music.model.MusicAlbumRef(
                    id = albumId?.let { value -> MusicResourceId(MusicSource.Netease, value) },
                    name = it,
                    artworkUrl = artworkUrl,
                )
            },
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            availability = if (status == 0) TrackAvailability.Playable else TrackAvailability.Unavailable,
            providerMetadata = ProviderTrackMetadata.Netease(id),
        )
    }


    suspend fun searchMedia(keywords: String, kind: MeloXSearchKind, limit: Int = 30): List<MeloXSearchMediaItem> =
        withContext(Dispatchers.IO) {
            if (kind == MeloXSearchKind.Songs) return@withContext emptyList()
            val query = keywords.trim()
            if (query.isEmpty()) return@withContext emptyList()
            val response = eapi(
                "/api/search/get",
                JSONObject().put("s", query).put("type", kind.apiType).put("limit", limit.coerceIn(1, 50)).put("offset", 0),
            )
            val result = response.optJSONObject("result") ?: return@withContext emptyList()
            val values = when (kind) {
                MeloXSearchKind.Albums -> result.optJSONArray("albums")
                MeloXSearchKind.Artists -> result.optJSONArray("artists")
                MeloXSearchKind.Playlists -> result.optJSONArray("playlists")
                MeloXSearchKind.Podcasts -> result.optJSONArray("djRadios") ?: result.optJSONArray("radios")
                MeloXSearchKind.Users -> result.optJSONArray("userprofiles") ?: result.optJSONArray("userProfiles")
                else -> null
            } ?: JSONArray()
            buildList {
                for (i in 0 until values.length()) {
                    val value = values.optJSONObject(i) ?: continue
                    val id = if (kind == MeloXSearchKind.Users) value.optLong("userId", -1L) else value.optLong("id", -1L)
                    if (id <= 0L) continue
                    when (kind) {
                        MeloXSearchKind.Albums -> add(MeloXSearchMediaItem(
                            id, kind,
                            value.optString("name").ifBlank { "未命名专辑" },
                            value.optJSONObject("artist")?.optString("name").orEmpty(),
                            secureUrl(value.optString("picUrl").takeIf(String::isNotBlank)),
                            value.optInt("size", 0),
                        ))
                        MeloXSearchKind.Artists -> add(MeloXSearchMediaItem(
                            id, kind,
                            value.optString("name").ifBlank { "未知歌手" },
                            buildList {
                                val aliases = value.optJSONArray("alias") ?: JSONArray()
                                for (j in 0 until aliases.length()) aliases.optString(j).takeIf(String::isNotBlank)?.let(::add)
                            }.joinToString(" / "),
                            secureUrl(value.optString("picUrl").takeIf(String::isNotBlank) ?: value.optString("img1v1Url").takeIf(String::isNotBlank)),
                        ))
                        MeloXSearchKind.Playlists -> add(MeloXSearchMediaItem(
                            id, kind,
                            value.optString("name").ifBlank { "未命名歌单" },
                            value.optJSONObject("creator")?.optString("nickname").orEmpty(),
                            secureUrl(value.optString("coverImgUrl").takeIf(String::isNotBlank) ?: value.optString("picUrl").takeIf(String::isNotBlank)),
                            value.optInt("trackCount", 0),
                        ))
                        MeloXSearchKind.Podcasts -> add(MeloXSearchMediaItem(id, kind, value.optString("name").ifBlank { "未命名播客" }, value.optJSONObject("dj")?.optString("nickname").orEmpty(), secureUrl(value.optString("picUrl").takeIf(String::isNotBlank)), value.optInt("programCount", 0)))
                        MeloXSearchKind.Users -> add(MeloXSearchMediaItem(value.optLong("userId", id), kind, value.optString("nickname").ifBlank { "网易云用户" }, value.optString("signature"), secureUrl(value.optString("avatarUrl").takeIf(String::isNotBlank))))
                        else -> Unit
                    }
                }
            }
        }

    suspend fun songDetail(songId: Long): SearchSong? = withContext(Dispatchers.IO) {
        val arr = JSONArray().put(JSONObject().put("id", songId))
        val result = eapi("/api/v3/song/detail", JSONObject().put("c", arr.toString()))
        parseSong(result.optJSONArray("songs")?.optJSONObject(0))
    }

    suspend fun collectionSongs(item: MeloXSearchMediaItem): List<SearchSong> = withContext(Dispatchers.IO) {
        val values = when (item.kind) {
            MeloXSearchKind.Albums -> eapi("/api/v1/album/${item.id}", JSONObject()).optJSONArray("songs")
            MeloXSearchKind.Artists -> eapi("/api/v1/artist/${item.id}", JSONObject()).optJSONArray("hotSongs")
            MeloXSearchKind.Podcasts -> {
                val response = eapi(
                    "/api/dj/program/byradio",
                    JSONObject().put("radioId", item.id).put("limit", 100).put("offset", 0).put("asc", false),
                )
                val programs = response.optJSONArray("programs") ?: JSONArray()
                return@withContext buildList {
                    for (i in 0 until programs.length()) {
                        val program = programs.optJSONObject(i) ?: continue
                        parseSong(program.optJSONObject("mainSong"))?.let(::add)
                    }
                }
            }
            else -> JSONArray()
        } ?: JSONArray()
        buildList {
            for (i in 0 until values.length()) parseSong(values.optJSONObject(i))?.let(::add)
        }
    }

    fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistArray = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (i in 0 until artistArray.length()) artistArray.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" / ")
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            artworkUrl = secureUrl(album?.optString("picUrl")?.takeIf(String::isNotBlank) ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank)),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
        )
    }

    private fun secureUrl(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
            else -> raw
        }
    }


    suspend fun podcastCategories(): List<MeloXPodcastCategory> = withContext(Dispatchers.IO) {
        val values = eapi("/api/djradio/category/get", JSONObject()).optJSONArray("categories") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) {
                val value = values.optJSONObject(index) ?: continue
                val id = value.optLong("id", -1L)
                if (id <= 0L) continue
                add(MeloXPodcastCategory(id, value.optString("name").ifBlank { "播客" }, secureUrl(value.optString("pic96x96Url").takeIf(String::isNotBlank))))
            }
        }
    }

    suspend fun featuredPodcasts(): List<MeloXPodcast> = withContext(Dispatchers.IO) {
        parsePodcasts(eapi("/api/djradio/recommend/v1", JSONObject()).optJSONArray("djRadios"))
    }

    suspend fun personalizedPodcasts(limit: Int = 12): List<MeloXPodcast> = withContext(Dispatchers.IO) {
        parsePodcasts(eapi("/api/djradio/personalize/rcmd", JSONObject().put("limit", limit.coerceIn(1, 50))).optJSONArray("data"))
    }

    suspend fun podcastsByCategory(categoryId: Long, offset: Int = 0, limit: Int = 30): MeloXPodcastPage<MeloXPodcast> = withContext(Dispatchers.IO) {
        val response = eapi("/api/djradio/hot", JSONObject().put("cateId", categoryId).put("limit", limit.coerceIn(1, 50)).put("offset", offset.coerceAtLeast(0)))
        val values = parsePodcasts(response.optJSONArray("djRadios"))
        val total = response.optInt("count", offset + values.size)
        MeloXPodcastPage(values, offset + values.size < total, total)
    }

    suspend fun podcastPrograms(radioId: Long, offset: Int = 0, limit: Int = 30): MeloXPodcastPage<MeloXPodcastProgram> = withContext(Dispatchers.IO) {
        val response = eapi("/api/dj/program/byradio", JSONObject().put("radioId", radioId).put("limit", limit.coerceIn(1, 50)).put("offset", offset.coerceAtLeast(0)).put("asc", false))
        val source = response.optJSONArray("programs") ?: JSONArray()
        val values = buildList { for (index in 0 until source.length()) parsePodcastProgram(source.optJSONObject(index))?.let(::add) }
        val total = response.optInt("count", offset + values.size)
        MeloXPodcastPage(values, offset + values.size < total, total)
    }

    suspend fun setPodcastSubscribed(id: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        eapi(if (subscribed) "/api/djradio/sub" else "/api/djradio/unsub", JSONObject().put("id", id))
        Unit
    }

    private fun parsePodcasts(values: JSONArray?): List<MeloXPodcast> {
        val source = values ?: JSONArray()
        return buildList { for (index in 0 until source.length()) parsePodcast(source.optJSONObject(index))?.let(::add) }
    }

    private fun parsePodcast(value: JSONObject?): MeloXPodcast? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val dj = value.optJSONObject("dj")
        return MeloXPodcast(
            id = id,
            name = value.optString("name").ifBlank { "未知播客" },
            artworkUrl = secureUrl(value.optString("picUrl").takeIf(String::isNotBlank)),
            description = value.optString("desc").takeIf(String::isNotBlank),
            category = value.optString("category").takeIf(String::isNotBlank),
            programCount = value.optInt("programCount", 0),
            subscriberCount = value.optLong("subCount", 0L),
            host = dj?.let { MeloXPodcastHost(it.optLong("userId", 0L), it.optString("nickname").ifBlank { "网易云主播" }, secureUrl(it.optString("avatarUrl").takeIf(String::isNotBlank))) },
            subscribed = value.optBoolean("subed", false),
        )
    }

    private fun parsePodcastProgram(value: JSONObject?): MeloXPodcastProgram? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val radio = value.optJSONObject("radio") ?: JSONObject()
        val song = parseSong(value.optJSONObject("mainSong"))
        return MeloXPodcastProgram(
            id = id,
            name = value.optString("name").ifBlank { "未知节目" },
            artworkUrl = secureUrl(value.optString("coverUrl").takeIf(String::isNotBlank) ?: radio.optString("picUrl").takeIf(String::isNotBlank)),
            durationMs = value.optLong("duration", song?.durationMs ?: 0L),
            radioId = radio.optLong("id", 0L),
            radioName = radio.optString("name").ifBlank { "未知播客" },
            playbackSong = song,
        )
    }

    suspend fun cloudSongs(limit: Int = 200, offset: Int = 0): MeloXCloudPage = withContext(Dispatchers.IO) {
        if (!NeteaseSessionStore.containsMusicU(cookieProvider())) throw java.io.IOException("请先登录网易云音乐")
        val response = eapi("/api/v1/cloud/get", JSONObject().put("limit", limit.coerceIn(1, 200)).put("offset", offset.coerceAtLeast(0)))
        val data = response.optJSONArray("data") ?: JSONArray()
        val values = buildList {
            for (index in 0 until data.length()) {
                val value = data.optJSONObject(index) ?: continue
                val parsed = parseSong(value.optJSONObject("simpleSong")) ?: continue
                add(MeloXCloudSong(value.optLong("songId", parsed.id), parsed, value.optLong("fileSize", 0L), value.optInt("bitrate", 0)))
            }
        }
        val total = response.optInt("count", offset + values.size)
        MeloXCloudPage(values, total, response.optLong("size", 0L), response.optLong("maxSize", 0L), response.optBoolean("hasMore", offset + values.size < total))
    }

    suspend fun deleteCloudSong(songId: Long) = withContext(Dispatchers.IO) {
        eapi("/api/cloud/del", JSONObject().put("songIds", JSONArray().put(songId)))
        Unit
    }

    private fun eapi(
        uri: String,
        data: JSONObject,
    ): JSONObject {
        val timestampMillis = System.currentTimeMillis()
        val cookieHeader = cookieProvider()
        val cookies = NeteaseSessionStore.parseCookie(cookieHeader)
        val useAuthenticatedSession = NeteaseSessionStore.containsMusicU(cookieHeader)

        val header = if (useAuthenticatedSession) {
            authenticatedEapiHeader(cookies, timestampMillis)
        } else {
            JSONObject()
                .put("os", "ios")
                .put("appver", "9.0.90")
                .put("osver", "18.0")
                .put("buildver", (timestampMillis / 1_000L).toString())
                .put("channel", "distribution")
                .put("requestId", "${timestampMillis}_0000")
                .put("__csrf", "")
        }

        val requestData = JSONObject(data.toString())
            .put("header", header)
            .put("e_r", false)
        val json = requestData.toString()
        val digest = md5Hex("nobody${uri}use${json}md5forencrypt")
        val encryptedPayload = "$uri-36cd479b6b5-$json-36cd479b6b5-$digest"
        val params = aesEcbEncrypt(
            encryptedPayload.toByteArray(Charsets.UTF_8),
            "e82ckenh8dichen8".toByteArray(Charsets.UTF_8),
        ).toHexUppercase()

        val path = uri.replace("/api/", "/eapi/")
        val requestBuilder = okhttp3.Request.Builder()
            .url("https://interface.music.163.com$path")
            .header(
                "User-Agent",
                if (useAuthenticatedSession) {
                    "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"
                } else {
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
                },
            )
            .header("Accept", "*/*")

        if (useAuthenticatedSession) {
            requestBuilder.header("Cookie", encodedCookieHeader(header))
        }

        val request = requestBuilder
            .post(okhttp3.FormBody.Builder().add("params", params).build())
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("响应体为空")
            if (!response.isSuccessful) throw IOException("网易云请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("网易云返回了空响应")
            val result = JSONObject(body)
            val code = result.optInt("code", response.code)
            if (code !in 200..299) {
                val message = (result.opt("message") as? String)?.ifBlank { (result.opt("msg") as? String) } ?: "请求失败"
                throw IOException("网易云请求失败（$code）：$message")
            }
            result
        }
    }

    private fun authenticatedEapiHeader(
        cookies: Map<String, String>,
        timestampMillis: Long,
    ): JSONObject {
        val header = JSONObject()
            .put("osver", cookies["osver"] ?: "16.2")
            .put("deviceId", cookies["deviceId"] ?: randomHex(26).uppercase())
            .put("os", cookies["os"] ?: "iPhone OS")
            .put("appver", cookies["appver"] ?: "9.0.90")
            .put("versioncode", cookies["versioncode"] ?: "140")
            .put("mobilename", cookies["mobilename"] ?: "")
            .put("buildver", cookies["buildver"] ?: (timestampMillis / 1_000L).toString())
            .put("resolution", cookies["resolution"] ?: "1170x2532")
            .put("__csrf", cookies["__csrf"] ?: "")
            .put("channel", cookies["channel"] ?: "distribution")
            .put("requestId", "${timestampMillis}_${randomDigits(4)}")
        cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { header.put("MUSIC_U", it) }
        return header
    }

    private fun encodedCookieHeader(values: JSONObject): String {
        val keys = buildList {
            val iterator = values.keys()
            while (iterator.hasNext()) add(iterator.next())
        }.sorted()
        return keys.joinToString("; ") { key ->
            "${encodeURIComponent(key)}=${encodeURIComponent(values.opt(key)?.toString() ?: "")}"
        }
    }

    private fun encodeURIComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    private fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomDigits(length: Int): String = buildString(length) {
        repeat(length) { append(('0'.code + SecureRandom().nextInt(10)).toChar()) }
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun aesEcbEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun ByteArray.toHexUppercase(): String =
        joinToString("") { "%02X".format(it) }
}