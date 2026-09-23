package melox.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.URI
import java.util.LinkedHashMap
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import melox.network.MeloXHttpClient
import melox.playback.MeloXAudioReactiveRuntime
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

enum class MeloXLyricsRenderingQuality { Low, Balanced, High }

internal data class ArtworkDynamicPalette(
    val cells: List<Color>,
    val average: Color,
) {
    companion object {
        val Fallback = ArtworkDynamicPalette(
            cells = listOf(
                Color(0xFF101E3D), Color(0xFF2E1A57), Color(0xFF0F3B4D),
                Color(0xFF38143D), Color(0xFF24476B), Color(0xFF4D1F4D),
                Color(0xFF0D2E42), Color(0xFF291A4D), Color(0xFF121C33),
            ),
            average = Color(0xFF171D32),
        )
    }
}

internal object ArtworkDynamicPaletteProvider {
    private const val GRID = 3
    private const val TARGET_SIZE = 160
    private const val MAX_CACHE_ENTRIES = 48
    private val cache = object : LinkedHashMap<String, ArtworkDynamicPalette>(MAX_CACHE_ENTRIES, .75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, ArtworkDynamicPalette>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }
    private val http = MeloXHttpClient.shared

    suspend fun paletteFor(url: String?): ArtworkDynamicPalette {
        val source = url?.takeIf(String::isNotBlank) ?: return ArtworkDynamicPalette.Fallback
        cached(source)?.let { return it }

        return withContext(Dispatchers.IO) {
            cached(source)?.let { return@withContext it }
            val palette = runCatching {
                val decoded = decodeArtwork(source)
                val scaled = if (decoded.width == TARGET_SIZE && decoded.height == TARGET_SIZE) {
                    decoded
                } else {
                    scaleNearest(decoded, TARGET_SIZE, TARGET_SIZE)
                }
                try {
                    makePalette(scaled)
                } finally {
                    if (scaled !== decoded) scaled.close()
                    decoded.close()
                }
            }.getOrNull()

            if (palette != null) synchronized(cache) { cache[source] = palette }
            palette ?: ArtworkDynamicPalette.Fallback
        }
    }

    private fun cached(source: String): ArtworkDynamicPalette? = synchronized(cache) { cache[source] }

    fun clearMemoryCache() = synchronized(cache) { cache.clear() }

    private fun decodeArtwork(source: String): Bitmap {
        val uri = runCatching { URI(source) }.getOrNull()
        return when (uri?.scheme?.lowercase()) {
            "http", "https" -> decodeNetworkArtwork(source)
            "file" -> decodeFileArtwork(File(uri).absolutePath)
            null -> decodeFileArtwork(source)
            else -> error("Unsupported artwork URI scheme: ${uri.scheme}")
        }
    }

    private fun decodeNetworkArtwork(source: String): Bitmap {
        val request = Request.Builder()
            .url(optimizedArtworkUrl(source))
            .header("User-Agent", "MeloX-Desktop/0.1")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Artwork HTTP ${response.code}")
            decodeBytes(response.body?.bytes() ?: error("Artwork HTTP empty body"))
        }
    }

    private fun decodeFileArtwork(path: String): Bitmap {
        val file = File(path)
        if (!file.isFile) error("Unable to read artwork file")
        return decodeBytes(file.readBytes())
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap {
        val image = Image.makeFromEncoded(bytes)
        val bitmap = Bitmap()
        val info = ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        check(bitmap.allocPixels(info)) { "Unable to allocate artwork pixels" }
        check(image.readPixels(bitmap)) { "Unable to read artwork pixels" }
        image.close()
        return bitmap
    }

    private fun scaleNearest(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcInfo = ImageInfo(source.width, source.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val srcBytes = source.readPixels(srcInfo, source.width * 4, 0, 0)
            ?: error("Unable to read artwork pixels")
        val srcWidth = source.width
        val srcHeight = source.height
        val dst = ByteArray(targetWidth * targetHeight * 4)
        var dstIndex = 0
        for (y in 0 until targetHeight) {
            val srcY = (y.toLong() * srcHeight / targetHeight).toInt().coerceIn(0, srcHeight - 1)
            val srcRow = srcY * srcWidth * 4
            for (x in 0 until targetWidth) {
                val srcX = (x.toLong() * srcWidth / targetWidth).toInt().coerceIn(0, srcWidth - 1)
                val srcIndex = srcRow + srcX * 4
                dst[dstIndex] = srcBytes[srcIndex]
                dst[dstIndex + 1] = srcBytes[srcIndex + 1]
                dst[dstIndex + 2] = srcBytes[srcIndex + 2]
                dst[dstIndex + 3] = srcBytes[srcIndex + 3]
                dstIndex += 4
            }
        }
        val scaled = Bitmap()
        val dstInfo = ImageInfo(targetWidth, targetHeight, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        check(scaled.allocPixels(dstInfo)) { "Unable to allocate scaled artwork" }
        check(scaled.installPixels(dst)) { "Unable to install scaled artwork" }
        return scaled
    }

    private fun makePalette(bitmap: Bitmap): ArtworkDynamicPalette {
        val width = bitmap.width
        val height = bitmap.height
        val cellWidth = width / GRID
        val cellHeight = height / GRID
        val rawCells = buildList(GRID * GRID) {
            for (row in 0 until GRID) {
                for (column in 0 until GRID) {
                    val left = column * cellWidth
                    val top = row * cellHeight
                    val right = if (column == GRID - 1) width else (column + 1) * cellWidth
                    val bottom = if (row == GRID - 1) height else (row + 1) * cellHeight
                    add(averageColor(bitmap, left, top, right, bottom))
                }
            }
        }
        val cells = rawCells.map { it.boostSaturation(1.2f) }
        return ArtworkDynamicPalette(
            cells = cells,
            average = averageColor(bitmap, 0, 0, width, height).boostSaturation(1.1f),
        )
    }

    private fun Color.boostSaturation(multiplier: Float): Color {
        val r = red
        val g = green
        val b = blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2f
        val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f)).coerceAtLeast(1e-6f)
        if (saturation < 0.08f) return this
        val hue = when {
            delta == 0f -> 0f
            max == r -> ((g - b) / delta).let { if (it < 0f) it + 6f else it } / 6f
            max == g -> ((b - r) / delta + 2f) / 6f
            else -> ((r - g) / delta + 4f) / 6f
        }
        return hslToColor(hue, (saturation * multiplier).coerceIn(0f, 1f), lightness)
    }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
        val h = ((hue % 1f) + 1f) % 1f
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs((h * 6f) % 2f - 1f))
        val m = lightness - c / 2f
        val (r1, g1, b1) = when ((h * 6f).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }

    private fun averageColor(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Color {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getColor(x, y)
                red += (pixel ushr 16) and 0xFF
                green += (pixel ushr 8) and 0xFF
                blue += pixel and 0xFF
                count += 1
                x += 2
            }
            y += 2
        }

        if (count == 0L) return Color(0xFF5B4B45)
        return Color(
            red = (red.toFloat() / count / 255f).coerceIn(0f, 1f),
            green = (green.toFloat() / count / 255f).coerceIn(0f, 1f),
            blue = (blue.toFloat() / count / 255f).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }

    private fun optimizedArtworkUrl(source: String): String {
        val uri = runCatching { URI(source) }.getOrNull() ?: return source
        if (uri.host?.endsWith(".music.126.net") != true) return source
        return source.toHttpUrlOrNull()
            ?.newBuilder()
            ?.setQueryParameter("param", "160y160")
            ?.build()
            ?.toString()
            ?: source
    }
}

@Composable
internal fun MeloXBlurredArtworkBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        DesktopArtworkImage(
            artworkUrl = artworkUrl,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.18f; scaleY = 1.18f }.blur(38.dp),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = .30f))
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .48f)),
                ),
            )
        }
    }
}

@Composable
internal fun MeloXLyricsArtworkBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    quality: MeloXLyricsRenderingQuality = MeloXLyricsRenderingQuality.High,
    backgroundFrameRate: Int = 24,
    reduceMotion: Boolean = false,
) {
    val planeCount = when (quality) {
        MeloXLyricsRenderingQuality.Low -> 1
        MeloXLyricsRenderingQuality.Balanced -> 2
        MeloXLyricsRenderingQuality.High -> 3
    }
    val frameRate = backgroundFrameRate.coerceIn(15, 60)
    val saturation = if (reduceMotion) 3.5f else 2.5f
    val artworkColorFilter = remember(saturation) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val elapsedWhilePlayingMs = remember(artworkUrl) { mutableLongStateOf(0L) }
    val settleUntilMs = remember(artworkUrl) { System.currentTimeMillis() + 1_600L }
    LaunchedEffect(planeCount, artworkUrl, frameRate) {
        var previousFrameAt = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            if (!latestIsPlaying && now >= settleUntilMs) {
                previousFrameAt = now
                delay(500L)
                continue
            }
            elapsedWhilePlayingMs.longValue += now - previousFrameAt
            previousFrameAt = now
            delay((1_000L / frameRate.toLong()).coerceAtLeast(1L))
        }
    }

    Box(modifier.fillMaxSize()) {
        repeat(planeCount) { index ->
            DesktopArtworkImage(
                artworkUrl = artworkUrl,
                colorFilter = artworkColorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.34f
                        scaleY = 1.34f
                        val elapsed = elapsedWhilePlayingMs.longValue.toFloat()
                        val duration = when (index) {
                            0 -> 120_000f
                            1 -> 90_000f
                            else -> 70_000f
                        }
                        val direction = if (index == 0) -1f else 1f
                        rotationZ = direction * (elapsed % duration) / duration * 360f
                        translationX = (index - 1) * 34f
                        translationY = (1 - index) * 22f
                        alpha = if (index == 0) .48f else .28f
                    }
                    .blur(if (quality == MeloXLyricsRenderingQuality.High) 30.dp else 24.dp),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = .34f))
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = .52f)),
                ),
            )
        }
    }
}

@Composable
internal fun MeloXFlowingLightBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    mediaId: String? = null,
    modifier: Modifier = Modifier,
    quality: MeloXLyricsRenderingQuality = MeloXLyricsRenderingQuality.High,
    backgroundFrameRate: Int = 24,
    reduceMotion: Boolean = false,
) {
    var targetPalette by remember { mutableStateOf(ArtworkDynamicPalette.Fallback) }
    val frameRate = backgroundFrameRate.coerceIn(15, 60)
    val meshWidth = when (quality) {
        MeloXLyricsRenderingQuality.Low -> 24
        MeloXLyricsRenderingQuality.Balanced -> 32
        MeloXLyricsRenderingQuality.High -> 48
    }
    val meshHeight = meshWidth * 23 / 10
    val meshBitmaps = remember(meshWidth, meshHeight) {
        List(2) { allocMeshBitmap(meshWidth, meshHeight) }
    }
    val meshImages = remember(meshBitmaps) { meshBitmaps.map { it.asComposeImageBitmap() } }
    var meshImage by remember(meshImages) { mutableStateOf(meshImages.first()) }
    val currentColors = remember(meshWidth, meshHeight) {
        MutableList(9) { index ->
            ArtworkDynamicPalette.Fallback.cells.getOrElse(index) { ArtworkDynamicPalette.Fallback.average }
        }
    }
    val currentAverage = remember(meshWidth, meshHeight) { arrayOf(ArtworkDynamicPalette.Fallback.average) }
    val phase = remember(meshWidth, meshHeight) { floatArrayOf(0f) }

    DisposableEffect(meshBitmaps) {
        onDispose { meshBitmaps.forEach { bitmap -> bitmap.close() } }
    }

    LaunchedEffect(artworkUrl) {
        targetPalette = ArtworkDynamicPaletteProvider.paletteFor(artworkUrl)
    }

    LaunchedEffect(isPlaying, artworkUrl, mediaId, quality, frameRate, meshBitmaps, targetPalette, reduceMotion) {
        val requestedFrameDelayMs = (1_000L / frameRate.toLong()).coerceAtLeast(1L)
        val frameDelayMs = when (quality) {
            MeloXLyricsRenderingQuality.Low -> requestedFrameDelayMs.coerceAtLeast(50L)
            MeloXLyricsRenderingQuality.Balanced -> requestedFrameDelayMs.coerceAtLeast(33L)
            MeloXLyricsRenderingQuality.High -> requestedFrameDelayMs.coerceAtLeast(16L)
        }
        var energy = .18f
        var beatPulse = 0f
        var downbeatPulse = 0f
        var writeIndex = 1
        val pixels = IntArray(meshWidth * meshHeight)
        val centersX = FloatArray(currentColors.size)
        val centersY = FloatArray(currentColors.size)
        if (reduceMotion || !isPlaying) {
            currentColors.indices.forEach { index ->
                currentColors[index] = targetPalette.cells.getOrElse(index) { targetPalette.average }
            }
            currentAverage[0] = targetPalette.average
            withContext(Dispatchers.Default) {
                fillFlowingMeshPixels(
                    pixels = pixels,
                    meshWidth = meshWidth,
                    meshHeight = meshHeight,
                    colors = currentColors,
                    average = currentAverage[0],
                    phase = phase[0],
                    energy = energy,
                    beatPulse = beatPulse,
                    downbeatPulse = downbeatPulse,
                    centersX = centersX,
                    centersY = centersY,
                )
                installArgbPixels(meshBitmaps[writeIndex], pixels, meshWidth, meshHeight)
            }
            meshImage = meshImages[writeIndex]
            awaitCancellation()
        }
        var lastRenderNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (lastRenderNanos != 0L && frameNanos - lastRenderNanos < frameDelayMs * 1_000_000L) continue
            val elapsedMs = if (lastRenderNanos == 0L) frameDelayMs.toFloat()
            else ((frameNanos - lastRenderNanos) / 1_000_000f).coerceIn(1f, 100f)
            lastRenderNanos = frameNanos
            val sample = MeloXAudioReactiveRuntime.sample(mediaId)
            energy += (sample.energy - energy) * .18f
            beatPulse += (sample.beat - beatPulse) * .32f
            downbeatPulse += (sample.downbeat - downbeatPulse) * .24f
            val motion = .026f + energy.coerceIn(0f, 1f) * .038f + beatPulse * .016f
            phase[0] = (phase[0] + motion * elapsedMs / (1_000f / 60f)) % (Math.PI.toFloat() * 2f)
            val paletteBlend = (elapsedMs / 800f).coerceIn(.02f, .18f)
            currentColors.indices.forEach { index ->
                currentColors[index] = lerpColor(
                    currentColors[index],
                    targetPalette.cells.getOrElse(index) { targetPalette.average },
                    paletteBlend,
                )
            }
            currentAverage[0] = lerpColor(currentAverage[0], targetPalette.average, paletteBlend)
            val bitmap = meshBitmaps[writeIndex]
            withContext(Dispatchers.Default) {
                fillFlowingMeshPixels(
                    pixels = pixels,
                    meshWidth = meshWidth,
                    meshHeight = meshHeight,
                    colors = currentColors,
                    average = currentAverage[0],
                    phase = phase[0],
                    energy = energy,
                    beatPulse = beatPulse,
                    downbeatPulse = downbeatPulse,
                    centersX = centersX,
                    centersY = centersY,
                )
                installArgbPixels(bitmap, pixels, meshWidth, meshHeight)
            }
            meshImage = meshImages[writeIndex]
            writeIndex = 1 - writeIndex
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawImage(
            image = meshImage,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = 0.04f),
                    0.52f to Color.Black.copy(alpha = 0.10f),
                    1f to Color.Black.copy(alpha = 0.48f),
                ),
            ),
        )
    }
}

private fun allocMeshBitmap(width: Int, height: Int): Bitmap {
    val bitmap = Bitmap()
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    check(bitmap.allocPixels(info)) { "Unable to allocate flowing mesh" }
    return bitmap
}

private fun installArgbPixels(bitmap: Bitmap, pixels: IntArray, width: Int, height: Int) {
    val bytes = ByteArray(width * height * 4)
    var offset = 0
    for (pixel in pixels) {
        bytes[offset] = (pixel and 0xFF).toByte()
        bytes[offset + 1] = ((pixel ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((pixel ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((pixel ushr 24) and 0xFF).toByte()
        offset += 4
    }
    check(bitmap.installPixels(bytes)) { "Unable to install flowing mesh pixels" }
}

private fun lerpColor(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)

private fun fillFlowingMeshPixels(
    pixels: IntArray,
    meshWidth: Int,
    meshHeight: Int,
    colors: List<Color>,
    average: Color,
    phase: Float,
    energy: Float,
    beatPulse: Float,
    downbeatPulse: Float,
    centersX: FloatArray,
    centersY: FloatArray,
) {
    val radiusNormalized = (0.58f + energy.coerceIn(0f, 1f) * .08f + beatPulse * .035f)
        .coerceAtLeast(.01f)
    for (index in colors.indices) {
        val row = index / 3
        val column = index % 3
        val baseX = when (column) { 0 -> .08f; 1 -> .50f; else -> .92f }
        val baseY = when (row) { 0 -> .10f; 1 -> .50f; else -> .90f }
        val localPhase = phase + index * .71f
        val displacement = .052f + energy.coerceIn(0f, 1f) * .045f
        centersX[index] = baseX + sin(localPhase) * displacement
        centersY[index] = baseY + cos(localPhase * .83f) * displacement * .87f
    }
    val maxDimension = maxOf(meshWidth, meshHeight).toFloat()
    val widthScale = meshWidth / maxDimension
    val heightScale = meshHeight / maxDimension
    val baseWeight = .22f
    val pulseGain = 1f + energy.coerceIn(0f, 1f) * .10f + beatPulse * .07f
    val downbeatShade = 1f - (downbeatPulse * .16f).coerceIn(0f, .16f)
    var pixelIndex = 0
    for (yIndex in 0 until meshHeight) {
        val v = yIndex.toFloat() / (meshHeight - 1).coerceAtLeast(1).toFloat()
        for (xIndex in 0 until meshWidth) {
            val u = xIndex.toFloat() / (meshWidth - 1).coerceAtLeast(1).toFloat()
            var totalWeight = baseWeight
            var red = average.red * baseWeight
            var green = average.green * baseWeight
            var blue = average.blue * baseWeight
            for (colorIndex in colors.indices) {
                val dx = (u - centersX[colorIndex]) * widthScale / radiusNormalized
                val dy = (v - centersY[colorIndex]) * heightScale / radiusNormalized
                val distanceSquared = dx * dx + dy * dy
                val falloff = 1f / (1f + distanceSquared * 4.5f)
                val weight = falloff * falloff
                val color = colors[colorIndex]
                totalWeight += weight
                red += color.red * weight
                green += color.green * weight
                blue += color.blue * weight
            }
            val r = ((red / totalWeight) * pulseGain * downbeatShade).coerceIn(0f, 1f)
            val g = ((green / totalWeight) * pulseGain * downbeatShade).coerceIn(0f, 1f)
            val b = ((blue / totalWeight) * pulseGain * downbeatShade).coerceIn(0f, 1f)
            pixels[pixelIndex++] = (0xFF shl 24) or
                ((r * 255f).toInt() shl 16) or
                ((g * 255f).toInt() shl 8) or
                (b * 255f).toInt()
        }
    }
}

private val desktopArtworkCache = object : LinkedHashMap<String, ImageBitmap>(32, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 32
}

@Composable
private fun DesktopArtworkImage(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    var bitmap by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let { synchronized(desktopArtworkCache) { desktopArtworkCache[it] } })
    }
    LaunchedEffect(artworkUrl) {
        val source = artworkUrl?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (synchronized(desktopArtworkCache) { desktopArtworkCache[source] } != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching { decodeArtworkImage(source) }.getOrNull()
        } ?: return@LaunchedEffect
        synchronized(desktopArtworkCache) { desktopArtworkCache[source] = loaded }
        bitmap = loaded
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = modifier,
        )
    }
}

private fun decodeArtworkImage(source: String): ImageBitmap {
    val bytes = when (runCatching { URI(source) }.getOrNull()?.scheme?.lowercase()) {
        "http", "https" -> {
            val request = Request.Builder()
                .url(source)
                .header("User-Agent", "MeloX-Desktop/0.1")
                .build()
            MeloXHttpClient.shared.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Artwork HTTP ${response.code}")
                response.body?.bytes() ?: error("Artwork HTTP empty body")
            }
        }
        "file" -> File(URI(source)).readBytes()
        else -> File(source).readBytes()
    }
    val image = Image.makeFromEncoded(bytes)
    return image.toComposeImageBitmap().also { image.close() }
}
