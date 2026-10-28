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
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.PlaylistCapability
import melox.music.provider.PlaylistWriteCapability
import melox.music.provider.RankingCapability
import melox.music.provider.SearchCapability
import melox.music.provider.UserLibraryCapability
import java.time.LocalDate
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * 酷狗概念版 (KuGou lite) provider.
 *
 * This is an independent [MusicSource] rather than a mode of the standard
 * [KugouProvider]: the two apps have different appids, signature salts, login
 * tokens and quality entitlements, and the standard token must never be sent to
 * a lite endpoint (or vice versa).
 */
class KugouLiteProvider(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
    persistAuth: (KugouSession, String) -> Unit = { _, _ -> },
    persistVip: (KugouSession, KugouLiteVip) -> Unit = { _, _ -> },
    persistDfid: (KugouSession, String) -> Unit = { _, _ -> },
    onAuthExpired: () -> Unit = {},
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
    ArtistCapability {
    override val source: MusicSource = MusicSource.KugouLite
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

    private val api = KugouLiteApiClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
        persistAuth = persistAuth,
        persistVip = persistVip,
        persistDfid = persistDfid,
        onAuthExpired = onAuthExpired,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        api.searchSongs(query, page, pageSize)

    override suspend fun searchPlaylists(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicPlaylistSummary> = api.searchPlaylists(query, page, pageSize)

    override suspend fun searchAlbums(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicAlbumSummary> = api.searchAlbums(query, page, pageSize)

    override suspend fun searchArtists(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicArtistSummary> = api.searchArtists(query, page, pageSize)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument = api.lyricDocument(track)

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = api.resolvePlayback(track, quality)

    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed {
        val newSongs = runCatching { api.homeFeedNewSongs(newSongLimit) }.getOrDefault(emptyList())
        val playlists = runCatching { api.recommendedPlaylists(playlistLimit) }.getOrDefault(emptyList())
        val rankings = runCatching { api.rankings(rankingLimit) }.getOrDefault(emptyList())
        return MusicHomeFeed(
            recommendedPlaylists = playlists,
            newSongs = newSongs,
            rankings = rankings,
        )
    }

    override suspend fun accountSummary(): MusicAccountSummary? = api.accountSummary()

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        api.userPlaylists(page, pageSize)

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = api.playlistDetail(playlist, page, pageSize)

    override suspend fun writablePlaylists(
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicPlaylistSummary> = api.writablePlaylists(page, pageSize)

    override suspend fun addTrackToPlaylist(
        track: MusicTrack,
        playlist: MusicPlaylistSummary,
    ) {
        api.addTrackToPlaylist(track, playlist)
    }

    override suspend fun rankingTracks(
        ranking: MusicRankingSummary,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> = api.rankingTracks(ranking, page, pageSize)

    override suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ): MusicAlbumDetail = api.albumDetail(album, page, pageSize)

    override suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ): MusicArtistDetail = api.artistDetail(artist, page, pageSize)

    suspend fun songDetail(track: MusicTrack): MusicTrack = api.songDetail(track)

    suspend fun vipStatus(force: Boolean = false): KugouLiteVip = api.vipStatus(force)

    suspend fun claimDailyVip(date: LocalDate = LocalDate.now()): JSONObject = api.claimDailyVip(date)

    suspend fun upgradeDailyVip(): JSONObject = api.upgradeDailyVip()
}
