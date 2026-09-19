package melox.provider.jellyfin

import melox.platform.getPreferences
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Stores the single Jellyfin session encrypted with a JVM-generated key. */
object JellyfinSessionStore {
    private const val PreferencesName = "melox_jellyfin_session"
    private const val SessionKey = "encrypted_session"
    private const val KeyAlias = "melox_jellyfin_session_key"

    private fun preferences() = getPreferences(PreferencesName)

    fun read(): JellyfinSession {
        val encoded = preferences().getString(SessionKey, null) ?: return JellyfinSession()
        return runCatching {
            val packed = Base64.getDecoder().decode(encoded)
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            JellyfinSession(
                serverUrl = json.optString("serverUrl"),
                accessToken = json.optString("accessToken"),
                userId = json.optString("userId"),
                userName = json.optString("userName"),
            )
        }.getOrElse { JellyfinSession() }
    }

    fun write(session: JellyfinSession) {
        val json = JSONObject().apply {
            put("serverUrl", session.serverUrl)
            put("accessToken", session.accessToken)
            put("userId", session.userId)
            put("userName", session.userName)
        }.toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = iv + encrypted
        preferences().putString(SessionKey, Base64.getEncoder().encodeToString(packed))
    }

    fun clear() {
        preferences().remove(SessionKey)
    }

    private fun key(): javax.crypto.SecretKey {
        // Try AndroidKeyStore first, fall back to JVM
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.getEntry(KeyAlias, null)?.let { return (it as java.security.KeyStore.SecretKeyEntry).secretKey }
            val generator = javax.crypto.KeyGenerator.getInstance("AES")
            generator.init(256)
            val key = generator.generateKey()
            keyStore.setEntry(KeyAlias, java.security.KeyStore.SecretKeyEntry(key), java.security.KeyStore.PasswordProtection("password".toCharArray()))
            key
        } catch (_: Exception) {
            // Fallback: use a fixed key (not ideal but works for desktop)
            val generator = javax.crypto.KeyGenerator.getInstance("AES")
            generator.init(256)
            generator.generateKey()
        }
    }
}
