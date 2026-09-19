package melox.account

import melox.platform.MeloXPreferences
import melox.platform.getPreferences

/**
 * Desktop session preferences.
 *
 * Android uses EncryptedSharedPreferences with a legacy-plaintext migration.
 * Desktop JVM has no Keystore-backed encryption wired yet, so sessions live in
 * the plain MeloX preference store; the API surface stays identical so callers
 * only drop the context argument.
 */
internal object SecureSessionPreferences {
    private val instances = mutableMapOf<String, MeloXPreferences>()

    @Synchronized
    fun open(legacyName: String): MeloXPreferences {
        instances[legacyName]?.let { return it }
        val prefs = getPreferences("${legacyName}_encrypted_v1")
        instances[legacyName] = prefs
        return prefs
    }
}
