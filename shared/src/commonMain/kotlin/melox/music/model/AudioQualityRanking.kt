package melox.music.model

fun AudioQualityTier.rank(): Int = when (this) {
    AudioQualityTier.Standard -> 1
    AudioQualityTier.High -> 2
    AudioQualityTier.Lossless -> 3
    AudioQualityTier.HiResolution, AudioQualityTier.Immersive -> 4
    AudioQualityTier.Master -> 5
}

/** Provider bitrate hints must be bits/s; explicit format flags take precedence. */
fun audioQualityForBitrate(
    bitrate: Int?,
    lossless: Boolean = false,
    hiResolution: Boolean = false,
    immersive: Boolean = false,
): AudioQualityTier? {
    if (immersive) return AudioQualityTier.Immersive
    if (hiResolution) return AudioQualityTier.HiResolution
    if (lossless) return AudioQualityTier.Lossless
    val bps = bitrate?.takeIf { it > 0 } ?: return null
    return when {
        bps >= 900_000 -> AudioQualityTier.Lossless
        bps >= 256_000 -> AudioQualityTier.High
        else -> AudioQualityTier.Standard
    }
}
