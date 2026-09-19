package melox.playback

import melox.music.model.AudioQualityTier
import melox.music.model.MusicTrack
import melox.provider.lxuser.ChkszApiClient
import melox.provider.lxuser.ChkszApiKeyStore
import melox.provider.lxuser.ChkszPlaybackResult

class ChkszPlaybackResolver {
    private val client = ChkszApiClient({ ChkszApiKeyStore.read() })

    fun cacheIdentity(): String = ChkszApiKeyStore.read().let { key ->
        if (key.isBlank()) "disabled" else "configured:${key.hashCode()}"
    }

    internal fun resolve(songId: Long, quality: AudioQualityTier): ChkszPlaybackResult? =
        client.resolveNetease(songId, quality)

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier): ChkszPlaybackResult? =
        client.resolveTrack(track, quality)
}