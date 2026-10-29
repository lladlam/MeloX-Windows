package melox.autosync

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoSyncJapaneseDictionaryTest {
    private val files = listOf("matrix.bin", "right-id.def", "pos-id.def", "COPYING", "rewrite.def", "left-id.def", "sys.dic", "char.bin", "unk.dic")

    @Test fun verifiedInstallRepairAndRemovalPreserveUnmanagedFiles() = runBlocking<Unit> {
        val bytes = archive(files.map { "open_jtalk_dic_utf_8-1.11/$it" to "data-$it" })
        var payload = bytes
        val routing = routing { payload }
        val previous = routing.selectedSource()
        routing.selectSource(MeloXGitHubSource.GitHubDoh)
        val root = Files.createTempDirectory("melox-ja-dictionary-test-").toFile()
        try {
            val repo = AutoSyncJapaneseDictionary(root, routing, spec(bytes))
            repo.install()
            assertTrue(repo.installed())
            assertEquals("data-sys.dic", repo.withInstalled { File(it, "sys.dic").readText() })
            val live = File(root, "installed")
            payload = ByteArray(bytes.size)
            assertFailsWith<IllegalArgumentException> { repo.install() }
            assertEquals("data-sys.dic", repo.withInstalled { File(it, "sys.dic").readText() })
            payload = bytes
            File(live, "sys.dic").writeText("X".repeat("data-sys.dic".length))
            assertNull(repo.withInstalled { true }, "Same-length corruption must not reach the frontend")
            repo.install()
            assertEquals("data-sys.dic", repo.withInstalled { File(it, "sys.dic").readText() })
            assertTrue(File(live, "sys.dic").delete())
            assertFalse(repo.installed())
            repo.install()
            val unrelated = File(live, "personal-notes.txt").apply { writeText("keep") }
            assertFailsWith<IllegalArgumentException> { repo.install() }
            assertEquals("keep", unrelated.readText())
            assertTrue(repo.remove())
            assertEquals("keep", unrelated.readText())
            assertFalse(File(live, "sys.dic").exists())
        } finally { routing.selectSource(previous); root.deleteRecursively() }
    }

    @Test fun rejectsTraversalAndSerializesRemovalWithActiveFrontend() = runBlocking<Unit> {
        val malicious = archive(listOf("open_jtalk_dic_utf_8-1.11/../../outside" to "bad"))
        val bytes = archive(files.map { "open_jtalk_dic_utf_8-1.11/$it" to "data-$it" })
        var payload = malicious
        val routing = routing { payload }
        val previous = routing.selectedSource()
        routing.selectSource(MeloXGitHubSource.GitHubDoh)
        val root = Files.createTempDirectory("melox-ja-dictionary-safety-").toFile()
        try {
            val rejected = AutoSyncJapaneseDictionary(File(root, "bad"), routing, spec(malicious))
            assertFailsWith<IllegalArgumentException> { rejected.install() }
            assertFalse(File(root, "outside").exists())
            payload = bytes
            val repo = AutoSyncJapaneseDictionary(File(root, "good"), routing, spec(bytes))
            repo.install()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val read = async(Dispatchers.IO) { repo.withInstalled { entered.complete(Unit); release.await(); File(it, "sys.dic").readText() } }
            withTimeout(5_000) { entered.await() }
            val remove = async(Dispatchers.IO) { repo.remove() }
            assertEquals("data-sys.dic", File(root, "good/installed/sys.dic").readText())
            release.complete(Unit)
            assertEquals("data-sys.dic", read.await())
            assertTrue(remove.await())
            assertFalse(repo.installed())
        } finally { routing.selectSource(previous); root.deleteRecursively() }
    }

    private fun routing(bytes: () -> ByteArray): MeloXGitHubRouting = MeloXGitHubRouting(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes().toResponseBody()).build()
        }.build())

    private fun spec(bytes: ByteArray) = AutoSyncJapaneseDictionary.Spec(
        "https://github.com/r9y9/open_jtalk/releases/download/v1.11.1/open_jtalk_dic_utf_8-1.11.tar.gz", bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }, 1)

    private fun archive(entries: List<Pair<String, String>>): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip -> TarArchiveOutputStream(gzip).use { tar ->
            entries.forEach { (name, text) ->
                val bytes = text.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes); tar.closeArchiveEntry()
            }
        } }
        return output.toByteArray()
    }
}
