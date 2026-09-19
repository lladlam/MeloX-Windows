package melox.network
import melox.account.NeteaseSessionStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

data class MeloXAccountDetail(val userId: Long, val nickname: String, val avatarUrl: String?, val backgroundUrl: String?, val signature: String?, val level: Int, val listenSongs: Int, val follows: Int, val followers: Int, val playlistCount: Int)
class NeteaseAccountDetailsClient(cookieProvider: () -> String, httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared) {
    private val cookieProvider = cookieProvider; private val weapi = NeteaseAuthenticatedWeapi(cookieProvider, httpClient); private val eapi = NeteaseAuthenticatedEapi(cookieProvider, httpClient)
    suspend fun userDetail(userId: Long): MeloXAccountDetail = withContext(Dispatchers.IO) {
        val loggedIn = NeteaseSessionStore.containsMusicU(cookieProvider())
        val fallbackData = JSONObject().put("all", "true").put("userId", userId)
        val response = if (loggedIn) try { weapi.post("/api/v1/user/detail/$userId") } catch (error: IOException) { if (!error.message.orEmpty().contains("空响应")) throw error; eapi.post("/api/w/v1/user/detail/$userId", fallbackData) } else eapi.post("/api/w/v1/user/detail/$userId", fallbackData, authenticated = false)
        val profile = response.optJSONObject("profile") ?: throw IOException("网易云没有返回用户资料"); val id = profile.optLong("userId", userId).takeIf { it > 0L } ?: userId
        MeloXAccountDetail(id, profile.optString("nickname").ifBlank { "网易云用户" }, secure(profile.optString("avatarUrl").takeIf(String::isNotBlank)), secure(profile.optString("backgroundUrl").takeIf(String::isNotBlank)), profile.optString("signature").takeIf(String::isNotBlank), response.optInt("level", 0).coerceAtLeast(0), response.optInt("listenSongs", 0).coerceAtLeast(0), profile.optInt("follows", 0).coerceAtLeast(0), profile.optInt("followeds", 0).coerceAtLeast(0), profile.optInt("playlistCount", 0).coerceAtLeast(0))
    }
    private fun secure(value: String?): String? = value?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
}
