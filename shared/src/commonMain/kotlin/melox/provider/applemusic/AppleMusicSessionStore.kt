package melox.provider.applemusic

import melox.platform.getSecurePreferences as getPreferences

data class AppleMusicSession(
    val developerToken: String = "",
    val musicUserToken: String = "",
    val storefront: String = "us",
) {
    val isConfigured get() = developerToken.isNotBlank()
    val hasUserAuthorization get() = isConfigured && musicUserToken.isNotBlank()
    override fun toString() = "AppleMusicSession(configured=$isConfigured, authorized=$hasUserAuthorization, storefront=$storefront)"
}

object AppleMusicSessionStore {
    private const val PreferencesName = "melox_applemusic_session"
    private const val KeyStorefront = "storefront"
    private const val KeyDeveloperToken = "developer_token"
    private const val KeyMusicUserToken = "music_user_token"

    private fun prefs() = getPreferences(PreferencesName)

    fun read(): AppleMusicSession =
        AppleMusicSession(
            developerToken = prefs().getString(KeyDeveloperToken, "").orEmpty(),
            musicUserToken = prefs().getString(KeyMusicUserToken, "").orEmpty(),
            storefront = prefs().getString(KeyStorefront, "us").orEmpty().ifBlank { "us" }.lowercase(),
        )

    fun write(storefront: String) {
        prefs().putString(KeyStorefront, storefront.ifBlank { "US" })
    }

    fun write(session: AppleMusicSession) {
        require(session.isConfigured)
        prefs().apply {
            putString(KeyDeveloperToken, session.developerToken.trim())
            putString(KeyMusicUserToken, session.musicUserToken.trim())
            putString(KeyStorefront, session.storefront.lowercase())
        }
    }

    fun clear() {
        prefs().clear()
    }
}
