package melox.provider.kugou

import melox.lyrics.LyricsDocument
import melox.music.model.AudioQualityTier
import melox.music.model.MusicAccountSummary
import melox.music.model.MusicAlbumDetail
import melox.music.model.MusicAlbumSummary
import melox.music.model.MusicArtistDetail
import melox.music.model.MusicArtistSummary
import melox.music.model.MusicHomeFeed
import melox.music.model.MusicPage
import melox.music.model.MusicPlaylistDetail
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicRankingSummary
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.provider.AlbumCapability
import melox.music.provider.ArtistCapability
import melox.music.provider.CatalogSearchCapability
import melox.music.provider.HomeFeedCapability
import melox.music.provider.LyricsCapability
import melox.music.provider.LocalAggregationCapability
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.PlaylistCapability
import melox.music.provider.PlaylistWriteCapability
import melox.music.provider.RankingCapability
import melox.music.provider.SearchCapability
import melox.music.provider.UserLibraryCapability
import okhttp3.OkHttpClient

class KugouProvider(
    sessionProvider: () -> KugouSession = { KugouSession("", 0L, "", 0, "-", "", "", "", "", "") },
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider,
    SearchCapability,
    CatalogSearchCapability,
    LyricsCapability,
    PlaybackCapability,
    HomeFeedCapability,
    UserLibraryCapability,
    PlaylistCapability,
    PlaylistWriteCapability,
    RankingCapability,
    AlbumCapability,
    ArtistCapability,
    LocalAggregationCapability {
    override val source: MusicSource = MusicSource.Kugou
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.PlaylistWrite,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.HomeRecommendations,
        MusicCapability.Rankings,
    )

    private val api = KugouApiClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val lyrics = KugouLyricsClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val discovery = KugouDiscoveryClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val playlists = KugouPlaylistClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val playlistWrites = KugouPlaylistWriteClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val rankings = KugouRankingClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val catalog = KugouCatalogClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        api.searchSongs(query, page, pageSize)

    override suspend fun searchPlaylists(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicPlaylistSummary> = catalog.searchPlaylists(query, page, pageSize)

    override suspend fun searchAlbums(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicAlbumSummary> = catalog.searchAlbums(query, page, pageSize)

    override suspend fun searchArtists(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicArtistSummary> = catalog.searchArtists(query, page, pageSize)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument = lyrics.lyrics(track)

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = api.resolvePlayback(track, quality)

    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed = discovery.homeFeed(playlistLimit, newSongLimit, rankingLimit)

    override suspend fun accountSummary(): MusicAccountSummary? = discovery.accountSummary()

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        discovery.userPlaylists(page, pageSize)

    override suspend fun aggregationTracks(page: Int, pageSize: Int): MusicPage<MusicTrack> {
        val playlists = discovery.userPlaylists(page = 1, pageSize = 20).items
        val tracks = playlists.flatMap { playlist ->
            runCatching { this@KugouProvider.playlistDetail(playlist, page = 1, pageSize = pageSize).tracks }
                .getOrDefault(emptyList())
        }.distinctBy { it.id.value }
        return MusicPage(tracks, page, pageSize, tracks.size.toLong())
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = playlists.detail(playlist, page, pageSize)

    override suspend fun writablePlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        playlistWrites.writablePlaylists(page, pageSize)

    override suspend fun addTrackToPlaylist(
        track: MusicTrack,
        playlist: MusicPlaylistSummary,
    ) {
        playlistWrites.addTrackToPlaylist(track, playlist)
    }

    override suspend fun rankingTracks(
        ranking: MusicRankingSummary,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> = rankings.tracks(ranking, page, pageSize)

    override suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ): MusicAlbumDetail = catalog.albumDetail(album, page, pageSize)

    override suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ): MusicArtistDetail = catalog.artistDetail(artist, page, pageSize)
}
