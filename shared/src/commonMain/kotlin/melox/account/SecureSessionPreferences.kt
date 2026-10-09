package melox.account

import melox.platform.MeloXPreferences
import melox.platform.getSecurePreferences

/**
 * Desktop session preferences.
 *
 * Android uses EncryptedSharedPreferences with a legacy-plaintext migration.
 * Windows uses current-user DPAPI. Legacy stores are migrated only after an
 * encrypted, verified, atomic write succeeds. Native failures never fall back
 * to writing plaintext credentials.
 */
internal object SecureSessionPreferences {
    private val instances = mutableMapOf<String, MeloXPreferences>()

    @Synchronized
    fun open(legacyName: String): MeloXPreferences {
        instances[legacyName]?.let { return it }
        val prefs = getSecurePreferences(legacyName)
        instances[legacyName] = prefs
        return prefs
    }
}
