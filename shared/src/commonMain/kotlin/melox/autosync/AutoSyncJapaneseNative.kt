package melox.autosync

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Extracts only the pinned, app-packaged frontend; never searches PATH. */
internal object AutoSyncJapaneseNative {
    private data class Bundle(val name: String, val suffix: String, val sha256: String)
    private var extracted: File? = null

    @Synchronized fun library(): File {
        extracted?.takeIf { it.isFile && hash(it) == bundle().sha256 }?.let { return it }
        val bundle = bundle()
        val input = checkNotNull(javaClass.classLoader.getResourceAsStream("autosync/native/${bundle.name}")) {
            "Bundled OpenJTalk frontend is missing"
        }
        val target = File.createTempFile("melox-openjtalk-", bundle.suffix)
        try {
            input.use { source -> target.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 32L * 1024 * 1024) { "OpenJTalk frontend exceeds size limit" }
                    output.write(buffer, 0, n)
                }
            } }
            check(hash(target) == bundle.sha256) { "OpenJTalk frontend checksum mismatch" }
            target.deleteOnExit()
            extracted = target
            return target
        } catch (failure: Throwable) {
            target.delete() // This invocation created the temporary file.
            throw failure
        }
    }

    private fun bundle(): Bundle {
        val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val arch = System.getProperty("os.arch").orEmpty().lowercase(Locale.ROOT)
        require(arch == "amd64" || arch == "x86_64") { "Unsupported OpenJTalk architecture" }
        return when {
            os.startsWith("windows") -> Bundle("melox_ja_g2p-win-x64.dll", ".dll", "120fcdee0f1eb65bcccd1cda045bc6ef436317ac613dcd170155d21ce169640a")
            os == "linux" -> Bundle("libmelox_ja_g2p-linux-x64.so", ".so", "a82b9056d4346f7bad2ca704c93e93dcee37970126797f8fb0f6fe9b79c9c6e4")
            else -> error("Unsupported OpenJTalk operating system")
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
