package melox.ui.player

import androidx.compose.runtime.collectAsState

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import java.io.File
import java.net.URI
import java.util.LinkedHashMap
import kotlin.math.cos
import kotlin.math.sin
import melox.settings.MeloXPlayerBackgroundMode
import melox.settings.playerShowsLyricsArtworkBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import melox.network.MeloXHttpClient
import melox.playback.MeloXAudioReactiveRuntime
import melox.ui.artwork.rememberMeloXArtworkBitmap
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
    fun combinedWith(other: ArtworkDynamicPalette): ArtworkDynamicPalette {
        if (this === Fallback) return other
        if (other === Fallback || this == other) return this
        return ArtworkDynamicPalette(
            cells = List(9) { index ->
                androidx.compose.ui.graphics.lerp(cells.getOrElse(index) { average }, other.cells.getOrElse(index) { other.average }, .5f)
            },
            average = androidx.compose.ui.graphics.lerp(average, other.average, .5f),
        )
    }
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
        val session = melox.provider.jellyfin.JellyfinSessionStore.read()
        val key = melox.provider.jellyfin.JellyfinArtworkAccess.cacheKey(source, session)
        cached(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            cached(key)?.let { return@withContext it }
            val palette = runCatching {
                val decoded = decodeArtwork(source, session)
                try {
                    if (decoded.width == TARGET_SIZE && decoded.height == TARGET_SIZE) {
                        makePalette(decoded)
                    } else {
                        scaleNearest(decoded, TARGET_SIZE, TARGET_SIZE).use(::makePalette)
                    }
                } finally {
                    decoded.close()
                }
            }.getOrNull()

            if (palette != null) synchronized(cache) { cache[key] = palette }
            palette ?: ArtworkDynamicPalette.Fallback
        }
    }

    private fun cached(source: String): ArtworkDynamicPalette? = synchronized(cache) { cache[source] }

    fun clearMemoryCache() = synchronized(cache) { cache.clear() }

    private fun decodeArtwork(source: String, session: melox.provider.jellyfin.JellyfinSession): Bitmap {
        val uri = runCatching { URI(source) }.getOrNull()
        return when (uri?.scheme?.lowercase()) {
            "http", "https" -> decodeNetworkArtwork(source, session)
            "file" -> decodeFileArtwork(File(uri).absolutePath)
            null -> decodeFileArtwork(source)
            else -> error("Unsupported artwork URI scheme: ${uri.scheme}")
        }
    }

    private fun decodeNetworkArtwork(source: String, session: melox.provider.jellyfin.JellyfinSession): Bitmap {
        val request = Request.Builder()
            .url(optimizedArtworkUrl(source))
            .header("User-Agent", "MeloX-Desktop/0.1")
            .apply { melox.provider.jellyfin.JellyfinArtworkAccess.requestHeaders(source, session).forEach(::header) }
            .build()
        return melox.provider.jellyfin.JellyfinArtworkAccess.clientFor(source, http, session).newCall(request).execute().use { response ->
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
        return Image.makeFromEncoded(bytes).use { image ->
            val bitmap = Bitmap()
            try {
                val info = ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
                check(bitmap.allocPixels(info)) { "Unable to allocate artwork pixels" }
                check(image.readPixels(bitmap)) { "Unable to read artwork pixels" }
                bitmap
            } catch (error: Throwable) {
                bitmap.close()
                throw error
            }
        }
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
    val latestReduceMotion by rememberUpdatedState(reduceMotion)
    val elapsedWhilePlayingMs = remember(artworkUrl) { mutableLongStateOf(0L) }
    val settleUntilMs = remember(artworkUrl) { System.currentTimeMillis() + 1_600L }
    LaunchedEffect(planeCount, artworkUrl, frameRate, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        var previousFrameAt = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            if (latestReduceMotion) {
                delay(500L)
                previousFrameAt = System.currentTimeMillis()
                continue
            }
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
internal fun MeloXMeiMeshBackdrop(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val jellyfinRevision by melox.provider.jellyfin.JellyfinSessionStore.revision.collectAsState()
    var palette by remember(jellyfinRevision) { mutableStateOf(ArtworkDynamicPalette.Fallback) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(artworkUrl, jellyfinRevision) {
        palette = ArtworkDynamicPaletteProvider.paletteFor(artworkUrl)
    }
    LaunchedEffect(reduceMotion, isPlaying) {
        if (reduceMotion || !isPlaying) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                phase.floatValue = (phase.floatValue + 0.004f) % 1f
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = if (reduceMotion) 0f else phase.floatValue
        drawRect(palette.average)
        val columns = 6
        val rows = 10
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val color = palette.cells[(x + y) % palette.cells.size]
                val drift = if (reduceMotion) 0f else sin((t + x * 0.11f + y * 0.07f) * 6.28318f) * 10f
                drawRect(
                    color = color.copy(alpha = 0.62f),
                    topLeft = Offset(x * cellWidth + drift, y * cellHeight),
                    size = Size(cellWidth * 0.9f, cellHeight * 0.9f),
                )
            }
        }
    }
}

@Composable
internal fun MeloXPlayerBackdrop(
    mode: MeloXPlayerBackgroundMode,
    lyricsPage: Boolean,
    artworkUrl: String?,
    isPlaying: Boolean,
    mediaId: String?,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    mixOutgoingArtworkUrl: String? = null,
    mixIncomingArtworkUrl: String? = null,
) {
    when {
        mode == MeloXPlayerBackgroundMode.CustomImage -> {
            val path = melox.settings.MeloXSettingsRuntime.customBackgroundPath
            val custom by androidx.compose.runtime.produceState<String?>(null, path) {
                value = withContext(Dispatchers.IO) {
                    runCatching { melox.settings.MeloXCustomBackgroundStore().ownedImage(path)?.toURI()?.toString() }.getOrNull()
                }
            }
            MeloXBlurredArtworkBackdrop(custom ?: artworkUrl, modifier)
        }
        playerShowsLyricsArtworkBackdrop(mode, lyricsPage) -> MeloXLyricsArtworkBackdrop(
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            modifier = modifier,
            reduceMotion = reduceMotion,
        )
        mode == MeloXPlayerBackgroundMode.MeiMesh -> MeloXMeiMeshBackdrop(
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            modifier = modifier,
            reduceMotion = reduceMotion,
        )
        mode == MeloXPlayerBackgroundMode.FlowingLight -> MeloXFlowingLightBackdrop(
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            mediaId = mediaId,
            modifier = modifier,
            reduceMotion = reduceMotion,
            mixOutgoingArtworkUrl = mixOutgoingArtworkUrl,
            mixIncomingArtworkUrl = mixIncomingArtworkUrl,
        )
        else -> MeloXBlurredArtworkBackdrop(artworkUrl, modifier)
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
    mixOutgoingArtworkUrl: String? = null,
    mixIncomingArtworkUrl: String? = null,
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
    val writeIndex = remember(meshBitmaps) { intArrayOf(1) }

    DisposableEffect(meshBitmaps) {
        onDispose { meshBitmaps.forEach { bitmap -> bitmap.close() } }
    }

    val jellyfinRevision by melox.provider.jellyfin.JellyfinSessionStore.revision.collectAsState()
    LaunchedEffect(artworkUrl, mixOutgoingArtworkUrl, mixIncomingArtworkUrl, jellyfinRevision) {
        val covers = listOfNotNull(mixOutgoingArtworkUrl ?: artworkUrl, mixIncomingArtworkUrl).filter(String::isNotBlank).distinct()
        val palettes = coroutineScope { covers.map { cover -> async { ArtworkDynamicPaletteProvider.paletteFor(cover) } }.awaitAll() }
        targetPalette = palettes.reduceOrNull(ArtworkDynamicPalette::combinedWith) ?: ArtworkDynamicPalette.Fallback
    }

    val controls = melox.settings.MeloXSettingsRuntime
    val speed = controls.flowingLightSpeed
    val saturation = controls.flowingLightSaturation
    val brightness = controls.flowingLightBrightness
    LaunchedEffect(isPlaying, artworkUrl, mediaId, quality, frameRate, meshBitmaps, targetPalette, reduceMotion, speed, saturation, brightness) {
        val requestedFrameDelayMs = (1_000L / frameRate.toLong()).coerceAtLeast(1L)
        val frameDelayMs = when (quality) {
            MeloXLyricsRenderingQuality.Low -> requestedFrameDelayMs.coerceAtLeast(50L)
            MeloXLyricsRenderingQuality.Balanced -> requestedFrameDelayMs.coerceAtLeast(33L)
            MeloXLyricsRenderingQuality.High -> requestedFrameDelayMs.coerceAtLeast(16L)
        }
        var energy = .18f
        var beatPulse = 0f
        val pixels = IntArray(meshWidth * meshHeight)
        val bytes = ByteArray(pixels.size * 4)
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
                    reactive = false,
                    centersX = centersX,
                    centersY = centersY,
                    saturation = saturation, brightness = brightness,
                )
            }
            // Only CPU arrays cross dispatchers. Installing/disposing native pixels
            // stays on the composition thread, so leaving the page cannot race a write.
            installArgbPixels(meshBitmaps[writeIndex[0]], pixels, bytes)
            meshImage = meshImages[writeIndex[0]]
            writeIndex[0] = 1 - writeIndex[0]
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
            val reactive = sample.hasAnalysis
            val motion = if (reactive) .021f + energy.coerceIn(0f, 1f) * .030f + beatPulse * .013f else .032f
            phase[0] = (phase[0] + motion * speed.coerceIn(.25f, 2f) * elapsedMs / (1_000f / 60f)) % FlowPhasePeriod
            val paletteBlend = (elapsedMs / 280f).coerceIn(.08f, .45f)
            currentColors.indices.forEach { index ->
                currentColors[index] = lerpColor(
                    currentColors[index],
                    targetPalette.cells.getOrElse(index) { targetPalette.average },
                    paletteBlend,
                )
            }
            currentAverage[0] = lerpColor(currentAverage[0], targetPalette.average, paletteBlend)
            val bitmap = meshBitmaps[writeIndex[0]]
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
                    reactive = reactive,
                    centersX = centersX,
                    centersY = centersY,
                    saturation = saturation, brightness = brightness,
                )
            }
            installArgbPixels(bitmap, pixels, bytes)
            meshImage = meshImages[writeIndex[0]]
            writeIndex[0] = 1 - writeIndex[0]
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

private fun installArgbPixels(bitmap: Bitmap, pixels: IntArray, bytes: ByteArray) {
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

// All temporal frequencies below are quarter-turn multiples: wrapping at 8π
// is seamless. This is the official v0.6.5 field, not a brightness pulse.
internal const val FlowPhasePeriod = 25.132742f
private val RoamFrequencyX = floatArrayOf(.50f, .25f, .75f, .50f, .25f, .75f, .50f, .25f, .75f)
private val RoamFrequencyY = floatArrayOf(.25f, .75f, .50f, .75f, .50f, .25f, .50f, .25f, .75f)

internal fun fillFlowingMeshPixels(
    pixels: IntArray,
    meshWidth: Int,
    meshHeight: Int,
    colors: List<Color>,
    average: Color,
    phase: Float,
    energy: Float,
    beatPulse: Float,
    reactive: Boolean,
    centersX: FloatArray,
    centersY: FloatArray,
    saturation: Float = 1f,
    brightness: Float = 1f,
) {
    val energyDrive = if (reactive) energy.coerceIn(0f, 1f) else 0f
    val beatDrive = if (reactive) beatPulse else 0f
    val radiusNormalized = (0.45f + energyDrive * .07f + beatDrive * .03f)
        .coerceAtLeast(.01f)
    for (index in colors.indices) {
        centersX[index] = .5f + .3f * sin(phase * RoamFrequencyX[index] + index * 1.7f)
        centersY[index] = .5f + .3f * cos(phase * RoamFrequencyY[index] + index * .9f)
    }
    val maxDimension = maxOf(meshWidth, meshHeight).toFloat()
    val widthScale = meshWidth / maxDimension
    val heightScale = meshHeight / maxDimension
    val baseWeight = .16f
    val warp = .15f + energyDrive * .05f + beatDrive * .02f
    val t = phase
    var pixelIndex = 0
    for (yIndex in 0 until meshHeight) {
        val v = yIndex.toFloat() / (meshHeight - 1).coerceAtLeast(1).toFloat()
        for (xIndex in 0 until meshWidth) {
            val u = xIndex.toFloat() / (meshWidth - 1).coerceAtLeast(1).toFloat()
            val warpX = sin(v * 3.1f + t) * cos(u * 2.3f - t * .75f) +
                .5f * sin((u + v) * 4.9f - t * 1.25f)
            val warpY = cos(u * 2.9f - t) * sin(v * 2.6f + t * .5f) +
                .5f * cos((u - v) * 5.4f + t)
            var sampleU = u + warp * warpX
            var sampleV = v + warp * warpY
            sampleU += warp * .28f * sin(sampleV * 6.7f - t * 1.5f)
            sampleV += warp * .28f * cos(sampleU * 7.1f + t * 1.5f)
            var totalWeight = baseWeight
            var red = average.red * baseWeight
            var green = average.green * baseWeight
            var blue = average.blue * baseWeight
            for (colorIndex in colors.indices) {
                val dx = (sampleU - centersX[colorIndex]) * widthScale / radiusNormalized
                val dy = (sampleV - centersY[colorIndex]) * heightScale / radiusNormalized
                val distanceSquared = dx * dx + dy * dy
                val falloff = 1f / (1f + distanceSquared * 4.5f)
                val weight = falloff * falloff
                val color = colors[colorIndex]
                totalWeight += weight
                red += color.red * weight
                green += color.green * weight
                blue += color.blue * weight
            }
            var r = red / totalWeight
            var g = green / totalWeight
            var b = blue / totalWeight
            if (saturation != 1f) {
                val luminance = r * .2126f + g * .7152f + b * .0722f
                r = luminance + (r - luminance) * saturation
                g = luminance + (g - luminance) * saturation
                b = luminance + (b - luminance) * saturation
            }
            r = (r * brightness).coerceIn(0f, 1f)
            g = (g * brightness).coerceIn(0f, 1f)
            b = (b * brightness).coerceIn(0f, 1f)
            pixels[pixelIndex++] = (0xFF shl 24) or
                ((r * 255f).toInt() shl 16) or
                ((g * 255f).toInt() shl 8) or
                (b * 255f).toInt()
        }
    }
}

@Composable
private fun DesktopArtworkImage(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    val revision by melox.provider.jellyfin.JellyfinSessionStore.revision.collectAsState()
    val session = remember(revision) { melox.provider.jellyfin.JellyfinSessionStore.read() }
    val key = remember(artworkUrl, session) { artworkUrl?.takeIf(String::isNotBlank)?.let { melox.provider.jellyfin.JellyfinArtworkAccess.cacheKey(it, session) } }
    val bitmap = rememberMeloXArtworkBitmap(key) {
        runCatching { decodeArtworkImage(checkNotNull(artworkUrl), session) }.getOrNull()
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

private fun decodeArtworkImage(source: String, session: melox.provider.jellyfin.JellyfinSession): ImageBitmap {
    val bytes = when (runCatching { URI(source) }.getOrNull()?.scheme?.lowercase()) {
        "http", "https" -> {
            val request = Request.Builder()
                .url(source)
                .header("User-Agent", "MeloX-Desktop/0.1")
                .apply { melox.provider.jellyfin.JellyfinArtworkAccess.requestHeaders(source, session).forEach(::header) }
                .build()
            melox.provider.jellyfin.JellyfinArtworkAccess.clientFor(source, MeloXHttpClient.shared, session).newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Artwork HTTP ${response.code}")
                response.body?.bytes() ?: error("Artwork HTTP empty body")
            }
        }
        "file" -> File(URI(source)).readBytes()
        else -> File(source).readBytes()
    }
    return Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
}
