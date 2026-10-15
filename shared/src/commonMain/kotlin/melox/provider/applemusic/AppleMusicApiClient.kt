package melox.provider.applemusic

import melox.lyrics.*
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

/** Pinned official REST catalogs. Preview URLs never become full-song playback. */
class AppleMusicApiClient(
    private val sessionProvider: () -> AppleMusicSession = { AppleMusicSessionStore.read() },
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, CatalogSearchCapability, AlbumCapability, ArtistCapability,
    LyricsCapability, PlaybackCapability, HomeFeedCapability, UserLibraryCapability, PlaylistCapability {
    override val source = MusicSource.AppleMusic
    override val displayName = source.displayName
    // Full playback requires an official desktop MusicKit integration, not REST previews.
    override val capabilities = setOf(MusicCapability.Search, MusicCapability.Lyrics, MusicCapability.Library,
        MusicCapability.Playlists, MusicCapability.Albums, MusicCapability.Artists, MusicCapability.HomeRecommendations)

    suspend fun validateSession(): AppleMusicSession = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        require(session.isConfigured) { "Apple Music 未配置 Developer Token" }
        getJson("v1/catalog/${storefront()}/charts", mapOf("types" to "songs", "limit" to "1"))
        val verified = if (session.hasUserAuthorization) {
            val id = getJson("v1/me/storefront", userRequired = true).array().firstOrNull()?.optString("id")
                ?.takeIf { it.matches(Regex("[a-zA-Z]{2}")) } ?: throw IOException("Apple Music 没有返回已授权 storefront")
            session.copy(storefront = id.lowercase())
        } else session.copy(storefront = session.storefront.lowercase())
        check(session == sessionProvider()) { "Apple Music 凭据已变化，请重试" }
        verified
    }

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int) = search(query, page, pageSize, "songs", ::parseTrack)
    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int) = search(query, page, pageSize, "playlists", ::parsePlaylist)
    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int) = search(query, page, pageSize, "albums", ::parseAlbum)
    override suspend fun searchArtists(query: String, page: Int, pageSize: Int) = search(query, page, pageSize, "artists", ::parseArtist)
    private suspend fun <T> search(query: String, page: Int, pageSize: Int, type: String, parse: (JSONObject) -> T?): MusicPage<T> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 25)
        if (query.isBlank()) return@withContext MusicPage(emptyList(), safePage, safeSize, 0)
        val data = getJson("v1/catalog/${storefront()}/search", mapOf("term" to query.trim(), "types" to type,
            "limit" to safeSize.toString(), "offset" to ((safePage - 1L) * safeSize).toString())).optJSONObject("results")?.optJSONObject(type) ?: JSONObject()
        val items = data.array().mapNotNull(parse)
        MusicPage(items, safePage, safeSize, data.total(), hasMore = data.optString("next").isNotBlank() || (data.total()?.let { safePage.toLong() * safeSize < it } ?: (items.size == safeSize)))
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        require(track.id.source == source)
        return PlaybackResolution.Unavailable("Apple Music 全曲播放需要 Apple 官方 MusicKit DRM 桌面播放器；当前仅接入真实目录与资料库")
    }

    override suspend fun homeFeed(playlistLimit: Int, newSongLimit: Int, rankingLimit: Int): MusicHomeFeed = withContext(Dispatchers.IO) {
        val data = getJson("v1/catalog/${storefront()}/charts", mapOf("types" to "playlists,songs",
            "limit" to maxOf(playlistLimit, newSongLimit, rankingLimit).coerceIn(1, 25).toString())).optJSONObject("results")
        fun charts(type: String) = data?.optJSONArray(type).objects().flatMap { it.array() }
        MusicHomeFeed(recommendedPlaylists = charts("playlists").mapNotNull(::parsePlaylist).take(playlistLimit.coerceAtLeast(0)),
            newSongs = charts("songs").mapNotNull(::parseTrack).take(newSongLimit.coerceAtLeast(0)))
    }
    override suspend fun accountSummary(): MusicAccountSummary? = sessionProvider().takeIf { it.hasUserAuthorization }?.let {
        MusicAccountSummary(source, "apple-music-user", "Apple Music", subtitle = "Music User Token 已配置")
    }
    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val safeSize = pageSize.coerceIn(1, 100)
        val root = getJson("v1/me/library/playlists", pageQuery(safePage, safeSize), userRequired = true)
        val items = root.array().mapNotNull(::parsePlaylist)
        MusicPage(items, safePage, safeSize, root.total(), hasMore = root.optString("next").isNotBlank())
    }
    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        require(playlist.id.source == source)
        val personal = playlist.id.value.startsWith("p.")
        val path = if (personal) "v1/me/library/playlists/${segment(playlist.id.value)}" else "v1/catalog/${storefront()}/playlists/${segment(playlist.id.value)}"
        val info = getJson(path, userRequired = personal).array().firstOrNull()
        val tracks = getJson("$path/tracks", pageQuery(page, pageSize.coerceIn(1, 100)), userRequired = personal)
        MusicPlaylistDetail(info?.let(::parsePlaylist) ?: playlist, tracks.array().mapNotNull(::parseTrack), tracks.total())
    }
    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail = withContext(Dispatchers.IO) {
        require(album.id.source == source)
        val path = "v1/catalog/${storefront()}/albums/${segment(album.id.value)}"
        val info = getJson(path).array().firstOrNull()
        val tracks = getJson("$path/tracks", pageQuery(page, pageSize.coerceIn(1, 100)))
        MusicAlbumDetail(info?.let(::parseAlbum) ?: album, tracks.array().mapNotNull(::parseTrack), tracks.total())
    }
    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail = withContext(Dispatchers.IO) {
        require(artist.id.source == source)
        val root = getJson("v1/catalog/${storefront()}/artists/${segment(artist.id.value)}", mapOf("include" to "albums"))
        MusicArtistDetail(root.array().firstOrNull()?.let(::parseArtist) ?: artist, emptyList(), null)
    }
    override suspend fun lyrics(track: MusicTrack): LyricsDocument = withContext(Dispatchers.IO) {
        require(track.id.source == source)
        val item = getJson("v1/catalog/${storefront()}/songs/${segment(track.id.value)}", mapOf("include" to "lyrics")).array().firstOrNull()
        val attributes = item?.optJSONObject("relationships")?.optJSONObject("lyrics")?.array()?.firstOrNull()?.optJSONObject("attributes")
            ?: return@withContext LyricsDocument(emptyList(), source = LyricSource.AppleMusic)
        val ttml = attributes.optString("ttml").takeIf { it.isNotBlank() }
            ?.let { TtmlLyricsParser.parse(it) }?.takeIf { it.lines.isNotEmpty() }
        if (ttml != null) return@withContext ttml.copy(source = LyricSource.AppleMusic)
        val text = attributes.optString("text").trim()
        // Apple's untimed field is one authored block; do not invent per-line times.
        LyricsDocument(
            if (text.isBlank()) emptyList() else listOf(LyricLine(0, text = text)),
            source = LyricSource.AppleMusic,
            pseudoTimingAllowed = false,
        )
    }

    private fun getJson(path: String, query: Map<String, String> = emptyMap(), userRequired: Boolean = false): JSONObject {
        val session = sessionProvider()
        if (!session.isConfigured) throw IOException("Apple Music 未配置 Developer Token")
        if (userRequired && !session.hasUserAuthorization) throw IOException("Apple Music 未配置 Music User Token")
        val url = "https://api.music.apple.com/".toHttpUrl().newBuilder().addPathSegments(path.trimStart('/'))
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${session.developerToken}")
            .header("Accept", "application/json").header("Accept-Language", java.util.Locale.getDefault().toLanguageTag())
            .header("User-Agent", "MeloX-Desktop/1.0").apply {
                if (session.hasUserAuthorization) header("Music-User-Token", session.musicUserToken)
            }.build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Apple Music API HTTP ${response.code}")
            check(session == sessionProvider()) { "Apple Music 凭据已变化，请重试" }
            JSONObject(response.body.string())
        }
    }
    private fun storefront(): String = sessionProvider().storefront.lowercase().also { require(it.matches(Regex("[a-z]{2}"))) { "Apple Music storefront 无效" } }
    private fun segment(value: String) = value.also { require(it.isNotBlank() && it.none { c -> c == '/' || c == '?' || c == '#' || c == '%' }) { "Apple Music 资源身份无效" } }
    private fun pageQuery(page: Int, size: Int) = mapOf("limit" to size.toString(), "offset" to ((page.coerceAtLeast(1) - 1L) * size).toString())
    private fun JSONObject.array() = optJSONArray("data").objects()
    private fun JSONObject.total(): Long? = optJSONObject("meta")?.takeIf { it.has("total") }?.optLong("total")
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
    private fun artwork(attributes: JSONObject): String? = attributes.optJSONObject("artwork")?.optString("url")?.takeIf(String::isNotBlank)
        ?.replace("{w}", "800")?.replace("{h}", "800")?.replace("{f}", "jpg")
    private fun parseTrack(value: JSONObject): MusicTrack? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val title = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        val preview = attributes.optJSONArray("previews")?.optJSONObject(0)?.optString("url")?.takeIf(String::isNotBlank)
        val art = artwork(attributes)
        return MusicTrack(MusicResourceId(source, id), title, listOf(MusicArtistRef(name = attributes.optString("artistName").ifBlank { "未知歌手" })),
            album = attributes.optString("albumName").takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it, artworkUrl = art) },
            artworkUrl = art, durationMs = attributes.optLong("durationInMillis").takeIf { it > 0 },
            availability = if (preview != null) TrackAvailability.PreviewOnly else TrackAvailability.SubscriptionRequired,
            providerMetadata = ProviderTrackMetadata.AppleMusic(id, storefront(), preview))
    }
    private fun parsePlaylist(value: JSONObject): MusicPlaylistSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val name = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicPlaylistSummary(MusicResourceId(source, id), name, artwork(attributes), creatorName = attributes.optString("curatorName").takeIf(String::isNotBlank),
            description = attributes.optJSONObject("description")?.optString("standard")?.takeIf(String::isNotBlank), trackCount = attributes.optInt("trackCount").takeIf { it > 0 })
    }
    private fun parseAlbum(value: JSONObject): MusicAlbumSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val name = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicAlbumSummary(MusicResourceId(source, id), name, artwork(attributes), artists = listOf(MusicArtistRef(name = attributes.optString("artistName").ifBlank { "未知歌手" })),
            releaseDate = attributes.optString("releaseDate").takeIf(String::isNotBlank), trackCount = attributes.optLong("trackCount").takeIf { it > 0 })
    }
    private fun parseArtist(value: JSONObject): MusicArtistSummary? {
        val id = value.optString("id").takeIf(String::isNotBlank) ?: return null
        val attributes = value.optJSONObject("attributes") ?: return null
        val name = attributes.optString("name").takeIf(String::isNotBlank) ?: return null
        return MusicArtistSummary(MusicResourceId(source, id), name, artwork(attributes))
    }
}
