package melox.music.provider

import melox.platform.getPreferences

/** Local consent gate for user-configured music sources. It is never remote-controlled. */
object ThirdPartyMusicSourceConsentStore {
    private const val PreferencesName = "melox_third_party_music_sources"
    private const val KeyEnabled = "enabled"
    private const val KeyAgreementVersion = "agreement_version"
    private const val KeyMembershipFallbackOnly = "membership_fallback_only"
    const val AgreementVersion = "1.0-2026-08-27"

    private fun prefs() = getPreferences(PreferencesName)

    fun enabled(): Boolean = prefs().getBoolean(KeyEnabled, false)

    fun accept() {
        prefs().apply {
            putBoolean(KeyEnabled, true)
            putString(KeyAgreementVersion, AgreementVersion)
        }
    }

    fun reject() {
        prefs().putBoolean(KeyEnabled, false)
    }

    fun membershipFallbackOnly(): Boolean = prefs().getBoolean(KeyMembershipFallbackOnly, false)

    fun setMembershipFallbackOnly(enabled: Boolean) {
        prefs().putBoolean(KeyMembershipFallbackOnly, enabled)
    }
}
