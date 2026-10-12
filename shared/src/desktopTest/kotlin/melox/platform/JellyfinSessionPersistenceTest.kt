package melox.platform

import melox.provider.jellyfin.JellyfinSession
import melox.provider.jellyfin.JellyfinSessionPersistence
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.*

/** Durable protected file checks; this test protection is not the Windows DPAPI implementation. */
class JellyfinSessionPersistenceTest {
    private class Protection : SecretProtection {
        private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        override fun protect(plain: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key); iv + doFinal(plain)
        }
        override fun unprotect(encrypted: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(0, 12))); doFinal(encrypted.copyOfRange(12, encrypted.size))
        }
    }
    private fun persistence(file: PreferenceFile) = JellyfinSessionPersistence(file::get, file::putValues)
    private val session = JellyfinSession(serverUrl = "http://localhost:8096", accessToken = "synthetic-session-token", userId = "user-one", userName = "Fixture", serverId = "server-one")

    @Test fun stableKeySurvivesIndependentReopenAndSessionReplacement() {
        val root = Files.createTempDirectory("melox-jellyfin-store").toFile()
        try {
            val protection = Protection()
            val file = PreferenceFile.secure(root, "melox_jellyfin_session", protection)
            persistence(file).write(session)
            val key = file.get("melox_jellyfin_session_key")
            val reopened = PreferenceFile.secure(root, "melox_jellyfin_session", protection)
            assertEquals(session, persistence(reopened).read())
            assertFalse(File(root, "prefs_melox_jellyfin_session.dpapi").readText().contains(session.accessToken))
            val next = session.copy(accessToken = "synthetic-next-token", userId = "user-two")
            persistence(reopened).write(next)
            val again = PreferenceFile.secure(root, "melox_jellyfin_session", protection)
            assertEquals(key, again.get("melox_jellyfin_session_key"))
            assertEquals(next, persistence(again).read())
            persistence(again).clear()
            assertFalse(persistence(PreferenceFile.secure(root, "melox_jellyfin_session", protection)).read().isLoggedIn)
            assertNull(again.get("melox_jellyfin_session_key"))
        } finally { root.deleteRecursively() }
    }

    @Test fun unrecoverableOldCiphertextIsPreservedUntilActualNewLogin() {
        val root = Files.createTempDirectory("melox-jellyfin-legacy").toFile()
        try {
            val file = PreferenceFile.secure(root, "melox_jellyfin_session", Protection())
            file.put("encrypted_session", "legacy-unrecoverable-ciphertext")
            val store = persistence(file)
            assertFalse(store.read().isLoggedIn)
            assertEquals("legacy-unrecoverable-ciphertext", file.get("encrypted_session"))
            assertNull(file.get("melox_jellyfin_session_key"))
            assertFailsWith<IllegalArgumentException> { store.write(JellyfinSession()) }
            assertEquals("legacy-unrecoverable-ciphertext", file.get("encrypted_session"))
            store.write(session)
            assertEquals(session, store.read())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedProtectedCommitLeavesPreviousSessionAndKeyUntouched() {
        val values = mutableMapOf<String, String?>()
        var fail = false
        val store = JellyfinSessionPersistence(values::get) { update ->
            if (fail) throw java.io.IOException("controlled persistence failure")
            values.putAll(update)
        }
        store.write(session)
        val previous = values.toMap()
        fail = true
        assertFailsWith<java.io.IOException> { store.write(session.copy(accessToken = "synthetic-next-token")) }
        assertEquals(previous, values)
        assertEquals(session, store.read())
    }
}
