package melox.remoteconfig

import melox.platform.meloXDataDir
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import org.json.JSONObject

internal data class MeloXStoredRemoteConfig(
    val envelope: String?,
    val etag: String?,
    val lastCheckedAtEpochMs: Long,
    val lastUpdatedAtEpochMs: Long,
    val highestConfigVersion: Int,
)

internal class MeloXRemoteConfigStore(private val file: File = File(meloXDataDir(), "remote_config/state.json")) {

    fun read(): MeloXStoredRemoteConfig = runCatching {
        if (!file.isFile) return@runCatching Empty
        val value = JSONObject(runCatching { file.readText() }.getOrDefault("{}"))
        MeloXStoredRemoteConfig(
            envelope = value.optString("envelope").takeIf(String::isNotBlank),
            etag = value.optString("etag").takeIf(String::isNotBlank),
            lastCheckedAtEpochMs = value.optLong("lastCheckedAtEpochMs"),
            lastUpdatedAtEpochMs = value.optLong("lastUpdatedAtEpochMs"),
            highestConfigVersion = value.optInt("highestConfigVersion"),
        )
    }.getOrDefault(Empty)

    fun write(value: MeloXStoredRemoteConfig) {
        file.parentFile?.mkdirs()
        val bytes = JSONObject()
            .put("envelope", value.envelope ?: "")
            .put("etag", value.etag ?: "")
            .put("lastCheckedAtEpochMs", value.lastCheckedAtEpochMs)
            .put("lastUpdatedAtEpochMs", value.lastUpdatedAtEpochMs)
            .put("highestConfigVersion", value.highestConfigVersion)
            .toString()
            .toByteArray()
        val temporary = File(file.parentFile, file.name + ".part")
        try {
            temporary.writeBytes(bytes)
            try { Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    fun clearEnvelope() {
        val current = read()
        write(
            current.copy(
                envelope = null,
                etag = null,
                lastCheckedAtEpochMs = 0L,
                lastUpdatedAtEpochMs = 0L,
            ),
        )
    }

    companion object {
        val Empty = MeloXStoredRemoteConfig(null, null, 0L, 0L, 0)
    }
}
