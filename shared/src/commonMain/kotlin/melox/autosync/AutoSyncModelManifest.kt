package melox.autosync

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Reads the published pack descriptor and the verified, installed tokenizer. */
internal data class AutoSyncModelManifest(
    val language: String,
    val engine: String,
    val inputName: String,
    val tokenCountName: String?,
    val outputs: List<String>,
    val blankId: Int,
    val delimiterId: Int,
    val unknownId: Int,
    val frameHopSeconds: Double,
    val tokens: List<String>,
    val identity: String,
) {
    companion object {
        fun read(model: AutoSyncModelRepository.InstalledModel): AutoSyncModelManifest {
            val json = model.manifest
            val input = json.optJSONObject("input")?.optString("name")?.takeIf(String::isNotBlank)
                ?: json.optJSONArray("inputs")?.optJSONObject(0)?.optString("name")?.takeIf(String::isNotBlank)
                ?: error("Missing ONNX input")
            val auxiliary = json.optJSONArray("aux_inputs") ?: json.optJSONArray("inputs") ?: JSONArray()
            val tokenCount = (0 until auxiliary.length()).asSequence().mapNotNull { auxiliary.optJSONObject(it)?.optString("name") }
                .firstOrNull { it != input && it.contains("token", ignoreCase = true) }
            val outputs = json.optJSONArray("outputs") ?: JSONArray()
            val outputNames = (0 until outputs.length()).mapNotNull { outputs.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
            val text = json.getJSONObject("text")
            val tokenizer = text.getString("tokens_file")
            require(tokenizer.matches(Regex("[A-Za-z0-9_.-]+")) && ".." !in tokenizer)
            val installed = json.getJSONArray("installed_files")
            require((0 until installed.length()).any { installed.getJSONObject(it).optString("name") == tokenizer })
            val tokens = parseTokens(File(model.directory, tokenizer).readText(Charsets.UTF_8))
            require(tokens.isNotEmpty()) { "Empty model vocabulary" }
            val fingerprint = (0 until installed.length()).map { installed.getJSONObject(it) }
                .sortedBy { it.getString("name") }.joinToString("|") { "${it.getString("name")}:${it.getString("sha256")}" }
            val identity = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            return AutoSyncModelManifest(
                language = json.getString("language"), engine = json.getString("engine"), inputName = input,
                tokenCountName = tokenCount, outputs = outputNames, blankId = json.optInt("blank_id", 0),
                delimiterId = text.optInt("word_delimiter_id", -1).takeIf { it >= 0 } ?: tokens.indexOf("|").coerceAtLeast(0),
                unknownId = tokens.indexOf("<unk>").takeIf { it >= 0 } ?: 3,
                frameHopSeconds = json.optDouble("frame_hop_seconds", Double.NaN).takeIf { it.isFinite() && it > 0 } ?: 0.02,
                tokens = tokens, identity = identity,
            )
        }

        internal fun parseTokens(text: String): List<String> {
            val trimmed = text.trimStart()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                require(array.length() <= 100_000)
                return List(array.length()) { array.optString(it) }
            }
            val obj = JSONObject(trimmed)
            require(obj.length() <= 100_000)
            val byIndex = HashMap<Int, String>()
            obj.keys().forEach { key ->
                val value = obj.get(key)
                when {
                    value is Number -> byIndex[value.toInt()] = key
                    key.toIntOrNull() != null -> byIndex[key.toInt()] = value.toString()
                    value is String && value.toIntOrNull() != null -> byIndex[value.toInt()] = key
                }
            }
            val max = byIndex.keys.maxOrNull() ?: return emptyList()
            require(max in 0..100_000 && byIndex.keys.all { it >= 0 })
            return List(max + 1) { byIndex[it].orEmpty() }
        }
    }
}
