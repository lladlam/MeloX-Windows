package melox.provider.jellyfin

import melox.music.model.AudioQualityTier
import melox.music.model.MusicPage
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.SearchCapability
import okhttp3.OkHttpClient

class JellyfinProvider(
    sessionProvider: () -> JellyfinSession = { JellyfinSession("", "") },
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, PlaybackCapability {
    override val source: MusicSource = MusicSource.Jellyfin
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Playback)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        MusicPage(emptyList(), page, pageSize, 0)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        PlaybackResolution.Unavailable("Jellyfin provider not yet implemented on desktop")
}