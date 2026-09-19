package melox.provider.lxuser

import melox.music.model.AudioQualityTier
import melox.music.model.MusicTrack

class ChkszApiClient(
    private val apiKeyProvider: () -> String = { "" },
) {
    fun resolveNetease(songId: Long, quality: AudioQualityTier): ChkszPlaybackResult? = null

    fun resolveTrack(track: MusicTrack, quality: AudioQualityTier): ChkszPlaybackResult? = null
}

data class ChkszPlaybackResult(val url: String)