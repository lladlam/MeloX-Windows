package melox.remoteconfig

import melox.platform.meloXDataDir
import java.io.File
import org.json.JSONObject

internal data class MeloXStoredRemoteConfig(
    val envelope: String?,
    val etag: String?,
    val lastCheckedAtEpochMs: Long,
    val lastUpdatedAtEpochMs: Long,
    val highestConfigVersion: Int,
)

internal class MeloXRemoteConfigStore {
    private val file = File(meloXDataDir(), "remote_config/state.json")

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
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Unable to persist remote config state" }
        }
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
