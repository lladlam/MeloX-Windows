package melox.provider.netease

import melox.audio.MusicQuality
import melox.audio.NeteaseQualityClient
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.lyrics.LyricsDocument
import melox.music.model.AudioQualityTier
import melox.music.model.MusicAlbumRef
import melox.music.model.MusicArtistRef
import melox.music.model.MusicPage
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.model.ProviderTrackMetadata
import melox.music.model.TrackAvailability
import melox.music.provider.LyricsCapability
import melox.music.provider.LocalAggregationCapability
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.SearchCapability
import melox.network.NeteaseSearchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Compatibility adapter around the already migrated NetEase implementation.
 * NetEase-native capabilities remain in the existing Netease* clients so future
 * MeloX iOS migrations can keep their current one-to-one structure.
 */
class NeteaseProvider(
    private val cookieProvider: () -> String = { "" },
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, LyricsCapability, PlaybackCapability, LocalAggregationCapability {
    override val source: MusicSource = MusicSource.Netease
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.Comments,
        MusicCapability.HomeRecommendations,
        MusicCapability.DailyRecommendations,
        MusicCapability.Rankings,
        MusicCapability.Podcasts,
        MusicCapability.CloudMusic,
        MusicCapability.PrivateFm,
        MusicCapability.HeartMode,
        MusicCapability.ListenTogether,
        MusicCapability.Messages,
        MusicCapability.Recognition,
    )

    private val searchClient = NeteaseSearchClient(
        httpClient = httpClient,
        cookieProvider = cookieProvider,
    )
    private val qualityClient = NeteaseQualityClient(
        cookieProvider = cookieProvider,
        httpClient = httpClient,
    )

    override suspend fun searchSongs(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> {
        if (page < 1) return MusicPage(emptyList(), 1, pageSize.coerceAtLeast(1), 0)
        // Current NeteaseSearchClient mirrors the iOS first-page path. Keep it
        // untouched during the no-behaviour-change migration; pagination can be
        // delegated to NeteaseUniversalSearchClient when UI starts requesting it.
        if (page > 1) return MusicPage(emptyList(), page, pageSize.coerceAtLeast(1), null, false)
        val size = pageSize.coerceIn(1, 50)
        // The original NetEase SearchScreen enriches missing artwork after search.
        // Unified search must do the same before mapping into provider-neutral tracks,
        // otherwise the Compose row receives a blank artwork URL.
        val songs = searchClient.ensureArtwork(searchClient.searchSongs(query, size)).map { song ->
            MusicTrack(
                id = MusicResourceId(MusicSource.Netease, song.id.toString()),
                title = song.name,
                artists = song.artists
                    .split(" / ")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .map { MusicArtistRef(name = it) },
                album = song.album.takeIf(String::isNotBlank)?.let {
                    MusicAlbumRef(name = it, artworkUrl = song.artworkUrl)
                },
                artworkUrl = song.artworkUrl,
                durationMs = song.durationMs.takeIf { it > 0L },
                availability = TrackAvailability.Playable,
                providerMetadata = ProviderTrackMetadata.Netease(song.id),
            )
        }
        return MusicPage(
            items = songs,
            page = page,
            pageSize = size,
            total = null,
            hasMore = songs.size >= size,
        )
    }

    override suspend fun lyrics(track: MusicTrack): LyricsDocument =
        searchClient.lyrics(track.requireNeteaseId())

    override suspend fun aggregationTracks(page: Int, pageSize: Int): MusicPage<MusicTrack> =
        withContext(Dispatchers.IO) {
            if (page > 1) return@withContext MusicPage(emptyList(), page, pageSize, 0L, false)
            val cookie = cookieProvider()
            val userId = cookie
                .takeIf { NeteaseSessionStore.containsMusicU(it) }
                ?.let { runCatching { searchClient.accountProfile(it).userId }.getOrNull() }
                ?: return@withContext MusicPage(emptyList(), page, pageSize, 0L, false)
            val snapshot = NeteaseLibraryClient(cookieProvider, httpClient).snapshot(userId)
            val songs = (snapshot.likedSongs + snapshot.recentSongs).distinctBy { it.id }
            MusicPage(
                items = songs.map { song ->
                    MusicTrack(
                        id = MusicResourceId(MusicSource.Netease, song.id.toString()),
                        title = song.name,
                        artists = song.artists.split(" / ").map(String::trim).filter(String::isNotBlank).map { MusicArtistRef(name = it) },
                        album = song.album.takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it, artworkUrl = song.artworkUrl) },
                        artworkUrl = song.artworkUrl,
                        durationMs = song.durationMs.takeIf { it > 0L },
                        providerMetadata = ProviderTrackMetadata.Netease(song.id),
                    )
                },
                page = page,
                pageSize = pageSize,
                total = songs.size.toLong(),
            )
        }

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = withContext(Dispatchers.IO) {
        val requested = quality.toNeteaseQuality()
        val result = qualityClient.playbackSourceBlocking(track.requireNeteaseId(), requested)
        PlaybackResolution.Playable(
            url = result.url,
            requestedQuality = quality,
            actualQuality = result.quality?.toTier(),
            bitrate = result.bitrate,
            format = result.format,
        )
    }
}

private fun MusicTrack.requireNeteaseId(): Long {
    require(id.source == MusicSource.Netease) {
        "NeteaseProvider cannot handle ${id.source.storageValue} track"
    }
    return (providerMetadata as? ProviderTrackMetadata.Netease)?.numericId
        ?: id.value.toLongOrNull()
        ?: error("invalid NetEase track id: ${id.value}")
}

private fun AudioQualityTier.toNeteaseQuality(): MusicQuality = when (this) {
    AudioQualityTier.Standard -> MusicQuality.Standard
    AudioQualityTier.High -> MusicQuality.High
    AudioQualityTier.Lossless -> MusicQuality.Lossless
    AudioQualityTier.HiResolution -> MusicQuality.HiResolution
    AudioQualityTier.Immersive -> MusicQuality.ImmersiveSurround
    AudioQualityTier.Master -> MusicQuality.UltraClearMaster
}

private fun MusicQuality.toTier(): AudioQualityTier = when (this) {
    MusicQuality.Standard -> AudioQualityTier.Standard
    MusicQuality.High -> AudioQualityTier.High
    MusicQuality.Lossless -> AudioQualityTier.Lossless
    MusicQuality.HiResolution -> AudioQualityTier.HiResolution
    MusicQuality.HighDefinitionSurround,
    MusicQuality.ImmersiveSurround -> AudioQualityTier.Immersive
    MusicQuality.UltraClearMaster -> AudioQualityTier.Master
}
