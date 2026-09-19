package melox.provider.youtubemusic

import melox.music.model.AudioQualityTier
import melox.music.model.MusicAccountSummary
import melox.music.model.MusicPage
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.provider.DownloadCapability
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.SearchCapability
import melox.music.provider.UserLibraryCapability
import okhttp3.OkHttpClient

class YouTubeMusicProvider(
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, PlaybackCapability, DownloadCapability, UserLibraryCapability {
    override val source: MusicSource = MusicSource.YouTubeMusic
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Search, MusicCapability.Playback, MusicCapability.Library)

    val session: YouTubeSession get() = YouTubeSessionStore.read()
    init { YouTubeSessionStore.apply() }

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        MusicPage(emptyList(), page, pageSize, 0)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        PlaybackResolution.Unavailable("YouTube Music 桌面端流解析尚未接入")

    override suspend fun resolveDownload(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        PlaybackResolution.Unavailable("YouTube Music 桌面端下载尚未接入")

    override suspend fun accountSummary(): MusicAccountSummary? = null

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        MusicPage(emptyList(), page, pageSize, 0)
}
