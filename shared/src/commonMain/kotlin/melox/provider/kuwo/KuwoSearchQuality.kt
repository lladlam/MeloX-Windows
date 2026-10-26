package melox.provider.kuwo

import melox.music.model.AudioQualityTier
import melox.music.model.audioQualityForBitrate
import melox.music.model.rank
import org.json.JSONObject

internal data class KuwoQualityHint(val tier: AudioQualityTier, val bitrate: Int?)

internal fun kuwoSearchQuality(item: JSONObject): KuwoQualityHint? {
    val info = item.optString("N_MINFO").ifBlank { item.optString("MINFO") }.ifBlank { item.optString("n_minfo") }
    fun hint(format: String, rawBitrate: Int?): KuwoQualityHint? {
        val bitrate = rawBitrate?.takeIf { it > 0 }?.let { if (it < 10_000) it * 1_000 else it }
        val tier = audioQualityForBitrate(bitrate, lossless = format.contains("flac") || format.contains("lossless"))
            ?: return null
        return KuwoQualityHint(tier, bitrate)
    }
    val variants = info.split(';').mapNotNull { entry ->
        val fields = entry.split(',').mapNotNull { part ->
            val index = part.indexOf(':')
            if (index <= 0) null else part.substring(0, index).trim().lowercase() to part.substring(index + 1).trim()
        }.toMap()
        hint(fields["format"].orEmpty().lowercase(), fields["bitrate"]?.toIntOrNull() ?: fields["br"]?.toIntOrNull())
    }
    return variants.maxWithOrNull(compareBy<KuwoQualityHint> { it.tier.rank() }.thenBy { it.bitrate ?: 0 })
        ?: hint(item.optString("FORMAT").lowercase(), item.optInt("BITRATE", 0).takeIf { it > 0 }
            ?: item.optInt("bitrate", 0).takeIf { it > 0 })
}
