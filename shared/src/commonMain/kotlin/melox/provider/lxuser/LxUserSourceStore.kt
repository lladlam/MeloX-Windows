package melox.provider.lxuser

import java.io.File
import java.util.UUID
import melox.platform.getPreferences
import melox.platform.meloXDataDir
import org.json.JSONArray
import org.json.JSONObject

data class LxUserSourceRecord(val id: String, val name: String, val metadata: LxUserScriptMetadata = LxUserScriptMetadata(name = name))

/** Executable bodies live in app-owned files; legacy preference entries remain readable. */
object LxUserSourceStore {
    private const val NAME = "melox_lxuser_sources"
    private const val KEY_SOURCES = "sources"
    const val MaxScriptBytes = 9_000_000L
    private const val MaxSources = 20
    private val ownedId = Regex("user_api_[0-9a-f-]{36}")
    private val unicode = Regex("\\\\u([0-9a-fA-F]{4})")
    private fun prefs() = getPreferences(NAME)
    private fun directory() = File(meloXDataDir(), "lx-user-sources")

    @Synchronized fun list(): List<LxUserSourceRecord> = runCatching {
        val array = JSONArray(prefs().getString(KEY_SOURCES, "[]").orEmpty())
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
                val name = item.optString("name").ifBlank { id }
                fun field(key: String) = item.optString(key).takeIf(String::isNotBlank)
                add(LxUserSourceRecord(id, name, LxUserScriptMetadata(name, field("version"), field("author"),
                    field("description"), field("homepage"), field("expirationTime"))))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized fun script(id: String): String? {
        if (list().none { it.id == id }) return null
        if (ownedId.matches(id)) {
            val file = File(directory(), "$id.js")
            if (file.isFile && file.length() <= MaxScriptBytes) return file.readText(Charsets.UTF_8)
        }
        return prefs().getString("script_$id", null)
    }

    @Synchronized fun importScript(script: String): LxUserSourceRecord {
        val source = script.removePrefix("\uFEFF")
        require(source.toByteArray(Charsets.UTF_8).size <= MaxScriptBytes) { "音乐源脚本过大（最大 9 MB）" }
        require(looksLikeLxMusicSource(source)) { "不是有效的 LX Music 音乐源脚本" }
        val records = list()
        require(records.size < MaxSources) { "最多添加 20 个音乐源" }
        val metadata = LxUserScriptMetadata.parse(source)
        val id = "user_api_${UUID.randomUUID()}"
        val record = LxUserSourceRecord(id, metadata.name ?: id, metadata)
        val dir = directory()
        check(dir.isDirectory || dir.mkdirs()) { "无法创建音乐源目录" }
        val file = File(dir, "$id.js")
        check(file.createNewFile()) { "无法创建音乐源脚本" }
        try {
            file.writeText(source, Charsets.UTF_8)
            save(records + record)
        } catch (error: Throwable) {
            file.delete() // Only the newly created app-owned file.
            throw error
        }
        return record
    }

    @Synchronized fun remove(id: String) {
        val records = list()
        if (records.none { it.id == id }) return
        save(records.filterNot { it.id == id })
        LxUserRuntime.evict(id)
        if (ownedId.matches(id)) File(directory(), "$id.js").delete()
        prefs().remove("script_$id")
    }

    private fun save(records: List<LxUserSourceRecord>) {
        val array = JSONArray()
        records.forEach { record -> array.put(JSONObject().apply {
            put("id", record.id); put("name", record.name)
            put("version", record.metadata.version.orEmpty()); put("author", record.metadata.author.orEmpty())
            put("description", record.metadata.description.orEmpty()); put("homepage", record.metadata.homepage.orEmpty())
            put("expirationTime", record.metadata.expirationTime.orEmpty())
        }) }
        prefs().putString(KEY_SOURCES, array.toString())
    }

    internal fun looksLikeLxMusicSource(script: String): Boolean {
        val decoded = unicode.replace(script) { it.groupValues[1].toInt(16).toChar().toString() }
        return decoded.contains("@name") && decoded.contains("@version") && decoded.contains("globalThis") &&
            (decoded.contains("lx") || decoded.contains("musicUrl") || decoded.contains("apiUrl"))
    }
}
