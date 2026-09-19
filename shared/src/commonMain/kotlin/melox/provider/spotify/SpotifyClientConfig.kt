package melox.provider.spotify

import melox.MeloXBuildConfig
import melox.platform.getPreferences

/**
 * Spotify Client ID comes from the build when one is baked in; otherwise the user
 * supplies it in the app. The customer-facing ID is not a secret, and browser login is
 * what OAuth uses here, so a plain preference is the right shape.
 */
object SpotifyClientConfig {
    private const val PreferencesName = "melox_spotify_client_config"
    private const val KeyClientId = "client_id"

    private fun prefs() = getPreferences(PreferencesName)

    fun effective(): String = userValue() ?: MeloXBuildConfig.SPOTIFY_CLIENT_ID

    fun read(): String = userValue().orEmpty()

    fun isConfigured(): Boolean = effective().isNotBlank()

    fun write(value: String) {
        prefs().putString(KeyClientId, value.trim())
    }

    fun clear() {
        prefs().remove(KeyClientId)
    }

    private fun userValue(): String? =
        prefs().getString(KeyClientId, null)?.trim()?.takeIf(String::isNotBlank)
}
