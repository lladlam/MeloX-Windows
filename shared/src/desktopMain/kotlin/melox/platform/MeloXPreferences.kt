package melox.platform

import java.io.File
import java.util.Properties

actual class MeloXPreferences actual constructor(name: String) {
    private val file = File(meloXDataDir(), "prefs_${name}.properties")
    private val properties = Properties()

    init {
        if (file.isFile) {
            file.inputStream().use { properties.load(it) }
        }
    }

    private fun save() {
        file.parentFile?.mkdirs()
        file.outputStream().use { properties.store(it, "MeloX preferences") }
    }

    actual fun getString(key: String, defaultValue: String?): String? =
        properties.getProperty(key) ?: defaultValue

    actual fun putString(key: String, value: String) {
        properties.setProperty(key, value)
        save()
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        properties.getProperty(key)?.toBoolean() ?: defaultValue

    actual fun putBoolean(key: String, value: Boolean) {
        properties.setProperty(key, value.toString())
        save()
    }

    actual fun getInt(key: String, defaultValue: Int): Int =
        properties.getProperty(key)?.toIntOrNull() ?: defaultValue

    actual fun putInt(key: String, value: Int) {
        properties.setProperty(key, value.toString())
        save()
    }

    actual fun getLong(key: String, defaultValue: Long): Long =
        properties.getProperty(key)?.toLongOrNull() ?: defaultValue

    actual fun putLong(key: String, value: Long) {
        properties.setProperty(key, value.toString())
        save()
    }

    actual fun getFloat(key: String, defaultValue: Float): Float =
        properties.getProperty(key)?.toFloatOrNull() ?: defaultValue

    actual fun putFloat(key: String, value: Float) {
        properties.setProperty(key, value.toString())
        save()
    }

    actual fun remove(key: String) {
        properties.remove(key)
        save()
    }

    actual fun clear() {
        properties.clear()
        save()
    }

    actual fun contains(key: String): Boolean = properties.containsKey(key)

    actual fun putStringSet(key: String, values: Set<String>) {
        properties.setProperty(key, values.joinToString("\u0001"))
        save()
    }

    actual fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? {
        val raw = properties.getProperty(key) ?: return defaultValue
        return raw.split("\u0001").toSet()
    }
}

actual fun getPreferences(name: String): MeloXPreferences = MeloXPreferences(name)