package melox.provider.applemusic

import melox.platform.getPreferences

data class AppleMusicSession(
    val isConfigured: Boolean,
    val storefront: String,
)

object AppleMusicSessionStore {
    private const val PreferencesName = "melox_applemusic_session"
    private const val KeyStorefront = "storefront"

    private fun prefs() = getPreferences(PreferencesName)

    fun read(): AppleMusicSession =
        AppleMusicSession(
            isConfigured = prefs().contains(KeyStorefront),
            storefront = prefs().getString(KeyStorefront, "US").orEmpty(),
        )

    fun write(storefront: String) {
        prefs().putString(KeyStorefront, storefront.ifBlank { "US" })
    }

    fun clear() {
        prefs().remove(KeyStorefront)
    }
}