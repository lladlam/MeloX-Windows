package melox.platform

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** One process-wide instance per store; state is committed only after durable persistence. */
internal class PreferenceFile(
    private val file: File?,
    private val protection: SecretProtection? = null,
    private val legacy: List<File> = emptyList(),
) {
    private var properties = Properties()

    init {
        if (file?.isFile == true) {
            val raw = file.readBytes()
            val plain = protection?.unprotect(raw) ?: raw
            try { plain.inputStream().use(properties::load) } finally { plain.fill(0) }
            // A previous process may have exited after replacing the encrypted
            // store but before removing its legacy source.
            if (protection != null) removeLegacy()
        } else {
            legacy.filter(File::isFile).forEach { it.inputStream().use(properties::load) }
            if (file != null && legacy.any(File::isFile)) {
                persist(properties)
                removeLegacy()
            }
        }
    }

    @Synchronized fun get(key: String): String? = properties.getProperty(key)

    @Synchronized fun put(key: String, value: String?) {
        putValues(mapOf(key to value))
    }

    @Synchronized fun putValues(values: Map<String, String?>) {
        val updated = Properties().apply { putAll(properties) }
        values.forEach { (key, value) ->
            if (value == null) updated.remove(key) else updated.setProperty(key, value)
        }
        persist(updated)
        properties = updated
    }

    @Synchronized fun clear() {
        val updated = Properties()
        persist(updated)
        properties = updated
    }

    private fun persist(updated: Properties) {
        val destination = file ?: return // Non-Windows development sessions are memory-only.
        val plain = ByteArrayOutputStream().use { output ->
            updated.store(output, "MeloX preferences")
            output.toByteArray()
        }
        try {
            val bytes = protection?.protect(plain) ?: plain
            if (protection != null) {
                val verified = protection.unprotect(bytes)
                try { check(plain.contentEquals(verified)) { "Secure preference verification failed" } }
                finally { verified.fill(0) }
            }
            atomicWrite(destination, bytes)
        } finally { plain.fill(0) }
    }

    private fun removeLegacy() {
        legacy.forEach { Files.deleteIfExists(it.toPath()) }
    }

    companion object {
        fun secure(root: File, name: String, protection: SecretProtection?): PreferenceFile = PreferenceFile(
            file = if (protection != null) File(root, "prefs_$name.dpapi") else null,
            protection = protection,
            legacy = listOf(File(root, "prefs_$name.properties"), File(root, "prefs_${name}_encrypted_v1.properties")),
        )
    }
}

internal fun atomicWrite(destination: File, bytes: ByteArray) {
    destination.parentFile.mkdirs()
    val temporary = Files.createTempFile(destination.parentFile.toPath(), ".${destination.name}.", ".tmp")
    try {
        FileOutputStream(temporary.toFile()).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        try {
            Files.move(temporary, destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally { Files.deleteIfExists(temporary) }
}
