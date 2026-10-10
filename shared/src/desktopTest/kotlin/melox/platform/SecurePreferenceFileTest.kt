package melox.platform

import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.*

/** Tests migration/atomic storage; Windows native DPAPI round-trip is a separate OS check. */
class SecurePreferenceFileTest {
    private class TestProtection : SecretProtection {
        private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        override fun protect(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.iv + cipher.doFinal(plain)
        }
        override fun unprotect(encrypted: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            return cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
        }
    }

    @Test fun migratesBothLegacyNamesAndPersistsOnlyProtectedPayload() {
        val root = Files.createTempDirectory("melox-session-test").toFile()
        try {
            val original = File(root, "prefs_account.properties").apply { writeText("cookie=old-cookie\naccount=id\n") }
            val renamed = File(root, "prefs_account_encrypted_v1.properties").apply { writeText("cookie=secret-token\n") }
            val protection = TestProtection()
            val store = PreferenceFile.secure(root, "account", protection)
            assertEquals("secret-token", store.get("cookie"))
            assertEquals("id", store.get("account"))
            assertFalse(original.exists())
            assertFalse(renamed.exists())
            val protectedFile = File(root, "prefs_account.dpapi")
            assertFalse(protectedFile.readBytes().toString(Charsets.ISO_8859_1).contains("secret-token"))
            store.put("cookie", "new-secret")
            assertEquals("new-secret", PreferenceFile.secure(root, "account", protection).get("cookie"))
            store.clear()
            assertNull(PreferenceFile.secure(root, "account", protection).get("cookie"))
        } finally { root.deleteRecursively() }
    }

    @Test fun protectionFailurePreservesLegacyAndCommittedState() {
        val root = Files.createTempDirectory("melox-failed-session-test").toFile()
        try {
            val legacy = File(root, "prefs_account.properties").apply { writeText("token=original\n") }
            val failing = object : SecretProtection {
                override fun protect(plain: ByteArray): ByteArray = error("DPAPI unavailable")
                override fun unprotect(encrypted: ByteArray): ByteArray = error("DPAPI unavailable")
            }
            val memory = PreferenceFile.secure(root, "account", failing)
            assertEquals("original", memory.get("token"))
            memory.put("token", "process-only")
            assertEquals("process-only", memory.get("token"))
            assertTrue(legacy.isFile)
            assertFalse(File(root, "prefs_account.dpapi").exists())
            val protection = TestProtection()
            val store = PreferenceFile.secure(root, "account", protection)
            val committed = File(root, "prefs_account.dpapi").readBytes()
            val broken = PreferenceFile(File(root, "prefs_account.dpapi"), object : SecretProtection {
                override fun protect(plain: ByteArray): ByteArray = error("write fails")
                override fun unprotect(encrypted: ByteArray): ByteArray = protection.unprotect(encrypted)
            })
            assertFails { broken.put("token", "uncommitted") }
            assertEquals("original", broken.get("token"))
            assertContentEquals(committed, File(root, "prefs_account.dpapi").readBytes())
            assertEquals("original", store.get("token"))
        } finally { root.deleteRecursively() }
    }

    @Test fun nonWindowsDevelopmentNeverWritesPlaintextSessions() {
        val root = Files.createTempDirectory("melox-memory-session-test").toFile()
        try {
            val store = PreferenceFile.secure(root, "account", null)
            store.put("token", "memory-only")
            assertEquals("memory-only", store.get("token"))
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptCiphertextRecoversAnEmptyVerifiedStoreAndDoesNotReviveStaleLegacy() {
        val root = Files.createTempDirectory("melox-corrupt-session-test").toFile()
        try {
            val cipher = File(root, "prefs_account.dpapi").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val original = cipher.readBytes()
            File(root, "prefs_account.properties").writeText("token=stale\n")
            val protection = TestProtection()
            val recovered = PreferenceFile.secure(root, "account", protection)
            assertNull(recovered.get("token"))
            assertContentEquals(original, root.listFiles()!!.single { it.name.startsWith(".account.corrupt-") }.readBytes())
            recovered.put("token", "fresh")
            assertEquals("fresh", PreferenceFile.secure(root, "account", protection).get("token"))
            assertFalse(File(root, "prefs_account.properties").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun unusableProtectionAndCiphertextFallBackToMemoryWithoutWritingSecrets() {
        val root = Files.createTempDirectory("melox-unavailable-session-test").toFile()
        try {
            File(root, "prefs_account.dpapi").writeBytes(byteArrayOf(1, 2, 3))
            val failing = object : SecretProtection {
                override fun protect(plain: ByteArray): ByteArray = error("DPAPI unavailable")
                override fun unprotect(encrypted: ByteArray): ByteArray = error("DPAPI unavailable")
            }
            val memory = PreferenceFile.secure(root, "account", failing)
            memory.put("token", "memory-secret")
            assertEquals("memory-secret", memory.get("token"))
            assertFalse(File(root, "prefs_account.dpapi").exists())
            assertTrue(root.listFiles()!!.none { it.readBytes().toString(Charsets.ISO_8859_1).contains("memory-secret") })
        } finally { root.deleteRecursively() }
    }
}
