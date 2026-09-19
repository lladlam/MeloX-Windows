package melox.remoteconfig

import melox.platform.getPreferences

object MeloXRemoteConfigConsent {
    const val PolicyVersion = "1.2-2026-08-25"
    private const val PreferencesName = "melox_remote_config_consent"
    private const val ChoiceMadeKey = "choice_made"
    private const val EnabledKey = "enabled"
    private const val VersionKey = "policy_version"
    private const val DecidedAtKey = "decided_at"

    private fun prefs() = getPreferences(PreferencesName)

    fun choiceMade(): Boolean =
        prefs().getBoolean(ChoiceMadeKey, false) && prefs().getString(VersionKey, null) == PolicyVersion

    fun enabled(): Boolean =
        prefs().getBoolean(EnabledKey, false) && prefs().getString(VersionKey, null) == PolicyVersion

    fun acceptedVersion(): String? = prefs().getString(VersionKey, null)

    fun accept() {
        prefs().apply {
            putBoolean(ChoiceMadeKey, true)
            putBoolean(EnabledKey, true)
            putString(VersionKey, PolicyVersion)
            putLong(DecidedAtKey, System.currentTimeMillis())
        }
    }

    fun reject() {
        prefs().apply {
            putBoolean(ChoiceMadeKey, true)
            putBoolean(EnabledKey, false)
            putString(VersionKey, PolicyVersion)
            putLong(DecidedAtKey, System.currentTimeMillis())
        }
    }
}
