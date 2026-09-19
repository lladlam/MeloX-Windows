package melox.provider.lxuser

import melox.platform.getPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LxUserSourceRecord(
    val id: String,
    val name: String,
)

object LxUserSourceStore {
    private const val NAME = "melox_lxuser_sources"
    private const val KEY_SOURCES = "sources"

    private fun prefs() = getPreferences(NAME)

    fun list(): List<LxUserSourceRecord> {
        val raw = prefs().getString(KEY_SOURCES, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(LxUserSourceRecord(item.getString("id"), item.getString("name")))
                }
            }
        }.getOrNull().orEmpty()
    }

    fun script(id: String): String? = prefs().getString("script_$id", null)
}