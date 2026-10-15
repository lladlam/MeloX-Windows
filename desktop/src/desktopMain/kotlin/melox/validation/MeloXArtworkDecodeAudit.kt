package melox.validation

import androidx.compose.ui.graphics.asSkiaBitmap
import melox.ui.artwork.decodeMeloXArtwork
import org.jetbrains.skia.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Generated codec fixtures only; production decoder, no network/provider substitutes. */
fun main(args: Array<String>) {
    val samples = JSONArray()
    for ((width, height) in listOf(3072 to 2048, 3011 to 2033, 256 to 256, 1024 to 3072)) {
        Bitmap().use { original ->
            check(original.allocN32Pixels(width, height))
            original.erase(0xff3c80b4.toInt())
            Image.makeFromBitmap(original).use { image ->
                for (format in listOf(EncodedImageFormat.JPEG, EncodedImageFormat.PNG, EncodedImageFormat.WEBP)) {
                    image.encodeToData(format, 100)!!.use { encoded ->
                        for ((targetW, targetH) in listOf(64 to 64, 427 to 427, 384 to 128, width to height)) {
                            decodeMeloXArtwork(encoded.bytes, targetW, targetH).asSkiaBitmap().use { decoded ->
                                check(decoded.width <= width && decoded.height <= height)
                                check(decoded.width >= minOf(width, targetW) && decoded.height >= minOf(height, targetH))
                                check(kotlin.math.abs(decoded.width.toDouble() / decoded.height - width.toDouble() / height) < .03)
                                val color = decoded.getColor(decoded.width / 2, decoded.height / 2)
                                for (shift in listOf(0, 8, 16)) check(kotlin.math.abs(((color ushr shift) and 255) - ((0xff3c80b4.toInt() ushr shift) and 255)) <= 3)
                                samples.put(JSONObject().put("format", format.name).put("source", "${width}x$height")
                                    .put("viewport", "${targetW}x$targetH").put("decoded", "${decoded.width}x${decoded.height}")
                                    .put("original_pixel_bytes", width.toLong() * height * 4)
                                    .put("retained_pixel_bytes", decoded.computeByteSize()))
                            }
                        }
                    }
                }
            }
        }
    }
    // Alpha must survive the same-size fast path and resized PNG path.
    Bitmap().use { bitmap ->
        check(bitmap.allocN32Pixels(256, 256)); bitmap.erase(0x80336699.toInt())
        Image.makeFromBitmap(bitmap).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
            for (size in listOf(64, 256)) decodeMeloXArtwork(data.bytes, size, size).asSkiaBitmap().use { decoded ->
                check(kotlin.math.abs((decoded.getColor(0, 0) ushr 24) - 128) <= 1)
            }
        } }
    }
    check(runCatching { decodeMeloXArtwork(byteArrayOf(1, 2, 3), 64, 64) }.isFailure)
    val output = File(args.single()).apply { parentFile.mkdirs() }
    output.writeText(JSONObject().put("status", "PASSED").put("validation_generated_images", true)
        .put("forced_gc", false).put("cases", samples).put("alpha_cases", 2).put("invalid_input_cases", 1).toString(2) + "\n")
    println("PASS: ${samples.length()} codec/viewport cases, alpha and invalid-input cleanup")
}
