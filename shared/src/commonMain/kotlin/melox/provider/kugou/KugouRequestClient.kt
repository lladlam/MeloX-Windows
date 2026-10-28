package melox.provider.kugou

import java.io.IOException
import java.security.MessageDigest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class KugouAuthException(message: String) : IOException(message)
internal class KugouApiException(val errorCode: Int, message: String) : IOException(message)

internal class KugouRequestClient(
    private val sessionProvider: () -> KugouSession,
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    companion object {
        const val AppId = 1005
        const val ClientVersion = 20489
        private const val AndroidSignatureSalt = "OIlwieks28dk2k092lksi2UIkp"
        internal const val UserAgent = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
    }

    fun get(
        baseUrl: String = "https://gateway.kugou.com",
        path: String,
        params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        includeDefaults: Boolean = true,
        sign: Boolean = true,
    ): JSONObject = execute(
        method = "GET",
        baseUrl = baseUrl,
        path = path,
        params = params,
        body = null,
        headers = headers,
        includeDefaults = includeDefaults,
        sign = sign,
    )

    fun post(
        baseUrl: String = "https://gateway.kugou.com",
        path: String,
        params: Map<String, String> = emptyMap(),
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
        includeDefaults: Boolean = true,
        sign: Boolean = true,
    ): JSONObject = execute(
        method = "POST",
        baseUrl = baseUrl,
        path = path,
        params = params,
        body = body.toString(),
        headers = headers,
        includeDefaults = includeDefaults,
        sign = sign,
    )

    fun postWithoutBody(
        baseUrl: String = "https://gateway.kugou.com", path: String, params: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(), includeDefaults: Boolean = true, sign: Boolean = true,
    ): JSONObject = execute("POST", baseUrl, path, params, null, headers, includeDefaults, sign)

    /** Device registration returns encrypted bytes rather than JSON. */
    fun postRaw(
        baseUrl: String = "https://gateway.kugou.com", path: String, params: Map<String, String> = emptyMap(),
        body: String, headers: Map<String, String> = emptyMap(), includeDefaults: Boolean = true,
        includeCookie: Boolean = true,
    ): ByteArray {
        val session = sessionProvider()
        val platform = session.platform
        val clientTime = (System.currentTimeMillis() / 1_000L).toString()
        val requestParams = linkedMapOf<String, String>()
        if (includeDefaults) {
            requestParams.putAll(mapOf("dfid" to session.dfid.ifBlank { "-" }, "mid" to session.mid, "uuid" to "-",
                "appid" to platform.appId.toString(), "clientver" to platform.clientVersion.toString(), "clienttime" to clientTime))
            if (session.token.isNotBlank()) requestParams["token"] = session.token
            if (session.userId > 0L) requestParams["userid"] = session.userId.toString()
        }
        requestParams.putAll(params)
        requestParams["signature"] = androidSignature(requestParams, body, platform)
        val builder = Request.Builder().url(buildUrl(baseUrl, path, requestParams))
            .header("User-Agent", platform.userAgent).header("Accept", "application/json, text/plain, */*")
            .header("dfid", session.dfid.ifBlank { "-" }).header("mid", session.mid)
            .header("clienttime", requestParams["clienttime"] ?: clientTime)
            .header("kg-rc", "1").header("kg-thash", "5d816a0").header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
        if (includeCookie) builder.header("Cookie", cookieHeader(session))
        headers.forEach(builder::header)
        httpClient.newCall(builder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw KugouAuthException("酷狗音乐登录状态已失效，请重新登录")
            if (!response.isSuccessful) throw IOException("酷狗音乐请求失败：HTTP ${response.code}")
            return response.body.bytes()
        }
    }

    private fun execute(
        method: String,
        baseUrl: String,
        path: String,
        params: Map<String, String>,
        body: String?,
        headers: Map<String, String>,
        includeDefaults: Boolean,
        sign: Boolean,
    ): JSONObject {
        val session = sessionProvider()
        val platform = session.platform
        val clientTime = (System.currentTimeMillis() / 1_000L).toString()
        val requestParams = linkedMapOf<String, String>()
        if (includeDefaults) {
            requestParams["dfid"] = session.dfid.ifBlank { "-" }
            requestParams["mid"] = session.mid
            requestParams["uuid"] = "-"
            requestParams["appid"] = platform.appId.toString()
            requestParams["clientver"] = platform.clientVersion.toString()
            requestParams["clienttime"] = clientTime
            if (session.token.isNotBlank()) requestParams["token"] = session.token
            if (session.userId > 0L) requestParams["userid"] = session.userId.toString()
        }
        requestParams.putAll(params)
        if (sign && "signature" !in requestParams) {
            requestParams["signature"] = androidSignature(requestParams, body.orEmpty(), platform)
        }

        val url = buildUrl(baseUrl, path, requestParams)
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", platform.userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("dfid", session.dfid.ifBlank { "-" })
            .header("mid", session.mid)
            .header("clienttime", requestParams["clienttime"] ?: clientTime)
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .header("Cookie", cookieHeader(session))
        headers.forEach(builder::header)

        val request = if (method == "POST") {
            val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value ?: "application/json; charset=utf-8"
            builder.post(
                body.orEmpty().toRequestBody(contentType.toMediaType()),
            ).build()
        } else {
            builder.get().build()
        }

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw IOException("空响应")
            if (response.code == 401 || response.code == 403) throw KugouAuthException("酷狗音乐登录状态已失效，请重新登录")
            if (!response.isSuccessful) {
                throw IOException("酷狗音乐请求失败：HTTP ${response.code}")
            }
            if (responseBody.isBlank()) throw IOException("酷狗音乐返回了空响应")
            val result = runCatching { JSONObject(responseBody) }
                .getOrElse { throw IOException("酷狗音乐返回了无法解析的数据", it) }
            val status = result.optInt("status", 1)
            val errorCode = result.optInt("error_code", 0)
            if (status == 0 || status == 401 || errorCode != 0) {
                val message = result.optString("error_msg")
                    .ifBlank { result.optString("msg") }
                    .ifBlank { result.optString("message") }
                    .ifBlank { result.optString("error") }
                    .ifBlank { "请求失败" }
                val normalized = message.lowercase()
                if (status == 401 || errorCode == 401 || errorCode == 20001 ||
                    "登录" in normalized || "token" in normalized || "userid" in normalized) {
                    throw KugouAuthException("酷狗音乐登录状态已失效，请重新登录：$message")
                }
                throw KugouApiException(errorCode, "酷狗音乐请求失败：$message")
            }
            return result
        }
    }

    private fun buildUrl(baseUrl: String, path: String, params: Map<String, String>): HttpUrl {
        val normalizedBase = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
        val relativePath = path.removePrefix("/")
        return normalizedBase.toHttpUrl().newBuilder()
            .addPathSegments(relativePath)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
    }

    private fun cookieHeader(session: KugouSession): String =
        session.asCookieMap().entries.joinToString("; ") { (key, value) -> "$key=$value" }

    internal fun androidSignature(params: Map<String, String>, body: String = "", platform: KugouPlatform = KugouPlatform.Standard): String {
        val parameterString = params.keys.sorted().joinToString("") { key -> "$key=${params[key].orEmpty()}" }
        val salt = platform.androidSignatureSalt
        return md5Hex("$salt$parameterString$body$salt")
    }

    internal fun signKey(hash: String, session: KugouSession): String = md5Hex(
        hash + session.platform.keySignatureSalt + session.platform.appId + session.mid + session.userId)

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}

/** Search/catalog endpoints do not use one consistent singer field. */
internal fun kugouSingerName(item: JSONObject, vararg keys: String): String {
    keys.asSequence()
        .map(item::optString)
        .firstOrNull(String::isNotBlank)
        ?.let { return it }

    val arrays = listOf("Singers", "singers", "artists", "authors")
    arrays.forEach { key ->
        val values = item.optJSONArray(key) ?: return@forEach
        val names = buildList {
            for (index in 0 until values.length()) {
                when (val value = values.opt(index)) {
                    is JSONObject -> value.optString("name").takeIf(String::isNotBlank)?.let(::add)
                    is String -> value.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
        if (names.isNotEmpty()) return names.joinToString(" / ")
    }
    return ""
}

internal fun recoverKugouTrackText(rawTitle: String, rawSinger: String): Pair<String, String> {
    val title = rawTitle.trim()
    val singer = rawSinger.trim()
    if (singer.isNotBlank()) return title.removePrefix("$singer - ").trim() to singer
    val separator = title.indexOf(" - ")
    if (separator > 0 && separator < title.lastIndex - 2) {
        return title.substring(separator + 3).trim() to title.substring(0, separator).trim()
    }
    return title to singer
}
