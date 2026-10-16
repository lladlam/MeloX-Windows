package melox.account

import com.sun.jna.Platform
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import melox.platform.WindowsDpapi
import org.json.JSONObject
import org.sqlite.SQLiteConfig
import kotlin.test.*

/** Real SQLite and AES-GCM, synthetic credentials only. Windows also exercises real current-user DPAPI. */
class NeteaseOfficialCookieReaderTest {
    private class Fixture : AutoCloseable {
        val home = Files.createTempDirectory("melox-网易-cookie-test-")
        val root = Files.createDirectory(home.resolve("CloudMusic"))
        val profile = Files.createDirectory(root.resolve("webapp91x64"))
        val temporary = home.resolve("melox-temp")
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded
        val blob = if (Platform.isWindows()) WindowsDpapi.protect(key) else byteArrayOf(1, 2, 3)
        var exposedKey: ByteArray? = null
        val reader = NeteaseOfficialCookieReader(root, temporary, { protected ->
            (if (Platform.isWindows()) WindowsDpapi.unprotect(protected) else {
                check(protected.contentEquals(blob))
                key.copyOf()
            }).also { exposedKey = it }
        }, { NOW })

        init {
            Files.writeString(profile.resolve("LocalPrefs.json"), JSONObject().put("os_crypt",
                JSONObject().put("encrypted_key", Base64.getEncoder().encodeToString("DPAPI".toByteArray() + blob))).toString())
            database().use { connection -> connection.createStatement().use { sql ->
                sql.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
                sql.execute("INSERT INTO meta VALUES ('version', '14')")
                sql.execute("""CREATE TABLE cookies (host_key TEXT, name TEXT, path TEXT,
                    encrypted_value BLOB, expires_utc INTEGER, has_expires INTEGER)""")
            } }
        }

        fun database() = SQLiteConfig().createConnection("jdbc:sqlite:${profile.resolve("Cookies").toUri()}")

        fun add(name: String, value: String, host: String = ".music.163.com", path: String = "/",
                expires: Long = (NOW + 11_644_473_600_000L + 60_000L) * 1_000L,
                transform: (ByteArray) -> ByteArray = { it }) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val encrypted = transform("v10".toByteArray() + cipher.iv + cipher.doFinal(value.toByteArray()))
            database().use { connection ->
                connection.prepareStatement("INSERT INTO cookies VALUES (?, ?, ?, ?, ?, 1)").use { sql ->
                    sql.setString(1, host); sql.setString(2, name); sql.setString(3, path)
                    sql.setBytes(4, encrypted); sql.setLong(5, expires); sql.executeUpdate()
                }
            }
        }

        fun read() = reader.readCookie(reader.findProfiles().single())

        fun assertClean() {
            if (Files.exists(temporary)) Files.list(temporary).use { assertEquals(0L, it.count()) }
            exposedKey?.let { assertTrue(it.all { byte -> byte == 0.toByte() }, "AES key must be wiped") }
        }

        override fun close() { key.fill(0); home.toFile().deleteRecursively() }
    }

    @Test fun importsOnlySessionCookiesAndUsesExistingSessionValidationWithoutModifyingSource() = runBlocking {
        Fixture().use { f ->
            f.add("MUSIC_U", "synthetic-account-session==")
            f.add("__csrf", "synthetic-csrf")
            f.add("MUSIC_R_U", "not-imported", path = "/api/login/token/refresh")
            f.add("MUSIC_U", "wrong-domain", host = ".not-music.163.com")
            f.add("MUSIC_U", "wrong-path", path = "/other")
            val originals = listOf("Cookies", "LocalPrefs.json").associateWith { name ->
                Files.readAllBytes(f.profile.resolve(name)) to Files.getLastModifiedTime(f.profile.resolve(name))
            }
            val imported = f.read()
            assertEquals(mapOf("MUSIC_U" to "synthetic-account-session==", "__csrf" to "synthetic-csrf"),
                NeteaseSessionStore.parseCookie(imported))
            val account = NeteaseAccountProfile(7, "Synthetic imported account", null, null, null)
            val store = NeteaseSessionStore(accountLoader = { header -> assertEquals(imported, header); account })
            store.clear()
            try {
                assertEquals(account, store.acceptAuthenticatedCookie(imported).getOrThrow())
                assertEquals(imported, NeteaseSessionStore.readCookie())
                assertEquals(account, store.profile)
            } finally { store.clear() }
            originals.forEach { (name, before) ->
                assertContentEquals(before.first, Files.readAllBytes(f.profile.resolve(name)))
                assertEquals(before.second, Files.getLastModifiedTime(f.profile.resolve(name)))
            }
            assertEquals(setOf("Cookies", "LocalPrefs.json"), Files.list(f.profile).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() })
            f.assertClean()
        }
    }

    @Test fun rejectedImportedCookieDoesNotReplaceExistingSession() = runBlocking {
        Fixture().use { f ->
            f.add("MUSIC_U", "synthetic-rejected")
            val account = NeteaseAccountProfile(7, "Synthetic previous account", null, null, null)
            val store = NeteaseSessionStore(accountLoader = { header ->
                if (header.contains("synthetic-rejected")) throw IOException("Session rejected") else account
            })
            store.clear()
            try {
                store.acceptAuthenticatedCookie("MUSIC_U=synthetic-previous").getOrThrow()
                assertTrue(store.acceptAuthenticatedCookie(f.read()).isFailure)
                assertEquals("MUSIC_U=synthetic-previous", NeteaseSessionStore.readCookie())
                assertEquals(account, store.profile)
            } finally { store.clear() }
            f.assertClean()
        }
    }

    @Test fun discoversOnlyCompleteProfilesAndDoesNotChooseBetweenMultipleProfiles() {
        Fixture().use { f ->
            Files.createDirectory(f.root.resolve("webapp-incomplete"))
            val other = Files.createDirectory(f.root.resolve("webapp-other"))
            Files.copy(f.profile.resolve("Cookies"), other.resolve("Cookies"))
            Files.copy(f.profile.resolve("LocalPrefs.json"), other.resolve("LocalPrefs.json"))
            assertEquals(listOf("webapp-other", "webapp91x64"), f.reader.findProfiles().map { it.name })
            val absent = NeteaseOfficialCookieReader(f.home.resolve("absent"), f.temporary, { error("must not decrypt") })
            assertTrue(absent.findProfiles().isEmpty())
            assertFalse(Files.exists(f.temporary))
        }
    }

    @Test fun rejectsExpiredOrMissingMainCookie() {
        Fixture().use { f ->
            f.add("MUSIC_U", "synthetic-expired", expires = (NOW + 11_644_473_600_000L) * 1_000L)
            f.add("__csrf", "synthetic-csrf")
            assertFailsWith<IOException> { f.read() }
            f.assertClean()
        }
    }

    @Test fun rejectsUnknownEncryptionAndTamperedGcmTagAndCleansTemporaryCopies() {
        for (tamper in listOf<(ByteArray) -> ByteArray>(
            { it.apply { this[2] = '1'.code.toByte() } },
            { it.apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() } },
        )) Fixture().use { f ->
            f.add("MUSIC_U", "synthetic-do-not-leak", transform = tamper)
            val error = assertFailsWith<IOException> { f.read() }
            assertFalse(error.toString().contains("synthetic-do-not-leak"))
            assertNull(error.cause)
            f.assertClean()
        }
    }

    @Test fun rejectsCookieDelimiterInjectionAndDuplicateRootSessions() {
        for (duplicate in listOf(false, true)) Fixture().use { f ->
            f.add("MUSIC_U", if (duplicate) "synthetic-first" else "synthetic;another=secret")
            if (duplicate) f.add("MUSIC_U", "synthetic-second", host = "music.163.com")
            assertFailsWith<IOException> { f.read() }
            f.assertClean()
        }
    }

    @Test fun dpapiAndMalformedPreferencesFailuresAreSanitizedAndCleaned() {
        Fixture().use { f ->
            val reader = NeteaseOfficialCookieReader(f.root, f.temporary, { error("synthetic-private-path-and-secret") })
            val error = assertFailsWith<IOException> { reader.readCookie(reader.findProfiles().single()) }
            assertFalse(error.toString().contains("synthetic-private-path-and-secret"))
            assertNull(error.cause)
            f.assertClean()
            Files.writeString(f.profile.resolve("LocalPrefs.json"), "synthetic-private-json-secret")
            assertFalse(assertFailsWith<IOException> { f.read() }.toString().contains("synthetic-private-json-secret"))
            f.assertClean()
        }
    }

    @Test fun rejectsUnknownSchemaAndUnselectedProfile() {
        Fixture().use { f ->
            f.add("MUSIC_U", "synthetic-session")
            f.database().use { connection -> connection.createStatement().use { it.execute("UPDATE meta SET value = '24'") } }
            assertFailsWith<IOException> { f.read() }
            f.assertClean()
            assertFailsWith<IOException> { f.reader.readCookie(NeteaseOfficialProfile(f.home)) }
            f.assertClean()
        }
    }

    @Test fun readsCommittedWalCookiesWhileWriterConnectionRemainsOpen() {
        Fixture().use { f ->
            f.database().use { writer ->
                writer.createStatement().use { it.execute("PRAGMA journal_mode=WAL"); it.execute("PRAGMA wal_autocheckpoint=0") }
                writer.autoCommit = false
                writer.createStatement().use { sql -> sql.executeQuery("SELECT COUNT(*) FROM cookies").use { assertTrue(it.next()) } }
                f.add("MUSIC_U", "synthetic-from-wal")
                assertTrue(Files.size(f.profile.resolve("Cookies-wal")) > 0)
                assertEquals("MUSIC_U=synthetic-from-wal", f.read())
                f.assertClean()
            }
        }
    }

    private companion object { const val NOW = 1_792_000_000_000L }
}
