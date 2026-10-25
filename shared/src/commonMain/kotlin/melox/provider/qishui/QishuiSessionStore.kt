package melox.provider.qishui

import java.security.SecureRandom
import melox.platform.getSecurePreferences

data class QishuiSession(
    val sessionId: String = "",
    val userId: String = "",
    val nickname: String = "",
    val deviceId: String = "",
    val installId: String = "",
    val cookie: String = "",
) {
    val isLoggedIn: Boolean get() = sessionId.isNotBlank() || cookie.isNotBlank()
    override fun toString() = "QishuiSession(isLoggedIn=$isLoggedIn)"
}

/** Isolated credentials in the desktop encrypted preference store; logout retains device identity. */
object QishuiSessionStore {
    private fun prefs() = getSecurePreferences("melox_qishui_session")

    @Synchronized fun read(): QishuiSession {
        val prefs = prefs()
        fun identity(key: String): String = prefs.getString(key, null)?.takeIf(String::isNotBlank) ?: run {
            val random = SecureRandom()
            buildString { repeat(16) { append(random.nextInt(10)) } }.also { prefs.putString(key, it) }
        }
        return QishuiSession(prefs.getString("sessionid", "").orEmpty(), prefs.getString("user_id", "").orEmpty(),
            prefs.getString("nickname", "").orEmpty(), identity("device_id"), identity("install_id"), prefs.getString("cookie", "").orEmpty())
    }

    @Synchronized fun save(sessionId: String, userId: String, nickname: String, cookie: String = "") {
        require(sessionId.isNotBlank() && sessionId.none { it.isWhitespace() || it == ';' })
        require(cookie.none { it == '\r' || it == '\n' })
        prefs().putValues(mapOf("sessionid" to sessionId, "user_id" to userId, "nickname" to nickname, "cookie" to cookie))
    }

    @Synchronized fun clear() {
        prefs().putValues(mapOf("sessionid" to null, "cookie" to null, "user_id" to null, "nickname" to null))
    }
}
