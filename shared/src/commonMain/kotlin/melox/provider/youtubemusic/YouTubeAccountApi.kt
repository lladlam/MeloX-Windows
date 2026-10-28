package melox.provider.youtubemusic

import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.models.body.AccountMenuBody
import com.metrolist.innertube.models.response.AccountMenuResponse
import com.metrolist.innertube.models.response.AccountsListResponse
import com.metrolist.innertube.utils.YouTubeAuth
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import melox.network.MeloXHttpClient
import melox.settings.MeloXAppLanguage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class YouTubeAccountHttpException(val statusCode: Int) : IOException("YouTube Music HTTP $statusCode")

/**
 * Uses the Android InnerTube request/response models with an immutable session per
 * request. A candidate login must never be installed into the process-wide
 * YouTube singleton while other catalogue or playback requests are running.
 */
internal class YouTubeAccountApi(
    http: OkHttpClient = MeloXHttpClient.shared,
    private val root: HttpUrl = YouTubeClient.API_URL_YOUTUBE_MUSIC.toHttpUrl(),
    private val locale: () -> Locale = { MeloXAppLanguage.locale },
) {
    // Credentials are explicit request headers: never forward them through an
    // unexpected redirect, and never persist account replies in a shared cache.
    private val http = http.newBuilder().cache(null).followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    suspend fun accountInfo(session: YouTubeSession): AccountInfo {
        val raw = request(session, YouTubeClient.WEB_REMIX, "account/account_menu")
        val menu = json.decodeFromString<AccountMenuResponse>(raw)
        return menu.actions.firstOrNull()?.openPopupAction?.popup?.multiPageMenuRenderer?.header
            ?.activeAccountHeaderRenderer?.toAccountInfo() ?: error("YouTube Music 没有返回有效账号")
    }

    suspend fun accounts(session: YouTubeSession): List<YouTubeChannel> {
        val raw = request(session, YouTubeClient.WEB_ACCOUNTS, "account/accounts_list")
        fun items(element: JsonElement): List<JsonElement> = when (element) {
            is JsonObject -> buildList {
                if (element.containsKey("accountName") && element.containsKey("serviceEndpoint")) add(element)
                element.values.forEach { addAll(items(it)) }
            }
            is JsonArray -> element.flatMap(::items)
            else -> emptyList()
        }
        return items(json.parseToJsonElement(raw)).mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<AccountsListResponse.AccountItem>(element) }.getOrNull()
        }.mapNotNull { item ->
            val tokens = item.serviceEndpoint?.selectActiveIdentityEndpoint?.supportedTokens
            YouTubeChannel(name = item.accountName?.text ?: return@mapNotNull null,
                handle = item.accountByline?.text, thumbnailUrl = item.accountPhoto?.thumbnails?.lastOrNull()?.url,
                pageId = tokens?.firstNotNullOfOrNull { it.pageIdToken?.pageId },
                dataSyncId = tokens?.firstNotNullOfOrNull { it.datasyncIdToken?.datasyncIdToken }, selected = item.isSelected)
        }
    }

    private suspend fun request(session: YouTubeSession, client: YouTubeClient, path: String): String {
        check(session.isLoggedIn) { "请先登录 YouTube Music" }
        val context = client.toContext(YouTubeLocale.fromLocale(locale()), session.visitorData.takeIf(String::isNotBlank),
            session.dataSyncId.takeIf(String::isNotBlank))
        val headers = YouTubeAuth.headers(session.cookie, System.currentTimeMillis() / 1000, session.authUser, session.pageId)
        val request = Request.Builder().url(requireNotNull(root.resolve(path)).newBuilder().addQueryParameter("prettyPrint", "false").build())
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            .header("User-Agent", client.userAgent)
            .header("Accept", "application/json")
            .apply {
                session.visitorData.takeIf(String::isNotBlank)?.let { header("X-Goog-Visitor-Id", it) }
                headers.forEach { (name, value) -> header(name, value) }
            }
            .post(json.encodeToString(AccountMenuBody(context)).toRequestBody("application/json".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    continuation.resumeWith(Result.failure(error))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw YouTubeAccountHttpException(it.code)
                            it.body.string()
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        }
    }
}
