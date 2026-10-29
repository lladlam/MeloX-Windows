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
import org.json.JSONArray
import org.json.JSONObject

/** Pinned Android AutoSync packs: user-initiated downloads, verified before publication. */
internal class AutoSyncModelRepository(
    private val root: File = File(meloXDataDir(), "autosync/models"),
    private val routing: MeloXGitHubRouting = MeloXGitHubRouting(),
) {
    data class FileSpec(val name: String, val role: String, val url: String, val bytes: Long, val sha256: String)
    data class Pack(
        val language: String,
        val engine: String,
        val sourceModel: String,
        val license: String,
        val accuracyStatus: String,
        val files: List<FileSpec>,
        val precision: FileSpec?,
        internal val descriptor: String,
    ) {
        val required get() = files.filter { it.role != "audit-only" }
        val totalBytes get() = required.sumOf { it.bytes }
    }
    data class Index(val notice: String, val packs: Map<String, Pack>)

    suspend fun fetchIndex(): Index = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val routes = routing.candidates()
        var last: Exception? = null
        for (route in routes) {
            try {
                val request = Request.Builder().url(routing.routedUrl(route, INDEX_URL))
                    .header("User-Agent", "MeloX-Desktop").header("Accept", "application/json").build()
                val bytes = routing.client(route).executeOwned(request, context.job) { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    require(response.body.contentLength() <= MAX_INDEX_BYTES) { "model index too large" }
                    response.body.byteStream().readNBytes(MAX_INDEX_BYTES + 1).also { require(it.size <= MAX_INDEX_BYTES) }
                }
                require(sha256(bytes) == INDEX_SHA256) { "AutoSync model index checksum mismatch" }
                return@withContext parseIndex(bytes.toString(Charsets.UTF_8))
            } catch (error: Exception) {
                context.ensureActive()
                last = error
            }
        }
        throw last ?: IOException("No AutoSync model download route")
    }

    /** The old pack remains usable on download failure or cancellation. */
    suspend fun install(pack: Pack, precision: Boolean = false, onProgress: (Float) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            mutation.withLock {
                require(pack.language in LANGUAGES)
                val activeModel = if (precision) requireNotNull(pack.precision) { "No precision model for ${pack.language}" }
                    else pack.required.single { it.role == "model" }
                val selected = pack.required.filter { it.role != "model" } + activeModel
                require(selected.map { it.name }.distinct().size == selected.size)
                selected.forEach(::validateSpec)
                check(root.isDirectory || root.mkdirs()) { "Cannot create AutoSync directory" }
                val staging = Files.createTempDirectory(root.toPath(), ".${pack.language}-install-").toFile()
                var completed = 0L
                val total = selected.sumOf { it.bytes }.coerceAtLeast(1)
                try {
                    val routes = routing.candidates()
                    for (spec in selected) {
                        currentCoroutineContext().ensureActive()
                        val target = File(staging, spec.name)
                        download(spec, target, routes) { written ->
                            onProgress(((completed + written).coerceAtMost(total).toDouble() / total).toFloat())
                        }
                        completed += spec.bytes
                        onProgress((completed.toDouble() / total).toFloat())
                    }
                    val manifest = JSONObject(pack.descriptor)
                        .put("release_tag", RELEASE_TAG)
                        .put("installed_model", activeModel.name)
                        .put("installed_variant", if (precision) "precision" else "int8")
                        .put("installed_files", JSONArray().apply { selected.forEach { spec -> put(JSONObject()
                            .put("name", spec.name).put("bytes", spec.bytes).put("sha256", spec.sha256)) } })
                    File(staging, "manifest.json").writeText(manifest.toString(), Charsets.UTF_8)
                    currentCoroutineContext().ensureActive()
                    publish(pack.language, staging)
                    onProgress(1f)
                } finally {
                    if (staging.exists()) staging.deleteRecursively() // This invocation created the staging directory.
                }
            }
        }

    fun installed(language: String): InstalledModel? = ownedPack(language)?.takeIf { pack ->
        val files = pack.manifest.getJSONArray("installed_files")
        (0 until files.length()).all { i ->
            val entry = files.getJSONObject(i)
            val file = File(pack.directory, entry.getString("name"))
            !Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() == entry.getLong("bytes")
        }
    }

    // A valid ownership manifest also permits repair/removal of a partially
    // missing pack. Never infer ownership from arbitrary directory contents.
    private fun ownedPack(language: String): InstalledModel? {
        if (language !in LANGUAGES) return null
        val dir = File(root, language)
        return runCatching {
            require(dir.isDirectory && !Files.isSymbolicLink(dir.toPath()))
            val manifestFile = File(dir, "manifest.json")
            require(manifestFile.isFile && !Files.isSymbolicLink(manifestFile.toPath()))
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optString("release_tag") == RELEASE_TAG && manifest.optString("language") == language)
            val name = manifest.getString("installed_model")
            val files = manifest.getJSONArray("installed_files")
            require(files.length() in 1..100)
            val names = (0 until files.length()).map { i ->
                val entry = files.getJSONObject(i)
                entry.getString("name").also { filename ->
                    require(plainName(filename) && filename != "manifest.json")
                    require(entry.getLong("bytes") in 1..1_000_000_000 && entry.getString("sha256").matches(Regex("[0-9a-f]{64}")))
                }
            }
            require(names.size == names.toSet().size && name in names)
            InstalledModel(dir, File(dir, name), manifest)
        }.getOrNull()
    }

    /** Serialize inference with install/removal, so Windows cannot replace an in-use pack. */
    suspend fun <T> withInstalled(language: String, block: suspend (InstalledModel) -> T): T? = withContext(Dispatchers.IO) {
        mutation.withLock { installed(language)?.let { block(it) } }
    }

    /** Remove only files recorded by this app's installed manifest. */
    suspend fun remove(language: String): Boolean = withContext(Dispatchers.IO) {
        mutation.withLock {
            require(language in LANGUAGES)
            val model = ownedPack(language) ?: return@withLock false
            val files = model.manifest.getJSONArray("installed_files")
            for (i in 0 until files.length()) {
                val name = files.getJSONObject(i).getString("name")
                require(plainName(name) && name != "manifest.json")
                Files.deleteIfExists(File(model.directory, name).toPath())
            }
            Files.deleteIfExists(File(model.directory, "manifest.json").toPath())
            try { Files.deleteIfExists(model.directory.toPath()) } catch (_: DirectoryNotEmptyException) { /* Keep unrelated files. */ }
            true
        }
    }

    data class InstalledModel(val directory: File, val model: File, val manifest: JSONObject) {
        /** Recheck every installed byte before native inference, off the UI thread. */
        suspend fun verify(): Boolean = withContext(Dispatchers.IO) {
            val files = manifest.getJSONArray("installed_files")
            for (i in 0 until files.length()) {
                currentCoroutineContext().ensureActive()
                val entry = files.getJSONObject(i)
                val name = entry.getString("name")
                if (!plainName(name)) return@withContext false
                val file = File(directory, name)
                if (!file.isFile || file.length() != entry.getLong("bytes") || digest(file) != entry.getString("sha256")) return@withContext false
            }
            true
        }
    }

    private suspend fun download(spec: FileSpec, target: File, routes: List<melox.network.MeloXGitHubSource>, progress: (Long) -> Unit) {
        val context = currentCoroutineContext()
        var last: Exception? = null
        for (route in routes) {
            try {
                val request = Request.Builder().url(routing.routedUrl(route, spec.url)).header("User-Agent", "MeloX-Desktop").build()
                val client = routing.client(route).newBuilder().cache(null)
                    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                    .callTimeout(600, TimeUnit.SECONDS).build()
                client.executeOwned(request, context.job) { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val declared = response.body.contentLength()
                    require(declared < 0 || declared == spec.bytes) { "AutoSync file size mismatch" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    response.body.byteStream().buffered().use { input -> target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            context.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            require(count <= spec.bytes) { "AutoSync file exceeds declared size" }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            progress(count)
                        }
                    } }
                    require(count == spec.bytes && digest.digest().hex() == spec.sha256) { "AutoSync file checksum mismatch" }
                }
                return
            } catch (error: Exception) {
                context.ensureActive()
                last = error
            }
        }
        throw last ?: IOException("No AutoSync model download route")
    }

    private fun publish(language: String, staging: File) {
        val live = File(root, language)
        val backup = File(root, ".${language}-backup-${UUID.randomUUID()}")
        val hadPrevious = live.exists()
        if (hadPrevious) {
            val old = requireNotNull(ownedPack(language)) { "Existing AutoSync directory is not an installed pack" }
            val files = old.manifest.getJSONArray("installed_files")
            val owned = (0 until files.length()).map { files.getJSONObject(it).getString("name") }.toSet() + "manifest.json"
            require(live.listFiles()?.all { it.name in owned && !Files.isSymbolicLink(it.toPath()) && it.isFile } == true) {
                "Existing AutoSync directory contains unmanaged files"
            }
        }
        if (hadPrevious) move(live, backup)
        try { move(staging, live) }
        catch (failure: Exception) {
            if (hadPrevious) try { move(backup, live) } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
        if (hadPrevious) backup.deleteRecursively() // Only the pack replaced by this successful install.
    }

    companion object {
        const val RELEASE_TAG = "android-models-v0.1.0-beta.4"
        const val RELEASE_PREFIX = "https://github.com/lladlam/MeloX-AutoSync/releases/download/$RELEASE_TAG/"
        const val INDEX_URL = RELEASE_PREFIX + "android-models.json"
        const val INDEX_SHA256 = "4b7b54a6f436b4f74ac6028f3876477a2d7dffe13fba557f9649577354a0f0ed"
        private const val MAX_INDEX_BYTES = 1024 * 1024
        private val LANGUAGES = setOf("en", "zh", "ja")
        private val mutation = Mutex()
        private fun plainName(value: String) = value != "." && value.matches(Regex("[A-Za-z0-9_.-]+")) && ".." !in value
        private fun validateSpec(spec: FileSpec) {
            require(plainName(spec.name) && spec.url == RELEASE_PREFIX + spec.name)
            require(spec.bytes in 1..1_000_000_000 && spec.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        private fun parseSpec(value: JSONObject) = FileSpec(value.getString("name"), value.optString("role", "asset"),
            value.getString("download_url"), value.getLong("bytes"), value.getString("sha256").lowercase()).also(::validateSpec)

        internal fun parseIndex(text: String): Index {
            val data = JSONObject(text)
            require(data.getString("release_tag") == RELEASE_TAG && data.optInt("min_backend_protocol", 1) <= 1)
            val languages = data.getJSONObject("languages")
            val extras = data.optJSONArray("extra_assets") ?: JSONArray()
            val precision = (0 until extras.length()).mapNotNull { i ->
                runCatching { parseSpec(extras.getJSONObject(i)) }.getOrNull()
            }.associateBy { it.name.substringBefore('-') }
            val packs = LANGUAGES.associateWith { language ->
                val obj = languages.getJSONObject(language)
                require(obj.getString("language") == language && obj.optInt("sample_rate", 0) == 16_000)
                val raw = obj.getJSONArray("files")
                val files = (0 until raw.length()).map { parseSpec(raw.getJSONObject(it)) }
                require(files.count { it.role == "model" } == 1 && files.map { it.name }.distinct().size == files.size)
                Pack(language, obj.getString("engine"), obj.optString("source_model"), obj.optString("license"),
                    obj.optJSONObject("accuracy")?.optString("status").orEmpty(), files, precision[language], obj.toString())
            }
            return Index(data.optString("accuracy_notice"), packs)
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        private suspend fun digest(file: File): String {
            val context = currentCoroutineContext()
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().hex()
        }
        private fun move(source: File, target: File) {
            try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath()) }
        }
    }
}
