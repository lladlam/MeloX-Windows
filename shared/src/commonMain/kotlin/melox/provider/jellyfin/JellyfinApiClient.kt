package melox.provider.jellyfin

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import melox.network.executeOwned
import melox.platform.getPreferences
import java.util.UUID

/** Actual user-server JSON transport, owned through response-body consumption. */
class JellyfinApiClient(
    private val httpClient: OkHttpClient,
    private val clientName: String = "MeloX-Desktop",
    private val clientVersion: String = "1.0.0",
) {
    suspend fun publicServerInfo(serverUrl: String): JellyfinServerInfo {
        val json = request(serverUrl, "/System/Info/Public", null)
        return JellyfinServerInfo(
            id = json.optString("Id"),
            name = json.optString("ServerName").ifBlank { "Jellyfin" },
            version = json.optString("Version"),
        )
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String): JellyfinSession {
        val normalized = normalizeServerUrl(serverUrl)
        val body = JSONObject().put("Username", username).put("Pw", password)
        val json = request(normalized, "/Users/AuthenticateByName", body)
        val user = json.optJSONObject("User") ?: throw IOException("Jellyfin 登录响应缺少用户信息")
        val token = json.optString("AccessToken").takeIf(String::isNotBlank)
            ?: throw IOException("Jellyfin 登录响应缺少 AccessToken")
        val server = publicServerInfo(normalized)
        val userId = user.optString("Id").takeIf(String::isNotBlank)
            ?: throw IOException("Jellyfin 登录响应缺少用户 ID")
        return JellyfinSession(
            serverUrl = normalized,
            accessToken = token,
            userId = userId,
            serverId = server.id,
            userName = user.optString("Name").ifBlank { username },
        )
    }

    suspend fun get(session: JellyfinSession, path: String, query: Map<String, String> = emptyMap()): JSONObject =
        request(session.baseUrl, path, null, session, query)

    suspend fun post(session: JellyfinSession, path: String, body: JSONObject = JSONObject(), query: Map<String, String> = emptyMap()): JSONObject =
        request(session.baseUrl, path, body, session, query)

    suspend fun delete(session: JellyfinSession, path: String, query: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(session.baseUrl, path, query))
            .header("Accept", "application/json")
            .header("X-Emby-Authorization", authorizationHeader())
            .header("X-Emby-Token", session.accessToken)
            .delete()
            .build()
        httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
            .executeOwned(request, currentCoroutineContext().job) { response ->
            if (!response.isSuccessful) throw IOException("Jellyfin 请求失败：HTTP ${response.code}")
        }
    }

    private suspend fun request(
        serverUrl: String,
        path: String,
        body: JSONObject?,
        session: JellyfinSession? = null,
        query: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url(serverUrl, path, query))
            .header("Accept", "application/json")
            .header("X-Emby-Authorization", authorizationHeader())
        session?.let { builder.header("X-Emby-Token", it.accessToken) }
        body?.let { builder.post(it.toString().toRequestBody(JSON)) }
        httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
            .executeOwned(builder.build(), currentCoroutineContext().job) { response ->
            if (!response.isSuccessful) throw IOException("Jellyfin 请求失败：HTTP ${response.code}")
            val body = response.body
            if (body != null && body.contentLength() > MaxResponseBytes) throw IOException("Jellyfin 响应过大")
            val bytes = body?.byteStream()?.use { it.readNBytes(MaxResponseBytes + 1) } ?: byteArrayOf()
            if (bytes.size > MaxResponseBytes) throw IOException("Jellyfin 响应过大")
            val text = bytes.toString(Charsets.UTF_8)
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun url(serverUrl: String, path: String, query: Map<String, String>) =
        (normalizeServerUrl(serverUrl) + path).toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

    private fun authorizationHeader(): String =
        "MediaBrowser Client=\"$clientName\", Device=\"${System.getProperty("os.name").orEmpty().replace('"', '_')}\", DeviceId=\"${deviceId()}\", Version=\"$clientVersion\""

    companion object {
        private const val MaxResponseBytes = 4 * 1024 * 1024
        val JSON = "application/json; charset=utf-8".toMediaType()
        fun normalizeServerUrl(value: String): String {
            val url = value.trim().trimEnd('/').toHttpUrl()
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "服务器地址不能包含凭据、查询参数或片段" }
            return url.toString().trimEnd('/')
        }
        private fun deviceId(): String = synchronized(JellyfinApiClient::class.java) {
            val prefs = getPreferences("melox_jellyfin_client")
            prefs.getString("device_id", null)?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString().also { prefs.putString("device_id", it) }
        }
    }
}
