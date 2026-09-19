package melox.platform

import java.io.File
import java.util.Properties

private val dataDir: File by lazy {
    val home = System.getProperty("user.home")
    File(home, ".melox").also { it.mkdirs() }
}

private val cacheDir: File by lazy {
    File(dataDir, "cache").also { it.mkdirs() }
}

private val settingsFile: File by lazy {
    File(dataDir, "settings.properties").also { if (!it.exists()) it.createNewFile() }
}

private fun loadSettings(): Properties {
    val props = Properties()
    if (settingsFile.exists()) {
        settingsFile.inputStream().use { props.load(it) }
    }
    return props
}

private fun saveSettings(props: Properties) {
    settingsFile.outputStream().use { props.store(it, null) }
}

actual fun meloXDataDir(): String = dataDir.absolutePath

actual fun meloXCacheDir(): String = cacheDir.absolutePath

actual fun readSettings(key: String): String? = loadSettings().getProperty(key)

actual fun writeSettings(key: String, value: String) {
    val props = loadSettings()
    props.setProperty(key, value)
    saveSettings(props)
}

actual fun deleteSettings(key: String) {
    val props = loadSettings()
    props.remove(key)
    saveSettings(props)
}

actual fun readFile(path: String): String? {
    return try {
        File(path).readText()
    } catch (_: Exception) {
        null
    }
}

actual fun writeFile(path: String, content: String) {
    File(path).parentFile?.mkdirs()
    File(path).writeText(content)
}

actual fun deleteFile(path: String) {
    File(path).delete()
}

actual fun fileExists(path: String): Boolean = File(path).exists()