package melox.platform

import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

actual class MeloXPreferences private constructor(private val store: PreferenceFile) {
    actual constructor(name: String) : this(PreferenceFiles.open(name, secure = false))

    actual fun getString(key: String, defaultValue: String?): String? = store.get(key) ?: defaultValue
    actual fun putString(key: String, value: String) = store.put(key, value)
    actual fun putValues(values: Map<String, String?>) = store.putValues(values)
    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean = store.get(key)?.toBoolean() ?: defaultValue
    actual fun putBoolean(key: String, value: Boolean) = store.put(key, value.toString())
    actual fun getInt(key: String, defaultValue: Int): Int = store.get(key)?.toIntOrNull() ?: defaultValue
    actual fun putInt(key: String, value: Int) = store.put(key, value.toString())
    actual fun getLong(key: String, defaultValue: Long): Long = store.get(key)?.toLongOrNull() ?: defaultValue
    actual fun putLong(key: String, value: Long) = store.put(key, value.toString())
    actual fun getFloat(key: String, defaultValue: Float): Float = store.get(key)?.toFloatOrNull() ?: defaultValue
    actual fun putFloat(key: String, value: Float) = store.put(key, value.toString())
    actual fun remove(key: String) = store.put(key, null)
    actual fun clear() = store.clear()
    actual fun contains(key: String): Boolean = store.get(key) != null
    actual fun putStringSet(key: String, values: Set<String>) = store.put(key, values.joinToString("\u0001"))
    actual fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        store.get(key)?.split("\u0001")?.toSet() ?: defaultValue

    internal companion object {
        fun secure(name: String) = MeloXPreferences(PreferenceFiles.open(name, secure = true))
    }
}

private object PreferenceFiles {
    private val stores = ConcurrentHashMap<String, PreferenceFile>()
    fun open(name: String, secure: Boolean): PreferenceFile {
        require(name.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid preference store name" }
        val root = File(meloXDataDir()).canonicalFile
        return stores.computeIfAbsent("$root:$secure:$name") {
            if (secure) PreferenceFile.secure(root, name, DesktopSecretProtection.current)
            else PreferenceFile(File(root, "prefs_$name.properties"))
        }
    }
}

actual fun getPreferences(name: String): MeloXPreferences = MeloXPreferences(name)
actual fun getSecurePreferences(legacyName: String): MeloXPreferences = MeloXPreferences.secure(legacyName)
