package melox.account

import com.sun.jna.Platform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import melox.platform.WindowsDpapi
import melox.platform.meloXDataDir
import org.json.JSONObject
import org.sqlite.SQLiteConfig

class NeteaseOfficialProfile internal constructor(internal val directory: Path) {
    val name: String get() = directory.fileName.toString()
}

/** One-shot Cookie import. All subsequent validation, persistence and requests use existing APIs. */
class NeteaseOfficialCookieReader internal constructor(
    private val root: Path?,
    private val temporaryRoot: Path,
    private val unprotect: (ByteArray) -> ByteArray,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    constructor() : this(
        if (Platform.isWindows()) System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            ?.let { Path.of(it, "NetEase", "CloudMusic") } else null,
        Path.of(meloXDataDir(), "temp"),
        WindowsDpapi::unprotect,
    )

    val isSupported: Boolean get() = root != null

    fun findProfiles(): List<NeteaseOfficialProfile> = sanitized {
        val directory = root ?: return@sanitized emptyList()
        if (!Files.isDirectory(directory)) return@sanitized emptyList()
        Files.list(directory).use { children ->
            children.filter { path ->
                path.fileName.toString().startsWith("webapp", ignoreCase = true) &&
                    Files.isRegularFile(path.resolve("Cookies")) &&
                    Files.isRegularFile(path.resolve("LocalPrefs.json"))
            }.map { NeteaseOfficialProfile(it.toRealPath()) }.toList()
                .distinctBy { it.directory }.sortedBy { it.name }
        }
    }

    fun readCookie(profile: NeteaseOfficialProfile): String = sanitized {
        if (findProfiles().none { it.directory == profile.directory }) {
            throw CookieImportException("未找到网易云音乐客户端登录数据，请先在客户端登录")
        }
        Files.createDirectories(temporaryRoot)
        val temporary = Files.createTempDirectory(temporaryRoot, "netease-cookie-import-")
        try {
            val prefs = copySnapshot(profile.directory, temporary)
            val blob = try {
                Base64.getDecoder().decode(JSONObject(prefs.toString(Charsets.UTF_8))
                    .getJSONObject("os_crypt").getString("encrypted_key"))
            } finally { prefs.fill(0) }
            val key = try {
                if (blob.size <= 5 || !blob.copyOfRange(0, 5).contentEquals("DPAPI".toByteArray())) {
                    throw CookieImportException("暂不支持此版本的网易云音乐登录数据")
                }
                val protectedKey = blob.copyOfRange(5, blob.size)
                try { unprotect(protectedKey) } finally { protectedKey.fill(0) }
            } finally { blob.fill(0) }
            try {
                if (key.size != 32) throw CookieImportException("无法读取当前 Windows 用户的网易云音乐登录态")
                queryCookie(temporary.resolve("Cookies"), key)
            } finally { key.fill(0) }
        } finally {
            // This directory contains only our encrypted copies, never official files or a saved key.
            Files.walk(temporary).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    private fun copySnapshot(source: Path, destination: Path): ByteArray {
        val names = listOf("Cookies", "Cookies-wal", "Cookies-journal", "LocalPrefs.json")
        fun stamps() = names.map { name ->
            val path = source.resolve(name)
            if (!Files.exists(path)) null else Files.readAttributes(path, BasicFileAttributes::class.java)
                .let { Triple(it.size(), it.lastModifiedTime(), it.fileKey()) }
        }
        val before = stamps()
        if (before.any { it != null && it.first > 64L * 1024 * 1024 }) {
            throw CookieImportException("网易云音乐登录数据过大，无法读取")
        }
        // Ordinary shared-read handles only; never open the official database with SQLite.
        names.dropLast(1).forEachIndexed { index, name ->
            if (before[index] != null) Files.newInputStream(source.resolve(name), StandardOpenOption.READ).use { input ->
                Files.newOutputStream(destination.resolve(name), StandardOpenOption.CREATE_NEW).use { output -> input.copyTo(output) }
            }
        }
        val prefs = Files.readAllBytes(source.resolve("LocalPrefs.json"))
        if (before != stamps()) {
            prefs.fill(0)
            throw CookieImportException("网易云音乐正在更新登录数据，请稍后重试；也可关闭客户端后重试")
        }
        return prefs
    }

    private fun queryCookie(database: Path, key: ByteArray): String {
        val config = SQLiteConfig().apply { setReadOnly(true); setBusyTimeout(1_500) }
        return config.createConnection("jdbc:sqlite:${database.toUri()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT value FROM meta WHERE key = 'version'").use { rows ->
                    if (!rows.next() || rows.getInt(1) != 14) {
                        throw CookieImportException("暂不支持此版本的网易云音乐登录数据")
                    }
                }
                val cookies = linkedMapOf<String, String>()
                statement.executeQuery("""
                    SELECT name, encrypted_value, expires_utc, has_expires FROM cookies
                    WHERE host_key IN ('.music.163.com', 'music.163.com') AND path = '/'
                      AND name IN ('MUSIC_U', '__csrf')
                """.trimIndent()).use { rows ->
                    val now = (nowMillis() + 11_644_473_600_000L) * 1_000L
                    while (rows.next()) {
                        if (rows.getInt("has_expires") != 0 && rows.getLong("expires_utc") <= now) continue
                        val name = rows.getString("name")
                        if (name in cookies) throw CookieImportException("发现多条网易云音乐登录态，请先在客户端重新登录")
                        cookies[name] = decryptCookie(rows.getBytes("encrypted_value"), key)
                    }
                }
                if (cookies["MUSIC_U"].isNullOrBlank()) {
                    throw CookieImportException("未找到有效登录态，请先在网易云音乐客户端登录")
                }
                NeteaseSessionStore.normalizeCookie(cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
        }
    }

    private fun decryptCookie(encrypted: ByteArray, key: ByteArray): String {
        if (encrypted.size <= 31 || !encrypted.copyOfRange(0, 3).contentEquals("v10".toByteArray())) {
            throw CookieImportException("暂不支持此版本的网易云音乐登录数据")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, encrypted, 3, 12))
        val plain = cipher.doFinal(encrypted, 15, encrypted.size - 15)
        try {
            // Cookie-octet only: reject delimiters/control bytes rather than changing their meaning.
            if (plain.isEmpty() || plain.any { (it.toInt() and 0xff).let { byte ->
                    byte !in 0x21..0x7e || byte == 0x22 || byte == 0x2c || byte == 0x3b || byte == 0x5c
                } }) throw CookieImportException("网易云音乐登录数据格式无效，请在客户端重新登录")
            return plain.toString(Charsets.US_ASCII)
        } finally { plain.fill(0) }
    }

    // Native/SQLite/JSON exceptions may contain local paths or input data. Do not expose them.
    private inline fun <T> sanitized(block: () -> T): T = try { block() }
    catch (error: CookieImportException) { throw error }
    catch (_: Exception) { throw CookieImportException("读取网易云音乐登录态失败，请确认使用同一 Windows 用户；也可关闭客户端后重试") }

    private class CookieImportException(message: String) : IOException(message)
}
