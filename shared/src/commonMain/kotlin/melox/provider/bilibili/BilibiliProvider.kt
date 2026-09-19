package melox.provider.bilibili

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

class BilibiliProvider(
    sessionProvider: () -> BilibiliSession = { BilibiliSession("", "") },
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
    associationProvider: (String, String) -> BilibiliPlaybackAssociation? = { _, _ -> null },
    apiCache: BilibiliApiCache = BilibiliApiCache.shared(),
    sessionRevisionProvider: () -> Long = { 0L },
) : MusicProvider, SearchCapability, PlaybackCapability {
    override val source: MusicSource = MusicSource.Bilibili
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Playback)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        MusicPage(emptyList(), page, pageSize, 0)

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        PlaybackResolution.Unavailable("Bilibili provider not yet implemented on desktop")

    companion object {
        fun cleanTitle(value: String) = value.replace(Regex("</?em[^>]*>", RegexOption.IGNORE_CASE), "").trim()
    }
}