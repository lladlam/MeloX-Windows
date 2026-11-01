package melox.provider.qqmusic

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.*

class QQMusicSearchFallbackTest {
    @Test fun cancelledSearchCancelsOwnedRequestWithoutStartingFallback() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        server.createContext("/") { exchange ->
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
            runCatching { exchange.sendResponseHeaders(200, -1) }
            exchange.close()
        }
        server.start()
        try {
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                calls.incrementAndGet()
                chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.address.port}/").build())
            }.build()
            val result = withTimeoutOrNull(2_500) { QQMusicProvider(httpClient = http).searchSongs("song", 1, 10) }
            assertNull(result)
            assertTrue(arrived.await(1, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally { release.countDown(); server.stop(0) }
    }

    @Test fun desktopSearchIsUsedByProviderWithRealPaginationAndTrackMetadata() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            val payload = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            assertEquals(11, payload.getJSONObject("comm").getInt("ct"))
            assertEquals(0, payload.getJSONObject("comm").getInt("cv"))
            val req = payload.getJSONObject("req_0")
            assertEquals("DoSearchForQQMusicDesktop", req.getString("method"))
            assertEquals(2, req.getJSONObject("param").getInt("page_num"))
            val data = JSONObject().put("meta", JSONObject().put("sum", 42))
                .put("body", JSONObject().put("song", JSONObject().put("list", JSONArray().put(song("mid1", "Song")))))
            response(request, JSONObject().put("req_0", JSONObject().put("code", 0).put("data", data)).toString())
        }.build()
        val page = QQMusicProvider(httpClient = http).searchSongs(" hello ", 2, 10)
        assertEquals(1, calls)
        assertEquals(42L, page.total)
        assertEquals("mid1", page.items.single().id.value)
        assertEquals(202_000L, page.items.single().durationMs)
    }

    @Test fun desktopFailureFallsBackToMobileAndExpandsGroupedTracks() = runBlocking {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            if (calls == 1) throw IOException("desktop unavailable")
            assertEquals("u6.y.qq.com", request.url.host)
            assertNull(request.header("Cookie"))
            assertEquals("QQMusic 20080508(android)", request.header("User-Agent"))
            val payload = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
            assertFalse(payload.getJSONObject("comm").has("cv"))
            val key = "music.search.SearchCgiService.DoSearchForQQMusicMobile"
            assertEquals("", payload.getJSONObject(key).getJSONObject("param").getString("searchid"))
            val primary = song("primary", "Song").put("grp", JSONArray().put(song("alternate", "Song feat. guest")))
            val body = JSONObject().put("code", 0).put(key, JSONObject().put("code", 0)
                .put("data", JSONObject().put("body", JSONObject().put("item_song", JSONArray().put(primary)))))
            response(request, body.toString())
        }.build()
        val page = QQMusicProvider(httpClient = http).searchSongs("Song", 1, 10)
        assertEquals(2, calls)
        assertEquals(listOf("primary", "alternate"), page.items.map { it.id.value })
    }

    private fun song(mid: String, title: String) = JSONObject().put("id", 123L).put("mid", mid)
        .put("name", title).put("interval", 202).put("singer", JSONArray().put(JSONObject().put("name", "Artist")))

    private fun response(request: okhttp3.Request, body: String): Response = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
}
