package melox.ui.screens

import androidx.compose.ui.graphics.Color
import java.io.InputStream
import java.net.URL
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.network.MeloXHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class MeloXDetailPalette(
    val background: Color,
    val prefersDarkAppearance: Boolean,
) {
    companion object {
        val LightFallback = MeloXDetailPalette(Color(0xFFE0E0E0), false)
        val DarkFallback = MeloXDetailPalette(Color(0xFF292929), true)
    }
}

/**
 * Pixel-for-pixel port of Android MeloXDetailPalette.kt (the palette branch
 * used by ArtworkAccentColorProvider.makeDetailPalette in MeloX iOS).
 *
 * The original downsamples artwork to <= 160 px, area-averages the image,
 * uses a 0.52 luminance split, then mixes the source average toward 0.055
 * for dark artwork or 0.94 for light artwork.
 */
internal object MeloXDetailPaletteProvider {
    private const val TARGET_SIZE = 160
    private const val MAX_CACHE_ENTRIES = 48
    private val http: OkHttpClient = MeloXHttpClient.shared
    private val cache = object : LinkedHashMap<String, MeloXDetailPalette>(MAX_CACHE_ENTRIES, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MeloXDetailPalette>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    suspend fun paletteFor(url: String?): MeloXDetailPalette {
        val source = url?.takeIf(String::isNotBlank) ?: return MeloXDetailPalette.LightFallback
        cached(source)?.let { return it }
        return withContext(Dispatchers.IO) {
            cached(source)?.let { return@withContext it }
            val palette = runCatching {
                val request = Request.Builder()
                    .url(optimizedArtworkUrl(source))
                    .header("User-Agent", "MeloX-Android/0.1")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Artwork HTTP ${response.code}")
                    response.body?.byteStream()?.use(::decodeBitmapStream)
                        ?: error("Unable to decode artwork")
                }
            }.getOrNull()
            if (palette != null) synchronized(cache) { cache[source] = palette }
            palette ?: MeloXDetailPalette.LightFallback
        }
    }

    private fun decodeBitmapStream(stream: InputStream): MeloXDetailPalette? {
        val image = javax.imageio.ImageIO.read(stream) ?: return null
        val maximum = maxOf(image.width, image.height)
        val scale = if (maximum > TARGET_SIZE) TARGET_SIZE.toDouble() / maximum else 1.0
        val targetW = (image.width * scale).toInt().coerceAtLeast(1)
        val targetH = (image.height * scale).toInt().coerceAtLeast(1)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var count = 0L
        // Area-average by sampling with nearest step when downscaling.
        val stepX = (image.width.toDouble() / targetW).coerceAtLeast(1.0)
        val stepY = (image.height.toDouble() / targetH).coerceAtLeast(1.0)
        var y = 0
        while (y < targetH) {
            var x = 0
            while (x < targetW) {
                val px = image.getRGB((x * stepX).toInt().coerceAtMost(image.width - 1), (y * stepY).toInt().coerceAtMost(image.height - 1))
                r += ((px shr 16) and 0xFF) / 255.0
                g += ((px shr 8) and 0xFF) / 255.0
                b += (px and 0xFF) / 255.0
                count += 1
                x++
            }
            y++
        }
        if (count == 0L) return MeloXDetailPalette.LightFallback
        r /= count
        g /= count
        b /= count

        val luminance = r * 0.2126 + g * 0.7152 + b * 0.0722
        val dark = luminance < 0.52
        val mix = if (dark) 0.055 else 0.94
        val sourceWeight = if (dark) 0.38 else 0.30
        val neutralWeight = if (dark) 0.62 else 0.70
        return MeloXDetailPalette(
            background = Color(
                red = (r * sourceWeight + mix * neutralWeight).toFloat().coerceIn(0f, 1f),
                green = (g * sourceWeight + mix * neutralWeight).toFloat().coerceIn(0f, 1f),
                blue = (b * sourceWeight + mix * neutralWeight).toFloat().coerceIn(0f, 1f),
                alpha = 1f,
            ),
            prefersDarkAppearance = dark,
        )
    }

    private fun cached(source: String): MeloXDetailPalette? = synchronized(cache) { cache[source] }

    private fun optimizedArtworkUrl(source: String): String {
        if (!source.contains(".music.126.net")) return source
        val withoutParam = source
            .replace(Regex("([?&])param=[^&]*&?", RegexOption.IGNORE_CASE)) { match ->
                if (match.value.startsWith("?")) "?" else "&"
            }
            .trimEnd('?', '&')
        val separator = if (withoutParam.contains('?')) '&' else '?'
        return "$withoutParam${separator}param=160y160"
    }
}
