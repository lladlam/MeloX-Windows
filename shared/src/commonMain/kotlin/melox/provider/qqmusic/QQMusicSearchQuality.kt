package melox.provider.qqmusic

import melox.music.model.AudioQualityTier
import melox.music.model.rank
import org.json.JSONObject

internal data class QQMusicQualityHint(val tier: AudioQualityTier, val bitrate: Int?)

/** Read both legacy byte sizes and new format entries without assuming a provider ceiling. */
internal fun qqMusicQualityHint(item: JSONObject): QQMusicQualityHint? {
    val file = item.optJSONObject("file") ?: item.optJSONObject("fileInfo") ?: item.optJSONObject("songFile")
        ?: return directQuality(item)
    val hints = mutableListOf<QQMusicQualityHint>()
    val legacy = listOf(
        "size_hires" to AudioQualityTier.HiResolution,
        "size_flac" to AudioQualityTier.Lossless,
        "size_320mp3" to AudioQualityTier.High,
        "size_128mp3" to AudioQualityTier.Standard,
        "size_96aac" to AudioQualityTier.Standard,
        "size_48aac" to AudioQualityTier.Standard,
    )
    for ((key, tier) in legacy) {
        if (file.optLong(key, 0L) <= 0L) continue
        hints.add(QQMusicQualityHint(tier, when (tier) {
            AudioQualityTier.HiResolution -> 1_411_200
            AudioQualityTier.Lossless -> 1_000_000
            AudioQualityTier.High -> 320_000
            else -> null
        }))
    }
    val variants = mutableListOf<JSONObject>()
    for (key in listOf("size_new", "size")) {
        file.optJSONArray(key)?.let { array ->
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(variants::add)
        }
    }
    file.optJSONObject("size_new")?.let { values ->
        values.keys().forEach { key -> values.optJSONObject(key)?.let(variants::add) }
    }
    variants.mapNotNullTo(hints, ::directQuality)
    return hints.maxWithOrNull(compareBy<QQMusicQualityHint> { it.tier.rank() }.thenBy { it.bitrate ?: 0 })
        ?: directQuality(file)
}

private fun directQuality(value: JSONObject): QQMusicQualityHint? {
    val bitrate = value.optInt("bitrate", value.optInt("br", 0)).takeIf { it > 0 }?.let {
        if (it < 10_000) it * 1_000 else it
    }
    val format = listOf("format", "file_type", "type", "ext").joinToString(" ") { value.optString(it) }.lowercase()
    val tier = when {
        format.contains("master") -> AudioQualityTier.Master
        format.contains("atmos") || format.contains("spatial") -> AudioQualityTier.Immersive
        listOf("hires", "hi_res", "hi-res").any(format::contains) -> AudioQualityTier.HiResolution
        format.contains("flac") || format.contains("lossless") -> AudioQualityTier.Lossless
        format.contains("320") || (bitrate ?: 0) >= 256_000 -> AudioQualityTier.High
        bitrate != null || format.isNotBlank() -> AudioQualityTier.Standard
        else -> return null
    }
    return QQMusicQualityHint(tier, bitrate)
}
