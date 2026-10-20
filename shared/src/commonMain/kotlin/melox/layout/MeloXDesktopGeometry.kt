package melox.layout

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Native Desktop a41ea0e: independent from Android's landscape/list-detail policy. */
fun meloXDesktopShelfWidth(containerWidth: Float, minimum: Int, spacing: Int): Float {
    val usable = (containerWidth - 104f).coerceAtLeast(0f)
    val count = max(((usable + spacing) / (minimum + spacing)).toInt(), 1)
    return floor((usable - spacing * (count - 1)) / count).coerceAtLeast(1f)
}

/** Native LyricsSpecs uses discrete viewport breakpoints; compact is always 24. */
fun meloXDesktopLyricsFontSize(viewportWidth: Float, compact: Boolean): Float = when {
    compact || viewportWidth < 300 -> 24f
    viewportWidth < 528 -> 28f
    viewportWidth < 672 -> 38f
    viewportWidth < 760 -> 50f
    else -> 72f
}

data class MeloXDesktopPlayerGeometry(val width: Float, val height: Float) {
    private val heightProgress = ((height - 600) / 168).coerceIn(0f, 1f)
    val scale = min(max(height / 768, 1f), max(width / 1200, 1f))
    val playerWidth = (320 + heightProgress * 102) * scale
    val artworkSize = (214 + heightProgress * 94) * scale
    val artworkTop = (54 - (600 - height).coerceIn(0f, 50f) + heightProgress * 60) * scale
    val metadataTop = (70 + heightProgress * 10) * scale
    val playerLeading = max(width * .25f - playerWidth * .5f, 0f)
    val panelLeading = width * .5f
    val panelTrailing = max(32f, width * .045f)
    val chromeHeight = 48f
    val lyricsTop = chromeHeight + 40f
    val lyricsFocusOffset = max(chromeHeight + artworkTop + artworkSize * .5f - lyricsTop, 0f)
}
