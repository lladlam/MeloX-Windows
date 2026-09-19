package melox.platform

expect fun meloXDataDir(): String

expect fun meloXCacheDir(): String

expect fun readSettings(key: String): String?

expect fun writeSettings(key: String, value: String)

expect fun deleteSettings(key: String)

expect fun readFile(path: String): String?

expect fun writeFile(path: String, content: String)

expect fun deleteFile(path: String)

expect fun fileExists(path: String): Boolean
