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