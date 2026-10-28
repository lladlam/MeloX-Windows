package melox.provider.youtubemusic

import com.metrolist.innertube.utils.YouTubeAuth
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Protocol fixtures exercise the actual desktop HTTP client, without private credentials. */
class YouTubeAccountProtocolTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
    private val session = YouTubeSession(cookie = "SAPISID=legacy; __Secure-3PAPISID=fixture-secret",
        visitorData = "fixture-visitor", dataSyncId = "fixture-sync", pageId = "fixture-page", authUser = "2")
    @BeforeTest fun start() = server.start()
    @AfterTest fun stop() { server.shutdown(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    private fun api() = YouTubeAccountApi(http, server.url("/youtubei/v1/")) { Locale.SIMPLIFIED_CHINESE }
    private fun request() = assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun accountCredentialsAreNotForwardedToARedirect() = runBlocking<Unit> {
        val destination = MockWebServer()
        destination.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/elsewhere")))
            val failure = assertFailsWith<YouTubeAccountHttpException> { api().accountInfo(session) }
            assertEquals(302, failure.statusCode)
            request()
            assertNull(destination.takeRequest(300, TimeUnit.MILLISECONDS))
        } finally {
            destination.shutdown()
        }
    }

    @Test fun accountValidationUsesTheSuppliedCookieAndCompleteSelectedContext() = runBlocking {
        server.enqueue(json("""{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":{"accountName":{"runs":[{"text":"Fixture channel"}]},"accountPhoto":{"thumbnails":[]}}}}}}}]}"""))
        assertEquals("Fixture channel", api().accountInfo(session).name)
        val request = request()
        assertEquals("/youtubei/v1/account/account_menu?prettyPrint=false", request.path)
        assertEquals("2", request.getHeader("X-Goog-AuthUser"))
        assertEquals("fixture-page", request.getHeader("X-Goog-PageId"))
        assertEquals("fixture-visitor", request.getHeader("X-Goog-Visitor-Id"))
        assertEquals("67", request.getHeader("X-YouTube-Client-Name"))
        val authorization = assertNotNull(request.getHeader("Authorization"))
        val timestamp = authorization.substringAfter("SAPISIDHASH ").substringBefore('_').toLong()
        assertEquals(YouTubeAuth.headers(session.cookie, timestamp, "2", "fixture-page")["Authorization"], authorization)
        val context = JSONObject(request.body.readUtf8()).getJSONObject("context")
        assertEquals("zh-CN", context.getJSONObject("client").getString("hl"))
        assertEquals("US", context.getJSONObject("client").getString("gl"))
        assertEquals("fixture-sync", context.getJSONObject("user").getString("onBehalfOfUser"))
        assertFalse(session.toString().contains("fixture-secret"))
    }

    @Test fun channelListFindsPersonalAndBrandIdentitiesInNestedResponse() = runBlocking {
        server.enqueue(json("""{"actions":[{"popup":{"sections":[{"accountName":{"simpleText":"Personal"},"isSelected":true,"serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[{"datasyncIdToken":{"datasyncIdToken":"personal-sync"}}]}}},{"other":{"accountName":{"runs":[{"text":"Brand"}]},"accountByline":{"simpleText":"@brand"},"serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[{"pageIdToken":{"pageId":"brand-page"}},{"datasyncIdToken":{"datasyncIdToken":"brand-sync"}}]}}}}]}}]}"""))
        val channels = api().accounts(session)
        assertEquals(listOf("Personal", "Brand"), channels.map { it.name })
        assertTrue(channels.first().selected)
        assertEquals("brand-page", channels.last().pageId)
        assertEquals("brand-sync", channels.last().dataSyncId)
        assertEquals("@brand", channels.last().handle)
        val request = request()
        assertEquals("/youtubei/v1/account/accounts_list?prettyPrint=false", request.path)
        assertEquals("1", request.getHeader("X-YouTube-Client-Name"))
    }

    @Test fun authenticationErrorsAreTypedWithoutExposingCookie() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val failure = assertFailsWith<YouTubeAccountHttpException> { api().accountInfo(session) }
        assertEquals(403, failure.statusCode)
        assertFalse(failure.message.orEmpty().contains("fixture-secret"))
    }
}
