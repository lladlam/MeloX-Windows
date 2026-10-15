package melox.ui.artwork

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.SamplingMode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Decode for the physical Crop viewport, never allocating a full JPEG just for a thumbnail.
 * Keep Skia's colour/alpha handling and enough source pixels for both axes at the current DPI.
 * PNG/WebP retain the normal codec decode, then immediately release the oversized bitmap.
 */
internal fun decodeMeloXArtwork(bytes: ByteArray, targetWidth: Int, targetHeight: Int): ImageBitmap {
    require(targetWidth > 0 && targetHeight > 0)
    return Data.makeFromBytes(bytes).use { data ->
        Codec.makeFromData(data).use codecUse@ { codec ->
            val ratio = min(1.0, max(targetWidth.toDouble() / codec.width, targetHeight.toDouble() / codec.height))
            val width = ceil(codec.width * ratio).toInt().coerceAtLeast(1)
            val height = ceil(codec.height * ratio).toInt().coerceAtLeast(1)
            var sample = 1
            if (codec.encodedImageFormat == EncodedImageFormat.JPEG) {
                while (sample < 8 && ceil(codec.width.toDouble() / (sample * 2)) >= width &&
                    ceil(codec.height.toDouble() / (sample * 2)) >= height) sample *= 2
            }
            val decoded = Bitmap()
            try {
                check(decoded.allocPixels(codec.imageInfo.withWidthHeight(
                    ceil(codec.width.toDouble() / sample).toInt(), ceil(codec.height.toDouble() / sample).toInt())))
                codec.readPixels(decoded)
                if (decoded.width == width && decoded.height == height) {
                    decoded.setImmutable()
                    // Ownership passes to the cache/UI lease; do not close this bitmap here.
                    return@codecUse decoded.asComposeImageBitmap()
                }
                val scaled = Bitmap()
                try {
                    check(scaled.allocPixels(decoded.imageInfo.withWidthHeight(width, height)))
                    checkNotNull(decoded.peekPixels()).use { source ->
                        checkNotNull(scaled.peekPixels()).use { destination -> check(source.scalePixels(destination, SamplingMode.LINEAR)) }
                    }
                    scaled.setImmutable()
                    scaled.asComposeImageBitmap()
                } catch (failure: Throwable) {
                    scaled.close()
                    throw failure
                } finally {
                    decoded.close()
                }
            } catch (failure: Throwable) {
                if (!decoded.isClosed) decoded.close()
                throw failure
            }
        }
    }
}

/** Small buckets avoid re-decoding on every resize pixel while respecting physical DPI. */
internal fun artworkSizeBucket(pixels: Int): Int = ((pixels.coerceAtLeast(1) + 63) / 64) * 64
