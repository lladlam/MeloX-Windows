package melox.provider.kugou

import melox.music.model.AudioQualityTier
import org.json.JSONObject

internal data class KugouQualityHint(val tier: AudioQualityTier, val bitrate: Int)

/** Non-zero variant byte sizes are catalogue hints, not playback entitlements. */
internal fun kugouSearchQuality(item: JSONObject): KugouQualityHint? {
    val sizes = listOf(
        listOf("ResFileSize", "res_filesize", "SuperFileSize", "viper_atmos_filesize") to AudioQualityTier.HiResolution,
        listOf("SQFileSize", "sq_filesize", "FlacFileSize") to AudioQualityTier.Lossless,
        listOf("HQFileSize", "hq_filesize") to AudioQualityTier.High,
        listOf("FileSize", "filesize") to AudioQualityTier.Standard,
    )
    val tier = sizes.firstOrNull { (keys, _) -> keys.any { item.optLong(it, 0L) > 0L } }?.second ?: return null
    return KugouQualityHint(tier, when (tier) {
        AudioQualityTier.HiResolution -> 1_411_200
        AudioQualityTier.Lossless -> 1_000_000
        AudioQualityTier.High -> 320_000
        else -> 128_000
    })
}
