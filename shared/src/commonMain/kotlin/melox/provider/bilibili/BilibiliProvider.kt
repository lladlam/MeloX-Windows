package melox.provider.bilibili

import melox.music.model.*
import melox.music.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Pinned native bvid:cid catalogs and WBI/DASH transport, not a NetEase substitute. */
class BilibiliProvider(
    private val sessionProvider: () -> BilibiliSession = { BilibiliSession("", "") },
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
    private val associationProvider: (String, Long) -> BilibiliPlaybackAssociation? = { _, _ -> null },
    private val apiCache: BilibiliApiCache? = BilibiliApiCache.shared(),
    private val sessionRevisionProvider: () -> Long = { 0L },
) : MusicProvider, SearchCapability, PlaybackCapability, UserLibraryCapability, PlaylistCapability {
    override val source = MusicSource.Bilibili
    override val displayName = source.displayName
    override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback, MusicCapability.Library, MusicCapability.Playlists)

    override suspend fun accountSummary(): MusicAccountSummary? = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        if (!session.isLoggedIn) return@withContext null
        val data = cachedGet("account", "/x/web-interface/nav", emptyMap(), BilibiliApiCache.Account).data()
        MusicAccountSummary(source, data.optLong("mid").takeIf { it > 0 }?.toString() ?: session.userId,
            data.optString("uname").ifBlank { "Bilibili 用户 ${session.userId}" }, secure(data.optString("face")))
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        val session = sessionProvider()
        if (!session.isLoggedIn) return@withContext MusicPage(emptyList(), safePage, safeSize, 0)
        val list = cachedGet("folders", "/x/v3/fav/folder/created/list-all",
            mapOf("up_mid" to session.userId, "type" to "2"), BilibiliApiCache.Folders).data().optJSONArray("list") ?: JSONArray()
        val all = list.objects().mapNotNull { item ->
            val id = item.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
            MusicPlaylistSummary(MusicResourceId(source, id.toString()), item.optString("title"), secure(item.optString("cover")), trackCount = item.optInt("media_count"))
        }
        val start = ((safePage - 1L) * safeSize).coerceAtMost(all.size.toLong()).toInt()
        val selected = all.drop(start).take(safeSize).map { playlist ->
            if (!playlist.artworkUrl.isNullOrBlank()) playlist else {
                val detail = folderData(playlist, 1, 20)
                playlist.copy(artworkUrl = secure(detail.optJSONObject("info")?.optString("cover").orEmpty())
                    ?: secure(detail.optJSONArray("medias")?.optJSONObject(0)?.optString("cover").orEmpty()))
            }
        }
        MusicPage(selected, safePage, safeSize, all.size.toLong())
    }

    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        require(playlist.id.source == source)
        val data = folderData(playlist, page.coerceAtLeast(1), pageSize)
        val medias = data.optJSONArray("medias") ?: JSONArray()
        val tracks = medias.objects().flatMap { media ->
            val bvid = media.optString("bvid")
            if (bvid.isBlank()) emptyList() else videoTracks(bvid, media)
        }
        val info = data.optJSONObject("info")
        val summary = playlist.copy(
            title = info?.optString("title").orEmpty().ifBlank { playlist.title },
            artworkUrl = secure(info?.optString("cover").orEmpty()) ?: secure(medias.optJSONObject(0)?.optString("cover").orEmpty()) ?: playlist.artworkUrl,
            creatorName = info?.optJSONObject("upper")?.optString("name").orEmpty().ifBlank { playlist.creatorName.orEmpty() }.ifBlank { null },
            description = info?.optString("intro").orEmpty().ifBlank { playlist.description.orEmpty() }.ifBlank { null },
            trackCount = info?.optInt("media_count")?.takeIf { it >= 0 } ?: playlist.trackCount,
        )
        MusicPlaylistDetail(summary, tracks, info?.optLong("media_count"))
    }

    private fun folderData(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): JSONObject = cachedGet(
        "folder-detail", "/x/v3/fav/resource/list", mapOf("media_id" to playlist.id.value, "pn" to page.toString(),
            "ps" to pageSize.coerceIn(1, 20).toString(), "order" to "mtime", "tid" to "0", "type" to "0", "platform" to "web"), BilibiliApiCache.FolderDetail).data()

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 50)
        if (query.isBlank()) return@withContext MusicPage(emptyList(), safePage, safeSize, 0)
        val params = mapOf("keyword" to BilibiliApiCache.normalizeSearchQuery(query), "search_type" to "video",
            "page" to safePage.toString(), "page_size" to safeSize.toString())
        val data = cachedWbiGet("search", "/x/web-interface/wbi/search/type", params, BilibiliApiCache.Search).data()
        val tracks = (data.optJSONArray("result") ?: JSONArray()).objects().mapNotNull { item ->
            val bvid = item.optString("bvid").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val cid = item.optLong("cid").takeIf { it > 0 } ?: view(bvid).optLong("cid").takeIf { it > 0 }
            cid?.let { mapTrack(item, bvid, it, item.optLong("aid"), 1) }
        }
        MusicPage(tracks, safePage, safeSize, data.optLong("numResults").takeIf { it > 0 })
    }

    suspend fun searchReplacementCandidates(query: String, pageSize: Int = 20): List<MusicTrack> =
        searchSongs(query, 1, pageSize.coerceIn(1, 50)).items

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution = withContext(Dispatchers.IO) {
        require(track.id.source == source)
        val session = sessionProvider()
        val metadata = track.providerMetadata as? ProviderTrackMetadata.Bilibili
            ?: parseIdentity(track.id.value)?.let { ProviderTrackMetadata.Bilibili(it.first, it.second) }
            ?: return@withContext PlaybackResolution.Unavailable("Bilibili 资源身份缺少 bvid+cid")
        if (!session.isLoggedIn) return@withContext PlaybackResolution.LoginRequired
        val association = associationProvider(metadata.bvid, metadata.cid)
        val bvid = association?.replacementBvid ?: metadata.bvid
        val cid = association?.replacementCid ?: metadata.cid
        val params = mapOf("bvid" to bvid, "cid" to cid.toString(), "qn" to "127", "fnval" to "4048", "fnver" to "0", "fourk" to "1")
        val data = wbiGet("/x/player/wbi/playurl", params).data()
        val candidates = buildList {
            addAudio(data.optJSONObject("dash")?.optJSONArray("audio"))
            addAudio(data.optJSONObject("dash")?.optJSONObject("flac")?.opt("audio"))
            addAudio(data.optJSONObject("dash")?.optJSONObject("dolby")?.optJSONArray("audio"))
        }.distinctBy { it.id to it.url }.filter { it.id in SafeAudioIds }
        val selected = quality.bilibiliAudioPreference().firstNotNullOfOrNull { id -> candidates.firstOrNull { it.id == id } }
            ?: candidates.firstOrNull() ?: return@withContext PlaybackResolution.Unavailable("Bilibili 没有返回 DASH 音频")
        check(session == sessionProvider()) { "Bilibili 账号已切换，请重试" }
        PlaybackResolution.Playable(selected.url,
            requestHeaders = mapOf("Referer" to "https://www.bilibili.com/video/$bvid", "User-Agent" to UserAgent, "Cookie" to session.cookie),
            requestedQuality = quality, actualQuality = selected.id.bilibiliTier(), bitrate = selected.bandwidth,
            format = selected.mime, expiresAtEpochMs = BilibiliApiCache.playbackExpiry(selected.url))
    }

    private fun videoTracks(bvid: String, fallback: JSONObject): List<MusicTrack> {
        val view = view(bvid)
        return (view.optJSONArray("pages") ?: JSONArray()).objects().mapIndexedNotNull { index, page ->
            val cid = page.optLong("cid").takeIf { it > 0 } ?: return@mapIndexedNotNull null
            mapTrack(fallback, bvid, cid, view.optLong("aid"), page.optInt("page", index + 1),
                page.optString("part").takeIf(String::isNotBlank), page.optLong("duration").takeIf { it > 0 }?.times(1_000),
                view.optJSONObject("owner")?.optString("name"), view.optString("pic"))
        }
    }
    private fun view(bvid: String) = cachedGet("view", "/x/web-interface/view", mapOf("bvid" to bvid), BilibiliApiCache.View).data()
    private fun mapTrack(item: JSONObject, bvid: String, cid: Long, aid: Long, page: Int, part: String? = null,
        durationMs: Long? = null, owner: String? = null, pic: String? = null): MusicTrack {
        val baseTitle = cleanTitle(item.optString("title").ifBlank { "Bilibili 视频" })
        return MusicTrack(MusicResourceId(source, "$bvid:$cid"), part?.takeIf { it != baseTitle }?.let { "$baseTitle - $it" } ?: baseTitle,
            listOf(MusicArtistRef(name = owner.orEmpty().ifBlank { item.optString("author").ifBlank { item.optString("upper_name") }.ifBlank { "未知 UP 主" } })),
            artworkUrl = secure(pic.orEmpty().ifBlank { item.optString("pic").ifBlank { item.optString("cover") } }),
            durationMs = durationMs ?: parseDuration(item.optString("duration")), availability = TrackAvailability.Playable,
            providerMetadata = ProviderTrackMetadata.Bilibili(bvid, cid, aid.takeIf { it > 0 }, page))
    }

    private fun cachedGet(operation: String, path: String, params: Map<String, String>, policy: BilibiliApiCache.Policy): JSONObject =
        apiCache?.getOrLoad(BilibiliApiCache.cacheKey(sessionScope(), operation, params), policy) { request(path, params) } ?: request(path, params)
    private fun cachedWbiGet(operation: String, path: String, params: Map<String, String>, policy: BilibiliApiCache.Policy): JSONObject =
        apiCache?.getOrLoad(BilibiliApiCache.cacheKey(sessionScope(), operation, params), policy) { wbiGet(path, params) } ?: wbiGet(path, params)
    private fun wbiGet(path: String, params: Map<String, String>, retry403: Boolean = true): JSONObject {
        val signed = BilibiliWbiSigner.sign(params, currentMixinKey(), System.currentTimeMillis() / 1_000)
        val response = request(path, signed)
        if (retry403 && response.optInt("code") == -403) {
            apiCache?.invalidateMixin(sessionScope())
            return wbiGet(path, params, false)
        }
        return response
    }
    private fun currentMixinKey(): String {
        val loader = {
            // A WBI rejection invalidates this key; do not read a stale cached nav response again.
            request("/x/web-interface/nav", emptyMap()).data().optJSONObject("wbi_img")?.let {
                BilibiliWbiSigner.mixinKey(it.optString("img_url"), it.optString("sub_url"))
            }?.takeIf(String::isNotBlank) ?: throw IOException("无法获取 Bilibili WBI key")
        }
        return apiCache?.mixinKey(sessionScope(), loader) ?: loader()
    }
    private fun sessionScope(): String {
        val session = sessionProvider()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(session.cookie.toByteArray()).joinToString("") { "%02x".format(it) }
        return "${session.userId}:${sessionRevisionProvider()}:$digest"
    }
    private fun request(path: String, params: Map<String, String>): JSONObject {
        val session = sessionProvider()
        val url = "https://api.bilibili.com$path".toHttpUrl().newBuilder().apply { params.toSortedMap().forEach(::addQueryParameter) }.build()
        val request = Request.Builder().url(url).header("User-Agent", UserAgent).header("Referer", "https://www.bilibili.com/")
            .apply { session.cookie.takeIf(String::isNotBlank)?.let { header("Cookie", it) } }.build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bilibili HTTP ${response.code}")
            check(session == sessionProvider()) { "Bilibili 账号已切换，请重试" }
            JSONObject(response.body.string())
        }
    }
    private fun JSONObject.data(): JSONObject {
        if (optInt("code", -1) != 0) throw IOException(optString("message").ifBlank { "Bilibili API ${optInt("code")}" })
        return optJSONObject("data") ?: JSONObject()
    }
    private data class Audio(val id: Int, val url: String, val bandwidth: Int?, val mime: String?)
    private fun MutableList<Audio>.addAudio(value: Any?) {
        when (value) {
            is JSONArray -> value.objects().forEach { addAudio(it) }
            is JSONObject -> {
                val url = value.optString("base_url").ifBlank { value.optString("baseUrl") }.ifBlank {
                    (value.optJSONArray("backup_url") ?: value.optJSONArray("backupUrl"))?.optString(0).orEmpty()
                }
                if (url.isNotBlank()) add(Audio(value.optInt("id"), url, value.optInt("bandwidth").takeIf { it > 0 },
                    value.optString("mime_type").ifBlank { value.optString("mimeType") }))
            }
        }
    }
    companion object {
        const val UserAgent = "MeloX-Desktop/1.0 (Windows; Bilibili transport)"
        private val SafeAudioIds = setOf(30216, 30232, 30280, 30250)
        fun cleanTitle(value: String) = value.replace(Regex("</?em[^>]*>", RegexOption.IGNORE_CASE), "").trim()
        fun parseDuration(value: String): Long? {
            val parts = value.trim().split(':').mapNotNull(String::toLongOrNull)
            return if (parts.size in 2..3) parts.fold(0L) { total, part -> total * 60 + part } * 1_000 else null
        }
        fun parseIdentity(value: String): Pair<String, Long>? {
            val split = value.lastIndexOf(':')
            if (split <= 0) return null
            return value.substring(0, split) to (value.substring(split + 1).toLongOrNull()?.takeIf { it > 0 } ?: return null)
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull(::optJSONObject)
private fun secure(value: String): String? = when {
    value.startsWith("//") -> "https:$value"
    value.startsWith("http://", true) -> "https://${value.substring(7)}"
    else -> value
}.takeIf(String::isNotBlank)
private fun AudioQualityTier.bilibiliAudioPreference() = when (this) {
    AudioQualityTier.Standard -> listOf(30216)
    AudioQualityTier.High -> listOf(30232, 30216)
    AudioQualityTier.Lossless, AudioQualityTier.HiResolution, AudioQualityTier.Master -> listOf(30280, 30232, 30216)
    AudioQualityTier.Immersive -> listOf(30250, 30280, 30232, 30216)
}
private fun Int.bilibiliTier() = when (this) {
    30250 -> AudioQualityTier.Immersive
    30280 -> AudioQualityTier.Lossless
    30232 -> AudioQualityTier.High
    else -> AudioQualityTier.Standard
}
