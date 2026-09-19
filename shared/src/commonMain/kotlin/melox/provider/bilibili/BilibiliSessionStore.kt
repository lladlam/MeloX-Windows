package melox.provider.bilibili

import melox.platform.getPreferences

data class BilibiliSession(
    val cookie: String,
    val userId: String,
) {
    val isLoggedIn: Boolean
        get() = cookie.isNotBlank() && userId.isNotBlank()
}

object BilibiliSessionStore {
    private const val PreferencesName = "melox_bilibili_session"
    private const val KeyCookie = "cookie"
    private const val KeyUserId = "userid"

    private fun prefs() = getPreferences(PreferencesName)

    fun read(): BilibiliSession =
        BilibiliSession(
            cookie = prefs().getString(KeyCookie, "").orEmpty(),
            userId = prefs().getString(KeyUserId, "").orEmpty(),
        )

    fun write(cookie: String, userId: String) {
        prefs().apply {
            putString(KeyCookie, cookie)
            putString(KeyUserId, userId)
        }
    }

    fun clear(clearWebCookies: Boolean = true) {
        prefs().clear()
    }
}