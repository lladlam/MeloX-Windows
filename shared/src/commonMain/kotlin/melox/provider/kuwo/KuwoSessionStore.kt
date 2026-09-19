package melox.provider.kuwo

import melox.platform.getPreferences

data class KuwoSession(
    val token: String,
    val userId: String,
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank() && userId.isNotBlank()
}

object KuwoSessionStore {
    private const val PreferencesName = "melox_kuwo_session"
    private const val PlaybackPreferencesName = "melox_kuwo_playback_session"
    private const val KeyToken = "token"
    private const val KeyUserId = "userid"

    private fun prefs(playback: Boolean) =
        getPreferences(if (playback) PlaybackPreferencesName else PreferencesName)

    fun read(playback: Boolean = false): KuwoSession =
        KuwoSession(
            token = prefs(playback).getString(KeyToken, "").orEmpty(),
            userId = prefs(playback).getString(KeyUserId, "").orEmpty(),
        )

    fun write(token: String, userId: String, playback: Boolean = false): KuwoSession {
        prefs(playback).apply {
            putString(KeyToken, token)
            putString(KeyUserId, userId)
        }
        return read(playback)
    }

    fun clear(playback: Boolean = false) {
        prefs(playback).clear()
    }
}