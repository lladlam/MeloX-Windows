package melox.provider.qqmusic

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
import melox.music.provider.FavoriteCapability
import melox.music.provider.HomeFeedCapability
import melox.music.provider.LyricsCapability
import melox.music.provider.LocalAggregationCapability
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.PlaylistCapability
import melox.music.provider.RankingCapability
import melox.music.provider.SearchCapability
import melox.music.provider.UserLibraryCapability
import okhttp3.OkHttpClient

class QQMusicProvider(
    sessionProvider: () -> QQMusicSession = { QQMusicSession("", "", "") },
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider,
    SearchCapability,
    CatalogSearchCapability,
    LyricsCapability,
    PlaybackCapability,
    FavoriteCapability,
    HomeFeedCapability,
    UserLibraryCapability,
    PlaylistCapability,
    RankingCapability,
    AlbumCapability,
    ArtistCapability,
    LocalAggregationCapability {
    override val source: MusicSource = MusicSource.QQMusic
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.Favorites,
        MusicCapability.HomeRecommendations,
        MusicCapability.Rankings,
    )

    private val api = QQMusicApiClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val richLyrics = QQMusicRichLyricsClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val playback = QQMusicPlaybackVkeyClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val favorites = QQMusicFavoriteClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val playlists = QQMusicPlaylistClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val rankings = QQMusicRankingClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )
    private val catalog = QQMusicCatalogClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        api.searchSongs(query, page, pageSize)

    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        catalog.searchPlaylists(query, page, pageSize)

    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int): MusicPage<MusicAlbumSummary> =
        catalog.searchAlbums(query, page, pageSize)

    override suspend fun searchArtists(query: String, page: Int, pageSize: Int): MusicPage<MusicArtistSummary> =
        catalog.searchArtists(query, page, pageSize)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        val rich = runCatching { richLyrics.lyrics(track) }.getOrNull()
            ?: return api.lyrics(track)
        val needsTranslation = rich.lines.none { !it.translation.isNullOrBlank() }
        val needsRomanization = rich.lines.none { !it.romanization.isNullOrBlank() }
        if (!needsTranslation && !needsRomanization) return rich

        // QQ occasionally serves genuine QRC timing while the rich translation
        // field is empty. The ordinary lyric endpoint can still contain the
        // translation. Keep the QRC syllable timeline and only fill missing
        // annotations from the line-timed response.
        val fallback = runCatching { api.lyrics(track) }.getOrNull() ?: return rich
        return mergeFallbackAnnotations(rich, fallback)
    }

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = playback.resolve(track, quality)

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) {
        favorites.setFavorite(track, favorite)
    }

    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed = api.homeFeed(playlistLimit, newSongLimit, rankingLimit)

    override suspend fun accountSummary(): MusicAccountSummary? = api.accountSummary()

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        api.userPlaylists(page, pageSize)

    override suspend fun aggregationTracks(page: Int, pageSize: Int): MusicPage<MusicTrack> {
        val playlists = api.userPlaylists(page = 1, pageSize = 20).items
        val tracks = playlists.flatMap { playlist ->
            runCatching { this@QQMusicProvider.playlistDetail(playlist, page = 1, pageSize = pageSize).tracks }
                .getOrDefault(emptyList())
        }.distinctBy { it.id.value }
        return MusicPage(tracks, page, pageSize, tracks.size.toLong())
    }

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = playlists.detail(playlist, page, pageSize)

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

    private fun mergeFallbackAnnotations(
        rich: LyricsDocument,
        fallback: LyricsDocument,
    ): LyricsDocument {
        if (fallback.lines.isEmpty()) return rich
        return rich.copy(
            lines = rich.lines.map { line ->
                val annotation = fallback.lines
                    .minByOrNull { candidate -> kotlin.math.abs(candidate.timeMs - line.timeMs) }
                    ?.takeIf { candidate -> kotlin.math.abs(candidate.timeMs - line.timeMs) <= AnnotationToleranceMs }
                line.copy(
                    translation = line.translation
                        ?: annotation?.translation?.takeIf(String::isNotBlank),
                    romanization = line.romanization
                        ?: annotation?.romanization?.takeIf(String::isNotBlank),
                    romanizationSyllables = line.romanizationSyllables.ifEmpty {
                        annotation?.romanizationSyllables.orEmpty()
                    },
                )
            },
        )
    }

    private companion object {
        const val AnnotationToleranceMs = 1_500L
    }
}
