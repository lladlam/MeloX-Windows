package melox.provider.kugou

import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class KugouQrLoginSession(
    val key: String,
    val qrContentUrl: String,
)

sealed interface KugouQrLoginState {
    data object Expired : KugouQrLoginState
    data object Waiting : KugouQrLoginState
    data object Scanned : KugouQrLoginState
    data class Authorized(
        val token: String,
        val userId: Long,
        val vipToken: String = "",
        val vipType: Int = 0,
    ) : KugouQrLoginState

    data class Unknown(val status: Int) : KugouQrLoginState
}

/**
 * Minimal direct port of MakcRe/KuGouMusicApi's QR login routes. The QR key and
 * returned account token stay on the phone; MeloX does not proxy them.
 */
class KugouLoginClient(
    private val sessionProvider: () -> KugouSession,
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    suspend fun createQrSession(): KugouQrLoginSession = withContext(Dispatchers.IO) {
        val session = sessionProvider()
        val response = webSignedGet(
            path = "/v2/qrcode",
            params = mapOf(
                "appid" to LoginQrAppId.toString(),
                "type" to "1",
                "plat" to "4",
                "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=$MusicAppId&",
                "srcappid" to SourceAppId.toString(),
            ),
            session = session,
        )
        val data = response.optJSONObject("data") ?: response
        val key = firstString(data, "qrcode", "key", "qrcode_key", "qrkey")
            .ifBlank { firstString(response, "qrcode", "key", "qrcode_key", "qrkey") }
        if (key.isBlank()) throw IOException("酷狗音乐没有返回二维码 key")
        KugouQrLoginSession(
            key = key,
            qrContentUrl = "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode=$key",
        )
    }

    suspend fun checkQrSession(key: String): KugouQrLoginState = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Kugou QR key must not be blank" }
        val session = sessionProvider()
        val response = webSignedGet(
            path = "/v2/get_userinfo_qrcode",
            params = mapOf(
                "plat" to "4",
                "appid" to MusicAppId.toString(),
                "srcappid" to SourceAppId.toString(),
                "qrcode" to key,
            ),
            session = session,
        )
        val data = response.optJSONObject("data") ?: response
        when (val status = data.optInt("status", response.optInt("status", -1))) {
            0 -> KugouQrLoginState.Expired
            1 -> KugouQrLoginState.Waiting
            2 -> KugouQrLoginState.Scanned
            4 -> {
                val token = firstString(data, "token", "Token")
                val userId = firstLong(data, "userid", "user_id", "UserID")
                if (token.isBlank() || userId <= 0L) {
                    throw IOException("酷狗音乐已授权，但没有返回完整登录凭证")
                }
                KugouQrLoginState.Authorized(
                    token = token,
                    userId = userId,
                    vipToken = firstString(data, "vip_token", "vipToken"),
                    vipType = firstLong(data, "vip_type", "vipType").toInt().coerceAtLeast(0),
                )
            }
            else -> KugouQrLoginState.Unknown(status)
        }
    }

    private fun webSignedGet(
        path: String,
        params: Map<String, String>,
        session: KugouSession,
    ): JSONObject {
        val clientTime = (System.currentTimeMillis() / 1_000L).toString()
        val requestParams = linkedMapOf<String, String>()
        requestParams["dfid"] = session.dfid.ifBlank { "-" }
        requestParams["mid"] = session.mid
        requestParams["uuid"] = "-"
        requestParams["appid"] = MusicAppId.toString()
        requestParams["clientver"] = KugouRequestClient.ClientVersion.toString()
        requestParams["clienttime"] = clientTime
        if (session.token.isNotBlank()) requestParams["token"] = session.token
        if (session.userId > 0L) requestParams["userid"] = session.userId.toString()
        requestParams.putAll(params)
        requestParams["signature"] = webSignature(requestParams)

        val url = "https://login-user.kugou.com/".toHttpUrl().newBuilder()
            .addPathSegments(path.removePrefix("/"))
            .apply { requestParams.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Mobile Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("dfid", session.dfid.ifBlank { "-" })
            .header("mid", session.mid)
            .header("clienttime", clientTime)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("空响应")
            if (!response.isSuccessful) throw IOException("酷狗登录请求失败：HTTP ${response.code}")
            if (body.isBlank()) throw IOException("酷狗登录接口返回了空响应")
            val json = runCatching { JSONObject(body) }
                .getOrElse { throw IOException("酷狗登录接口返回了无法解析的数据", it) }
            if (json.optInt("status", 1) == 0 || json.optInt("error_code", 0) != 0) {
                throw IOException(
                    json.optString("error_msg")
                        .ifBlank { json.optString("msg") }
                        .ifBlank { "酷狗登录请求失败" },
                )
            }
            return json
        }
    }

    private fun webSignature(params: Map<String, String>): String {
        val paramsString = params.keys.sorted().joinToString("") { key -> "$key=${params[key].orEmpty()}" }
        return md5Hex("$WebSignatureSalt$paramsString$WebSignatureSalt")
    }

    private fun firstString(value: JSONObject, vararg keys: String): String =
        keys.asSequence().map(value::optString).firstOrNull(String::isNotBlank).orEmpty()

    private fun firstLong(value: JSONObject, vararg keys: String): Long =
        keys.asSequence().mapNotNull { key ->
            when (val raw = value.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
        }.firstOrNull() ?: -1L

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val MusicAppId = 1005
        private const val LoginQrAppId = 1001
        private const val SourceAppId = 2919
        private const val WebSignatureSalt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    }
}
