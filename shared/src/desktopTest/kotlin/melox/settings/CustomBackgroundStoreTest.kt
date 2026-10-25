package melox.settings

import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class CustomBackgroundStoreTest {
    private val root = Files.createTempDirectory("melox-background-test").toFile()
    private val owned = File(root, "owned")
    private val store = MeloXCustomBackgroundStore(owned)
    @AfterTest fun cleanup() { root.deleteRecursively() }

    private fun image(name: String, width: Int = 32, height: Int = 24): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xCC1122)
        return File(root, name).also { ImageIO.write(image, "png", it); image.flush() }
    }

    @Test fun importedImageIsOwnedBoundedAndIndependentOfTheOriginal() {
        val original = image("原图.png", 2560, 1600)
        val before = original.readBytes()
        val imported = store.importImage(original)
        assertContentEquals(before, original.readBytes())
        assertEquals(owned.canonicalFile, imported.canonicalFile.parentFile)
        val decoded = ImageIO.read(imported)
        assertEquals(1280, decoded.width); assertEquals(800, decoded.height); decoded.flush()
        assertEquals(imported, store.ownedImage(imported.path))
    }

    @Test fun rejectedImportPreservesTheCurrentBackground() {
        val imported = store.importImage(image("valid.png"))
        val invalid = File(root, "not-an-image.png").apply { writeText("invalid") }
        assertFailsWith<java.io.IOException> { store.importImage(invalid) }
        assertTrue(imported.isFile); assertEquals(listOf(imported.name), owned.listFiles()!!.map { it.name })
    }

    @Test fun pruningOnlyRemovesOwnedImagesAndNeverOriginalOrUnrelatedFiles() {
        val original = image("user-original.png")
        val first = store.importImage(original)
        val second = store.importImage(original)
        val other = File(owned, "unrelated.png").apply { writeText("user data") }
        assertNull(store.ownedImage(original.path)); assertNull(store.ownedImage(other.path))
        store.prune(second)
        assertFalse(first.exists()); assertTrue(second.exists()); assertTrue(original.exists()); assertTrue(other.exists())
        store.prune(null)
        assertFalse(second.exists()); assertTrue(original.exists()); assertTrue(other.exists())
    }

    @Test fun oversizedInputsAreRejectedBeforeDecode() {
        val original = File(root, "large.png")
        java.io.RandomAccessFile(original, "rw").use { it.setLength(MeloXCustomBackgroundStore.MaxImportBytes + 1) }
        assertFailsWith<IllegalArgumentException> { store.importImage(original) }
        assertFalse(owned.exists())
    }
}
