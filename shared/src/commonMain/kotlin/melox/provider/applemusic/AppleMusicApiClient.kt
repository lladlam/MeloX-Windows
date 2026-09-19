package melox.provider.applemusic

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

class AppleMusicApiClient(
    sessionProvider: () -> AppleMusicSession = { AppleMusicSession(false, "US") },
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, PlaybackCapability {
    override val source: MusicSource = MusicSource.AppleMusic
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Playback)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        MusicPage(emptyList(), page, pageSize, 0)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        PlaybackResolution.Unavailable("Apple Music provider not yet implemented on desktop")
}