package melox.network

import melox.music.model.AudioQualityTier
import melox.music.model.audioQualityForBitrate
import org.json.JSONObject

internal fun neteaseSearchQuality(song: JSONObject): Pair<AudioQualityTier, Int?>? {
    for ((key, tier) in listOf(
        "hr" to AudioQualityTier.HiResolution, "sq" to AudioQualityTier.Lossless,
        "h" to AudioQualityTier.High, "m" to AudioQualityTier.Standard, "l" to AudioQualityTier.Standard,
    )) {
        val value = song.optJSONObject(key) ?: continue
        val bitrate = value.optInt("br", value.optInt("bitrate", 0)).takeIf { it > 0 } ?: continue
        return tier to bitrate
    }
    val quality = song.optJSONObject("quality") ?: return null
    val bitrate = quality.optInt("br", quality.optInt("bitrate", 0)).takeIf { it > 0 }
    return audioQualityForBitrate(bitrate)?.let { it to bitrate }
}
