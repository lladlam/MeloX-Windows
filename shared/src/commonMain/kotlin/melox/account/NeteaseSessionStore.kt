package melox.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import melox.platform.clearDesktopCookieJar
import melox.network.NeteaseSearchClient

@Stable
class NeteaseSessionStore(
    private val cookieProvider: () -> String = { "" },
) {
    private val preferences = encryptedPreferences(PREFERENCES_NAME)

    var cookie by mutableStateOf(preferences.getString(KEY_COOKIE, "").orEmpty())
        private set

    var profile by mutableStateOf<NeteaseAccountProfile?>(null)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val isLoggedIn: Boolean
        get() = cookie.isNotBlank()

    suspend fun acceptAuthenticatedCookie(candidate: String, persist: Boolean = true): Result<NeteaseAccountProfile> {
        val normalized = normalizeCookie(candidate)
        if (!containsMusicU(normalized)) {
            return Result.failure(IllegalStateException("未检测到 MUSIC_U 登录 Cookie"))
        }

        isRefreshing = true
        errorMessage = null
        return runCatching {
            val account = NeteaseSearchClient(cookieProvider = { normalized }).accountProfile()
            if (persist) {
                preferences.putString(KEY_COOKIE, normalized)
                cookie = normalized
                profile = account
            }
            account
        }.onFailure { error ->
            errorMessage = error.message ?: "网易云账号验证失败"
        }.also {
            isRefreshing = false
        }
    }

    suspend fun refreshProfile(force: Boolean = false) {
        if (cookie.isBlank()) {
            profile = null
            errorMessage = null
            return
        }
        if (!force && profile != null) return

        isRefreshing = true
        errorMessage = null
        runCatching {
            NeteaseSearchClient(cookieProvider = { cookie }).accountProfile()
        }.onSuccess { account ->
            profile = account
        }.onFailure { error ->
            errorMessage = error.message ?: "账号信息读取失败"
        }
        isRefreshing = false
    }

    fun clear() {
        preferences.remove(KEY_COOKIE)
        cookie = ""
        profile = null
        errorMessage = null
        clearDesktopCookieJar()
    }

    companion object {
        private const val PREFERENCES_NAME = "netease_session"
        private const val KEY_COOKIE = "cookie_header"

        private fun encryptedPreferences(name: String) =
            SecureSessionPreferences.open(name)

        fun readCookie(): String =
            encryptedPreferences(PREFERENCES_NAME)
                .getString(KEY_COOKIE, "")
                .orEmpty()

        fun readPlaybackCookie(): String =
            encryptedPreferences("netease_playback_session")
                .getString(KEY_COOKIE, "").orEmpty()

        fun writePlaybackCookie(cookieHeader: String) {
            val normalized = normalizeCookie(cookieHeader)
            require(containsMusicU(normalized)) { "未检测到 MUSIC_U 登录 Cookie" }
            encryptedPreferences("netease_playback_session")
                .putString(KEY_COOKIE, normalized)
        }

        fun clearPlayback() {
            encryptedPreferences("netease_playback_session").clear()
        }

        fun containsMusicU(cookieHeader: String): Boolean =
            parseCookie(cookieHeader)["MUSIC_U"].isNullOrBlank().not()

        fun normalizeCookie(cookieHeader: String): String =
            parseCookie(cookieHeader)
                .toSortedMap()
                .entries
                .joinToString("; ") { (key, value) -> "$key=$value" }

        fun parseCookie(cookieHeader: String): Map<String, String> =
            cookieHeader
                .split(';')
                .mapNotNull { item ->
                    val parts = item.trim().split('=', limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    if (key.isBlank()) null else key to value
                }
                .toMap()
    }
}

@Composable
fun rememberNeteaseSessionStore(): NeteaseSessionStore =
    remember { NeteaseSessionStore() }
