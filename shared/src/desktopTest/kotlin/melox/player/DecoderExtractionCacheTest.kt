package melox.player

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.*

class DecoderExtractionCacheTest {
    @Test fun validLegacyBinaryIsContentVerifiedAndReusedWithoutWritingAnotherCopy() {
        val root = Files.createTempDirectory("melox-decoder-cache").toFile()
        val oldTmp = System.getProperty("java.io.tmpdir")
        try {
            val sources = File(root, "sources").apply { mkdirs() }
            val bytes = "validation decoder bytes".toByteArray()
            File(sources, "fixture-decoder").writeBytes(bytes)
            val legacy = File(root, "melox-fixture-decoder").apply { writeBytes(bytes); setExecutable(true) }
            System.setProperty("java.io.tmpdir", root.path)
            URLClassLoader(arrayOf(sources.toURI().toURL()), null).use { loader ->
                assertEquals(legacy.canonicalFile, FfmpegLocator.extract("fixture-decoder", loader)?.canonicalFile)
                val filesBefore = root.listFiles()!!.map { it.name }.sorted()
                repeat(3) { assertEquals(legacy.canonicalFile, FfmpegLocator.extract("fixture-decoder", loader)?.canonicalFile) }
                assertEquals(filesBefore, root.listFiles()!!.map { it.name }.sorted())
                assertFalse(root.listFiles()!!.any { it.name.endsWith(".part") })
            }
        } finally { System.setProperty("java.io.tmpdir", oldTmp); root.deleteRecursively() }
    }

    @Test fun sameLengthStaleLegacyDecoderCannotReplaceThePackagedVersion() {
        val root = Files.createTempDirectory("melox-decoder-version").toFile()
        val oldTmp = System.getProperty("java.io.tmpdir")
        try {
            val sources = File(root, "sources").apply { mkdirs() }
            val expected = "new decoder".toByteArray()
            File(sources, "fixture-decoder").writeBytes(expected)
            val legacy = File(root, "melox-fixture-decoder").apply { writeBytes("old decoder".toByteArray()); setExecutable(true) }
            assertEquals(expected.size.toLong(), legacy.length())
            System.setProperty("java.io.tmpdir", root.path)
            URLClassLoader(arrayOf(sources.toURI().toURL()), null).use { loader ->
                val actual = assertNotNull(FfmpegLocator.extract("fixture-decoder", loader))
                assertNotEquals(legacy.canonicalFile, actual.canonicalFile)
                assertContentEquals(expected, actual.readBytes())
                assertContentEquals("old decoder".toByteArray(), legacy.readBytes())
                assertFalse(root.listFiles()!!.any { it.name.endsWith(".part") })
            }
        } finally { System.setProperty("java.io.tmpdir", oldTmp); root.deleteRecursively() }
    }
}
