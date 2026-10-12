package melox.provider.jellyfin

import melox.music.model.*
import melox.music.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject

class JellyfinProvider(
    private val sessionProvider: () -> JellyfinSession = JellyfinSessionStore::read,
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, PlaybackCapability, CatalogSearchCapability,
    AlbumCapability, ArtistCapability, PlaylistCapability, UserLibraryCapability, FavoriteCapability,
    PlaylistWriteCapability, PlaylistSyncCapability {
    private val api = JellyfinApiClient(httpClient)
    override val source: MusicSource = MusicSource.Jellyfin
    override val displayName: String = source.displayName
    override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback, MusicCapability.Library,
        MusicCapability.Albums, MusicCapability.Artists, MusicCapability.Playlists, MusicCapability.Favorites, MusicCapability.PlaylistWrite)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        itemPage("Audio", query, page, pageSize, map = ::toTrack)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution = withContext(Dispatchers.IO) {
        requireResource(track.id)
        val session = sessionProvider().takeIf { it.isLoggedIn } ?: return@withContext PlaybackResolution.LoginRequired
        requireServer(track, session)
        PlaybackResolution.Playable(
            url = (session.baseUrl + path("Audio", track.id.value, "universal")).toHttpUrl().newBuilder()
                .addQueryParameter("UserId", session.userId).build().toString(),
            requestHeaders = mapOf("X-Emby-Token" to session.accessToken), requestedQuality = quality,
            // The server chooses the stream format; an arbitrary requested tier is not measured quality.
            actualQuality = null,
        )
    }

    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int) = itemPage("MusicAlbum", query, page, pageSize, map = ::toAlbum)
    override suspend fun searchArtists(query: String, page: Int, pageSize: Int) = itemPage("MusicArtist", query, page, pageSize, map = ::toArtist)
    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int) = itemPage("Playlist", query, page, pageSize, map = ::toPlaylist)
    override suspend fun accountSummary(): MusicAccountSummary? = sessionProvider().takeIf { it.isLoggedIn }?.let {
        MusicAccountSummary(source, it.userId, it.userName, subtitle = it.serverUrl)
    }
    override suspend fun userPlaylists(page: Int, pageSize: Int) = itemPage("Playlist", "", page, pageSize, map = ::toPlaylist)

    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail {
        requireResource(playlist.id)
        val session = requireSession()
        requireServerUrl(playlist.artworkUrl, session)
        val size = pageSize.coerceIn(1, 200)
        val json = api.get(session, path("Playlists", playlist.id.value, "Items"), pagination(page, size) + ("UserId" to session.userId))
        requireCurrentSession(session)
        return MusicPlaylistDetail(playlist, items(json).map { toTrack(it, playlist.id.value, session) }, total(json))
    }
    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail {
        requireResource(album.id)
        requireServerUrl(album.artworkUrl, requireSession())
        val result = itemPage("Audio", "", page, pageSize, mapOf("ParentId" to album.id.value), ::toTrack)
        return MusicAlbumDetail(album, result.items, result.total)
    }
    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail {
        requireResource(artist.id)
        requireServerUrl(artist.artworkUrl, requireSession())
        val result = itemPage("Audio", "", page, pageSize, mapOf("ArtistIds" to artist.id.value), ::toTrack)
        return MusicArtistDetail(artist, result.items, result.total)
    }

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        requireResource(track.id)
        val session = requireSession()
        requireServer(track, session)
        val endpoint = path("Users", session.userId, "FavoriteItems", track.id.value)
        if (favorite) api.post(session, endpoint) else api.delete(session, endpoint)
    }
    override suspend fun writablePlaylists(page: Int, pageSize: Int) = userPlaylists(page, pageSize)
    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        requireResource(track.id); requireResource(playlist.id)
        val session = requireSession(); requireServer(track, session)
        requireServerUrl(playlist.artworkUrl, session)
        api.post(session, path("Playlists", playlist.id.value, "Items"), query = mapOf("Ids" to track.id.value, "UserId" to session.userId))
    }
    override suspend fun createPlaylist(name: String): MusicPlaylistSummary {
        require(name.isNotBlank()) { "歌单名不能为空" }
        val session = requireSession()
        val json = api.post(session, "/Playlists", JSONObject().put("Name", name.trim()).put("UserId", session.userId).put("MediaType", "Audio"))
        val id = json.optString("Id").takeIf(String::isNotBlank) ?: error("Jellyfin 未返回新歌单 ID")
        return MusicPlaylistSummary(MusicResourceId(source, id), name.trim(), session.baseUrl + path("Items", id, "Images", "Primary"))
    }
    override suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String) {
        requireResource(playlist.id); require(name.isNotBlank()) { "歌单名不能为空" }
        val session = requireSession()
        requireServerUrl(playlist.artworkUrl, session)
        api.post(session, path("Playlists", playlist.id.value), JSONObject().put("Name", name.trim()))
    }
    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        val session = requireSession()
        val membership = requireMembership(track, playlist, session)
        api.delete(session, path("Playlists", playlist.id.value, "Items"), mapOf("EntryIds" to membership.playlistEntryId!!))
    }
    override suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int) {
        require(newIndex >= 0)
        val session = requireSession()
        val membership = requireMembership(track, playlist, session)
        api.post(session, path("Playlists", playlist.id.value, "Items", membership.playlistEntryId!!, "Move", newIndex.toString()))
    }

    private fun requireMembership(track: MusicTrack, playlist: MusicPlaylistSummary, session: JellyfinSession): ProviderTrackMetadata.Jellyfin {
        requireResource(track.id); requireResource(playlist.id); requireServer(track, session)
        requireServerUrl(playlist.artworkUrl, session)
        val metadata = requireNotNull(track.providerMetadata as? ProviderTrackMetadata.Jellyfin) { "缺少 Jellyfin 原生歌单条目 ID，请刷新歌单" }
        require(metadata.playlistId == playlist.id.value && !metadata.playlistEntryId.isNullOrBlank()) { "缺少 Jellyfin 原生歌单条目 ID，请刷新歌单" }
        return metadata
    }
    private fun requireSession() = sessionProvider().takeIf { it.isLoggedIn } ?: error("Jellyfin 尚未登录")
    private fun requireResource(id: MusicResourceId) { require(id.source == source && id.value.isNotBlank()) { "无效的 Jellyfin 资源身份" } }
    private fun requireServer(track: MusicTrack, session: JellyfinSession) {
        val metadata = track.providerMetadata as? ProviderTrackMetadata.Jellyfin ?: return
        require(metadata.serverIdentity == session.serverId.ifBlank { session.baseUrl }) { "资源来自其他 Jellyfin 服务器，请刷新资料库" }
    }
    private fun requireServerUrl(artworkUrl: String?, session: JellyfinSession) {
        if (artworkUrl == null) return // Existing persisted summaries may have no artwork.
        require(JellyfinArtworkAccess.requestHeaders(artworkUrl, session).isNotEmpty()) { "资源来自其他 Jellyfin 服务器，请刷新资料库" }
    }
    private fun requireCurrentSession(session: JellyfinSession) {
        if (sessionProvider() != session) throw kotlinx.coroutines.CancellationException("Jellyfin 登录会话已变更")
    }

    private suspend fun <T> itemPage(type: String, query: String, page: Int, pageSize: Int,
        filters: Map<String, String> = emptyMap(), map: (JSONObject, JellyfinSession) -> T): MusicPage<T> {
        val session = requireSession(); val number = page.coerceAtLeast(1); val size = pageSize.coerceIn(1, 200)
        val json = api.get(session, path("Users", session.userId, "Items"), filters + pagination(number, size) + mapOf(
            "IncludeItemTypes" to type, "Recursive" to "true", "SearchTerm" to query, "SortBy" to "SortName", "Fields" to "PrimaryImageAspectRatio,MediaStreams",
        ))
        requireCurrentSession(session)
        return MusicPage(items(json).map { map(it, session) }, number, size, total(json))
    }
    private fun pagination(page: Int, size: Int) = mapOf("StartIndex" to ((page.coerceAtLeast(1).toLong() - 1) * size).toString(), "Limit" to size.toString())
    private fun total(json: JSONObject) = json.takeIf { it.has("TotalRecordCount") }?.optLong("TotalRecordCount")?.takeIf { it >= 0 }
    private fun items(json: JSONObject): List<JSONObject> {
        val array = json.optJSONArray("Items") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.takeIf { item -> item.optString("Id").isNotBlank() } }
    }
    private fun toTrack(item: JSONObject, session: JellyfinSession): MusicTrack = toTrack(item, null, session)
    private fun toTrack(item: JSONObject, playlistId: String?, session: JellyfinSession): MusicTrack {
        val id = item.optString("Id")
        val artistItems = item.optJSONArray("ArtistItems")
        val artists = artistItems?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { artist ->
            artist.optString("Name").takeIf(String::isNotBlank)?.let { name -> MusicArtistRef(artist.optString("Id").takeIf(String::isNotBlank)?.let { MusicResourceId(source, it) }, name) }
        } } }.orEmpty().ifEmpty { listOf(MusicArtistRef(name = item.optString("AlbumArtist").ifBlank { "未知歌手" })) }
        val artwork = imageUrl(item, session)
        return MusicTrack(MusicResourceId(source, id), item.optString("Name").ifBlank { id }, artists,
            album = item.optString("Album").takeIf(String::isNotBlank)?.let { MusicAlbumRef(
                item.optString("AlbumId").takeIf(String::isNotBlank)?.let { MusicResourceId(source, it) }, it, artwork) },
            artworkUrl = artwork, durationMs = item.optLong("RunTimeTicks").takeIf { it > 0 }?.div(10_000),
            availability = TrackAvailability.Playable,
            providerMetadata = ProviderTrackMetadata.Jellyfin(session.serverId.ifBlank { session.baseUrl }, playlistId, item.optString("PlaylistItemId").takeIf(String::isNotBlank)))
    }
    private fun toAlbum(item: JSONObject, session: JellyfinSession) = MusicAlbumSummary(MusicResourceId(source, item.optString("Id")), item.optString("Name"), imageUrl(item, session),
        listOf(MusicArtistRef(name = item.optString("AlbumArtist").ifBlank { "未知歌手" })), item.optString("PremiereDate").takeIf(String::isNotBlank),
        item.optLong("ChildCount").takeIf { it > 0 })
    private fun toArtist(item: JSONObject, session: JellyfinSession) = MusicArtistSummary(MusicResourceId(source, item.optString("Id")), item.optString("Name"), imageUrl(item, session))
    private fun toPlaylist(item: JSONObject, session: JellyfinSession) = MusicPlaylistSummary(MusicResourceId(source, item.optString("Id")), item.optString("Name"), imageUrl(item, session),
        trackCount = item.optInt("ChildCount").takeIf { it >= 0 })
    private fun imageUrl(item: JSONObject, session: JellyfinSession): String? {
        val id = item.optString("Id").takeIf(String::isNotBlank) ?: return null
        return session.baseUrl + path("Items", id, "Images", "Primary")
    }

    private fun path(vararg segments: String) = segments.joinToString("/", prefix = "/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
}
