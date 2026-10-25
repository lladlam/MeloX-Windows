package melox.settings

import java.io.File
import java.io.IOException
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.ceil
import melox.platform.meloXDataDir

/** Imports an owned, bounded image; never persists or deletes the user's original. */
class MeloXCustomBackgroundStore(private val directory: File = File(meloXDataDir(), "player-backgrounds")) {
    fun importImage(source: File): File {
        require(source.isFile && source.length() in 1..MaxImportBytes) { "请选择不超过 48 MB 的图片" }
        directory.mkdirs()
        val target = File(directory, "custom-${UUID.randomUUID()}.png")
        try {
            ImageIO.createImageInputStream(source).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: throw IOException("无法解码所选图片")
                try {
                    reader.input = input
                    val width = reader.getWidth(0); val height = reader.getHeight(0)
                    require(width > 0 && height > 0 && width <= 100_000 && height <= 100_000) { "图片尺寸无效" }
                    val sample = ceil(maxOf(width, height) / 1280.0).toInt().coerceAtLeast(1)
                    val param = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                    val image = reader.read(0, param) ?: throw IOException("无法解码所选图片")
                    try { check(ImageIO.write(image, "png", target)) { "无法保存背景图片" } }
                    finally { image.flush() }
                } finally { reader.dispose() }
            }
            return target
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }

    fun ownedImage(path: String): File? = path.takeIf(String::isNotBlank)?.let(::File)?.takeIf { file ->
        !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.isFile &&
            file.canonicalFile.parentFile == directory.canonicalFile && file.name.startsWith("custom-") && file.extension == "png"
    }

    fun prune(keep: File?) {
        directory.listFiles().orEmpty().forEach { file ->
            if (ownedImage(file.path) != null && file.canonicalFile != keep?.canonicalFile) file.delete()
        }
    }

    companion object { const val MaxImportBytes = 48L * 1024 * 1024 }
}
