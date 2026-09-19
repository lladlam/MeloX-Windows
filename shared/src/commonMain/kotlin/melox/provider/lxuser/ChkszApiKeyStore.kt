package melox.provider.lxuser

import melox.platform.getPreferences

object ChkszApiKeyStore {
    private const val NAME = "melox_chksz"
    private const val KEY_API_KEY = "api_key"

    private fun prefs() = getPreferences(NAME)

    fun read(): String = prefs().getString(KEY_API_KEY, "").orEmpty()
    fun write(key: String) = prefs().putString(KEY_API_KEY, key)
    fun clear() = prefs().remove(KEY_API_KEY)
}