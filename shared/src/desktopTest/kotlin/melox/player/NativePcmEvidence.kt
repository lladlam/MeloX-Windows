package melox.player

import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

/** Retain only generated audio from the isolated Gradle test home for independent readback. */
internal fun retainNativePcmEvidence(name: String, bytes: ByteArray, scenario: String) {
    val directory = File(System.getProperty("user.home"), "native-pcm-evidence").apply { mkdirs() }
    val file = File(directory, "$name.s16le.gz")
    GZIPOutputStream(file.outputStream()).use { it.write(bytes) }
    File(directory, "$name.json").writeText(JSONObject()
        .put("origin", "GENERATED_AUDIO_PRODUCTION_NATIVE_DECODER")
        .put("output", "CONTROLLED_SOFTWARE_SOURCE_DATA_LINE")
        .put("physical_audible_acceptance", "BLOCKED")
        .put("scenario", scenario).put("file", file.name)
        .put("sample_rate", 44100).put("channels", 2).put("format", "S16LE")
        .put("bytes", bytes.size)
        .put("pcm_sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        .toString(2))
}
