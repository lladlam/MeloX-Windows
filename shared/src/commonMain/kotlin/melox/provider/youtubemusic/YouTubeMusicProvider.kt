package melox.provider.youtubemusic

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.ChartsPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.lyrics.LrcLibLyricsClient
import melox.lyrics.LyricsDocument
import melox.music.model.*
import melox.music.provider.*
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Android main's vendored InnerTube catalogue and NewPipe audio extraction on JVM. */
class YouTubeMusicProvider(
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, CatalogSearchCapability, PlaybackCapability,
    DownloadCapability, FavoriteCapability, UserLibraryCapability, PlaylistCapability, PlaylistWriteCapability,
    AlbumCapability, ArtistCapability, HomeFeedCapability, LyricsCapability, RankingCapability,
    LibraryCollectionCapability, PlaylistSyncCapability {
    val session: YouTubeSession get() = YouTubeSessionStore.read()
    private val lyricsClient = LrcLibLyricsClient(httpClient)
    init { YouTubeSessionStore.apply() }
    override val source = MusicSource.YouTubeMusic
    override val displayName = source.displayName
    override val capabilities = setOf(
        MusicCapability.Search, MusicCapability.Playback, MusicCapability.Lyrics,
        MusicCapability.Library, MusicCapability.Favorites, MusicCapability.Rankings,
        MusicCapability.Playlists, MusicCapability.Albums, MusicCapability.Artists,
        MusicCapability.HomeRecommendations,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_SONG) { (it as? SongItem)?.toMusicTrack() }
    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST) { (it as? PlaylistItem)?.toPlaylistSummary() }
    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_ALBUM) { (it as? AlbumItem)?.toAlbumSummary() }
    override suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        search(query, page, pageSize, YouTube.SearchFilter.FILTER_ARTIST) { (it as? ArtistItem)?.toArtistSummary() }

    override suspend fun accountSummary(): MusicAccountSummary? = withContext(Dispatchers.IO) {
        if (!session.isLoggedIn) return@withContext null
        YouTubeSessionStore.apply()
        YouTube.accountInfo().getOrThrow().let { MusicAccountSummary(source, it.email.orEmpty(), it.name, it.thumbnailUrl) }
    }

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> = withContext(Dispatchers.IO) {
        if (page > 1 || !session.isLoggedIn) return@withContext MusicPage(emptyList(), page, pageSize, 0)
        YouTubeSessionStore.apply()
        val liked = MusicPlaylistSummary(MusicResourceId(source, LIKED_SONGS), "Liked songs")
        val items = listOf(liked) + YouTube.library("FEmusic_liked_playlists").getOrThrow().items
            .filterIsInstance<PlaylistItem>().map { it.toPlaylistSummary() }
        MusicPage(items, page, pageSize, items.size.toLong(), false)
    }
    override suspend fun writablePlaylists(page: Int, pageSize: Int) = userPlaylists(page, pageSize)
    override suspend fun savedAlbums(page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        libraryPage(page, pageSize, "FEmusic_liked_albums") { (it as? AlbumItem)?.toAlbumSummary() }
    override suspend fun followedArtists(page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        libraryPage(page, pageSize, "FEmusic_library_corpus_track_artists") { (it as? ArtistItem)?.toArtistSummary() }

    private fun requireLogin() {
        check(session.isLoggedIn) { "YouTube Music 需要登录后才能修改收藏或歌单" }
        YouTubeSessionStore.apply()
    }
    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        require(track.id.source == source)
        requireLogin()
        YouTube.likeVideo(track.id.value, favorite).getOrThrow()
    }
    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        require(track.id.source == source && playlist.id.source == source)
        requireLogin()
        YouTube.addToPlaylist(playlist.id.value, track.id.value).getOrThrow()
    }
    override suspend fun createPlaylist(name: String): MusicPlaylistSummary = withContext(Dispatchers.IO) {
        requireLogin()
        val title = name.ifBlank { "New playlist" }
        MusicPlaylistSummary(MusicResourceId(source, YouTube.createPlaylist(title)), title)
    }
    override suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String) {
        require(playlist.id.source == source)
        requireLogin()
        YouTube.renamePlaylist(playlist.id.value, name).getOrThrow()
    }
    override val canDeletePlaylists = true
    override suspend fun deletePlaylist(playlist: MusicPlaylistSummary) {
        require(playlist.id.source == source)
        if (playlist.id.value == LIKED_SONGS) error("喜欢的歌曲不能删除")
        requireLogin()
        YouTube.deletePlaylist(playlist.id.value).getOrThrow()
    }
    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        require(track.id.source == source && playlist.id.source == source)
        val membership = track.providerMetadata as? ProviderTrackMetadata.YouTube
        val setVideoId = membership?.setVideoId ?: error("这首歌没有 YouTube 歌单成员标识，无法从歌单移除")
        requireLogin()
        YouTube.removeFromPlaylist(playlist.id.value, track.id.value, setVideoId).getOrThrow()
    }
    override suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int) {
        require(track.id.source == source && playlist.id.source == source)
        val membership = track.providerMetadata as? ProviderTrackMetadata.YouTube
        val setVideoId = membership?.setVideoId ?: error("这首歌没有 YouTube 歌单成员标识，无法调整顺序")
        requireLogin()
        val songs = YouTube.playlist(playlist.id.value).getOrThrow().songs
        val without = songs.filterNot { it.id == track.id.value }
        val successor = without.getOrNull(newIndex.coerceIn(0, without.size))
        YouTube.moveSongPlaylist(playlist.id.value, setVideoId, successor?.setVideoId).getOrThrow()
    }

    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail = withContext(Dispatchers.IO) {
        require(playlist.id.source == source)
        YouTubeSessionStore.apply()
        val result = YouTube.playlist(playlist.id.value).getOrThrow()
        val tracks = result.songs.map { it.toMusicTrack() }
        MusicPlaylistDetail(playlist.copy(title = result.playlist.title, artworkUrl = result.playlist.thumbnail,
            creatorName = result.playlist.author?.name), tracks, tracks.size.toLong())
    }
    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int): MusicAlbumDetail = withContext(Dispatchers.IO) {
        require(album.id.source == source)
        YouTubeSessionStore.apply()
        val result = YouTube.album(album.id.value).getOrThrow()
        val tracks = result.songs.map { it.toMusicTrack() }
        MusicAlbumDetail(result.album.toAlbumSummary(), tracks, tracks.size.toLong())
    }
    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int): MusicArtistDetail = withContext(Dispatchers.IO) {
        require(artist.id.source == source)
        YouTubeSessionStore.apply()
        val result = YouTube.artist(artist.id.value).getOrThrow()
        val tracks = result.sections.flatMap { it.items }.filterIsInstance<SongItem>().map { it.toMusicTrack() }
        MusicArtistDetail(result.artist.toArtistSummary(), tracks, tracks.size.toLong())
    }
    override suspend fun rankingTracks(ranking: MusicRankingSummary, page: Int, pageSize: Int): MusicPage<MusicTrack> = withContext(Dispatchers.IO) {
        require(ranking.id.source == source)
        val section = chartSections().firstOrNull { it.title == ranking.id.value }
            ?: error("这个榜单已经不在 YouTube Music 的排行里")
        val tracks = section.items.filterIsInstance<SongItem>().map { it.toMusicTrack() }
        val size = pageSize.coerceAtLeast(1)
        MusicPage(tracks.drop((page.coerceAtLeast(1) - 1) * size).take(size), page, size, tracks.size.toLong(), false)
    }
    override suspend fun homeFeed(playlistLimit: Int, newSongLimit: Int, rankingLimit: Int): MusicHomeFeed = withContext(Dispatchers.IO) {
        YouTubeSessionStore.apply()
        val playlists = mutableListOf<MusicPlaylistSummary>()
        val songs = mutableListOf<MusicTrack>()
        YouTube.home().getOrThrow().sections.flatMap { it.items }.forEach { item ->
            when (item) {
                is SongItem -> songs += item.toMusicTrack()
                is PlaylistItem -> playlists += item.toPlaylistSummary()
                is AlbumItem -> playlists += MusicPlaylistSummary(MusicResourceId(source, item.playlistId), item.title,
                    item.thumbnail, item.artists?.joinToString(", ") { it.name })
                else -> Unit
            }
        }
        val rankings = try { chartSections() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
        MusicHomeFeed(
            recommendedPlaylists = playlists.distinctBy { it.id }.take(playlistLimit.coerceAtLeast(0)),
            newSongs = songs.distinctBy { it.id }.take(newSongLimit.coerceAtLeast(0)),
            rankings = rankings.filter { it.items.any { item -> item is SongItem } }.map { section ->
                val preview = section.items.filterIsInstance<SongItem>().take(3).map { it.toMusicTrack() }
                MusicRankingSummary(MusicResourceId(source, section.title), section.title, preview.firstOrNull()?.artworkUrl, previewTracks = preview)
            }.take(rankingLimit.coerceAtLeast(0)),
        )
    }
    private suspend fun chartSections(): List<ChartsPage.ChartSection> {
        YouTubeSessionStore.apply()
        return YouTube.getChartsPage().getOrThrow().sections
    }
    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        require(track.id.source == source)
        return lyricsClient.lyrics(track.title, track.artists.firstOrNull()?.name.orEmpty(), track.durationMs ?: 0L)
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution = withContext(Dispatchers.IO) {
        require(track.id.source == source)
        try {
            synchronized(newPipeLock) {
                if (!newPipeInitialised) {
                    NewPipe.init(DesktopNewPipeDownloader(httpClient), Localization.DEFAULT)
                    newPipeInitialised = true
                }
            }
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=${track.id.value}")
            val stream = info.audioStreams.filter { it.content.isNotBlank() }.maxByOrNull { it.averageBitrate }
                ?: error("YouTube Music 无可用音频流")
            PlaybackResolution.Playable(url = stream.content, requestedQuality = quality, actualQuality = quality,
                bitrate = stream.averageBitrate.takeIf { it > 0 }, format = "m4a",
                expiresAtEpochMs = System.currentTimeMillis() + 90 * 60_000L)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { PlaybackResolution.Unavailable(error.message ?: "YouTube Music 播放失败") }
    }
    override suspend fun resolveDownload(track: MusicTrack, quality: AudioQualityTier) = resolvePlayback(track, quality)

    private suspend fun <T> libraryPage(page: Int, pageSize: Int, browseId: String, map: (YTItem) -> T?): MusicPage<T> = withContext(Dispatchers.IO) {
        if (page > 1 || !session.isLoggedIn) return@withContext MusicPage(emptyList(), page, pageSize, 0)
        YouTubeSessionStore.apply()
        val items = YouTube.library(browseId).getOrThrow().items.mapNotNull(map).take(pageSize.coerceAtLeast(1))
        MusicPage(items, page, pageSize, items.size.toLong(), false)
    }
    private suspend fun <T> search(query: String, page: Int, pageSize: Int, filter: YouTube.SearchFilter, map: (YTItem) -> T?): MusicPage<T> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext MusicPage(emptyList(), page, pageSize, 0, false)
        YouTubeSessionStore.apply()
        val result = YouTube.search(query.trim(), filter).getOrThrow()
        MusicPage(result.items.mapNotNull(map).take(pageSize.coerceAtLeast(1)), page, pageSize,
            result.items.size.toLong(), result.continuation != null)
    }

    private fun SongItem.toMusicTrack() = MusicTrack(
        id = MusicResourceId(source, id), title = title,
        artists = artists.map { MusicArtistRef(it.id?.let { id -> MusicResourceId(source, id) }, it.name) },
        album = album?.let { MusicAlbumRef(MusicResourceId(source, it.id), it.name) }, artworkUrl = thumbnail,
        durationMs = duration?.takeIf { it > 0 }?.times(1000L), availability = TrackAvailability.Playable,
        providerMetadata = ProviderTrackMetadata.YouTube(id, setVideoId),
    )
    private fun PlaylistItem.toPlaylistSummary() = MusicPlaylistSummary(MusicResourceId(source, id), title,
        thumbnail, author?.name, trackCount = songCountText?.filter(Char::isDigit)?.toIntOrNull())
    private fun AlbumItem.toAlbumSummary() = MusicAlbumSummary(MusicResourceId(source, browseId), title,
        thumbnail, artists.orEmpty().map { MusicArtistRef(it.id?.let { id -> MusicResourceId(source, id) }, it.name) }, year?.toString())
    private fun ArtistItem.toArtistSummary() = MusicArtistSummary(MusicResourceId(source, id), title, thumbnail)

    companion object {
        private const val LIKED_SONGS = "LM"
        private val newPipeLock = Any()
        @Volatile private var newPipeInitialised = false
    }
}

private class DesktopNewPipeDownloader(private val http: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url()).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36")
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("YouTube rate limited", request.url())
            return Response(response.code, response.message, response.headers.toMultimap(), response.body?.string(), response.request.url.toString())
        }
    }
}
