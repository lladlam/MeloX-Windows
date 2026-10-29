package melox.autosync

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import melox.network.MeloXGitHubRouting
import melox.network.executeOwned
import melox.platform.meloXDataDir
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.GZIPInputStream

/** Downloaded separately from the acoustic model; never modifies files it does not own. */
internal class AutoSyncJapaneseDictionary(
    private val root: File = File(meloXDataDir(), "autosync/ja-dictionary"),
    private val routing: MeloXGitHubRouting = MeloXGitHubRouting(),
    private val spec: Spec = RELEASE,
) {
    internal data class Spec(val url: String, val archiveBytes: Long, val sha256: String, val minimumBytes: Long)
    private val live get() = File(root, "installed")

    /** Cheap availability check; [withInstalled] checks contents before native loading. */
    fun installed(): Boolean = manifest(live) != null

    suspend fun <T> withInstalled(block: suspend (File) -> T): T? = withContext(Dispatchers.IO) {
        mutation.withLock {
            val entries = manifest(live) ?: return@withLock null
            for ((name, expected) in entries) {
                currentCoroutineContext().ensureActive()
                if (digest(File(live, name)) != expected.second) return@withLock null
            }
            block(live)
        }
    }

    suspend fun install(progress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        mutation.withLock {
            check(root.isDirectory || root.mkdirs()) { "Cannot create dictionary directory" }
            check(root.usableSpace > spec.archiveBytes + spec.minimumBytes * 2 + 64L * 1024 * 1024) { "Insufficient disk space for dictionary" }
            val stage = Files.createTempDirectory(root.toPath(), ".install-").toFile()
            try {
                val archive = File(stage, "archive.tar.gz")
                download(archive, progress)
                val extracted = File(stage, "contents").apply { check(mkdir()) }
                val entries = extract(archive, extracted)
                val marker = JSONObject().put("archive", spec.sha256).put("files", JSONArray().apply {
                    entries.forEach { (name, info) -> put(JSONObject().put("name", name).put("bytes", info.first).put("sha256", info.second)) }
                })
                File(extracted, MARKER).writeText(marker.toString(), Charsets.UTF_8)
                currentCoroutineContext().ensureActive()
                publish(extracted)
                progress(1f)
            } finally { stage.deleteRecursively() } // This invocation created the staging directory.
        }
    }

    suspend fun remove(): Boolean = withContext(Dispatchers.IO) {
        mutation.withLock {
            val entries = manifest(live, requireComplete = false) ?: return@withLock false
            entries.keys.forEach { name -> Files.deleteIfExists(File(live, name).toPath()) }
            Files.deleteIfExists(File(live, MARKER).toPath())
            try { Files.deleteIfExists(live.toPath()) } catch (_: DirectoryNotEmptyException) { }
            true
        }
    }

    private suspend fun download(target: File, progress: (Float) -> Unit) {
        val context = currentCoroutineContext()
        var last: Exception? = null
        for (route in routing.candidates()) {
            try {
                val request = Request.Builder().url(routing.routedUrl(route, spec.url)).header("User-Agent", "MeloX-Desktop").build()
                val client = routing.client(route).newBuilder().cache(null).connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS).callTimeout(600, TimeUnit.SECONDS).build()
                client.executeOwned(request, context.job) { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val declared = response.body.contentLength()
                    require(declared < 0 || declared == spec.archiveBytes) { "Dictionary archive size mismatch" }
                    val hash = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    response.body.byteStream().buffered().use { input -> target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            context.ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            count += size
                            require(count <= spec.archiveBytes) { "Dictionary archive too large" }
                            hash.update(buffer, 0, size); output.write(buffer, 0, size)
                            progress((count.toDouble() / spec.archiveBytes * .9).toFloat())
                        }
                    } }
                    require(count == spec.archiveBytes && hash.digest().hex() == spec.sha256) { "Dictionary checksum mismatch" }
                }
                return
            } catch (failure: Exception) { context.ensureActive(); last = failure }
        }
        throw last ?: IOException("No dictionary download route")
    }

    private suspend fun extract(archive: File, destination: File): Map<String, Pair<Long, String>> {
        val found = linkedMapOf<String, Pair<Long, String>>()
        var total = 0L
        GZIPInputStream(archive.inputStream().buffered()).use { gzip -> TarArchiveInputStream(gzip).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextEntry ?: break
                if (entry.isDirectory && entry.name.trimEnd('/') == ARCHIVE_ROOT) continue
                require(entry.isFile && !entry.isSymbolicLink && !entry.isLink && tar.canReadEntryData(entry)) { "Invalid dictionary archive entry" }
                val name = entry.name.removePrefix("$ARCHIVE_ROOT/")
                require(entry.name == "$ARCHIVE_ROOT/$name" && name in FILES && name !in found) { "Unexpected dictionary archive entry" }
                require(entry.size in 1..MAX_EXTRACTED && total + entry.size <= MAX_EXTRACTED) { "Dictionary archive too large" }
                val hash = MessageDigest.getInstance("SHA-256")
                var count = 0L
                File(destination, name).outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = tar.read(buffer)
                        if (n < 0) break
                        count += n; require(count <= entry.size)
                        hash.update(buffer, 0, n); output.write(buffer, 0, n)
                    }
                }
                require(count == entry.size)
                total += count
                found[name] = count to hash.digest().hex()
            }
        } }
        require(found.keys == FILES && total >= spec.minimumBytes) { "Dictionary archive is incomplete" }
        return found
    }

    private fun manifest(directory: File, requireComplete: Boolean = true): Map<String, Pair<Long, String>>? = runCatching {
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
        val marker = File(directory, MARKER)
        require(marker.isFile && !Files.isSymbolicLink(marker.toPath()))
        val data = JSONObject(marker.readText(Charsets.UTF_8))
        require(data.getString("archive") == spec.sha256)
        val items = data.getJSONArray("files")
        require(items.length() == FILES.size)
        val result = (0 until items.length()).associate { index ->
            val item = items.getJSONObject(index)
            val name = item.getString("name")
            val bytes = item.getLong("bytes")
            val hash = item.getString("sha256")
            require(name in FILES && bytes in 1..MAX_EXTRACTED && hash.matches(Regex("[0-9a-f]{64}")))
            val file = File(directory, name)
            require(!Files.isSymbolicLink(file.toPath()))
            if (requireComplete) require(file.isFile && file.length() == bytes)
            else require(!file.exists() || file.isFile)
            name to (bytes to hash)
        }
        require(result.keys == FILES)
        result
    }.getOrNull()

    private fun publish(staging: File) {
        val backup = File(root, ".backup-${UUID.randomUUID()}")
        val hadOld = live.exists()
        if (hadOld) {
            val existing = requireNotNull(manifest(live, requireComplete = false)) { "Existing dictionary is not a managed installation" }
            require(live.listFiles()?.all { it.name in existing.keys + MARKER && it.isFile && !Files.isSymbolicLink(it.toPath()) } == true) {
                "Existing dictionary contains unmanaged files"
            }
            move(live, backup)
        }
        try { move(staging, live) }
        catch (failure: Exception) {
            if (hadOld) try { move(backup, live) } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
        if (hadOld) backup.deleteRecursively() // Validated as exclusively managed before replacement.
    }

    companion object {
        private val mutation = Mutex()
        private const val ARCHIVE_ROOT = "open_jtalk_dic_utf_8-1.11"
        private const val MARKER = "install.json"
        private const val MAX_EXTRACTED = 160L * 1024 * 1024
        private val FILES = setOf("matrix.bin", "right-id.def", "pos-id.def", "COPYING", "rewrite.def", "left-id.def", "sys.dic", "char.bin", "unk.dic")
        val RELEASE = Spec("https://github.com/r9y9/open_jtalk/releases/download/v1.11.1/open_jtalk_dic_utf_8-1.11.tar.gz",
            23_646_843L, "fe6ba0e43542cef98339abdffd903e062008ea170b04e7e2a35da805902f382a", 53_650_000L)
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
        private suspend fun digest(file: File): String {
            val context = currentCoroutineContext()
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    context.ensureActive()
                    val size = input.read(buffer)
                    if (size < 0) break
                    hash.update(buffer, 0, size)
                }
            }
            return hash.digest().hex()
        }
        private fun move(from: File, to: File) {
            try { Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(from.toPath(), to.toPath()) }
        }
    }
}
