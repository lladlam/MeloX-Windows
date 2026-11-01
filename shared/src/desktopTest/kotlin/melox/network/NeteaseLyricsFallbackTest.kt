package melox.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import kotlin.test.*

class NeteaseLyricsFallbackTest {
    @Test fun placeholderPrimaryFallsBackWithoutSessionOrCookieJar() = runBlocking {
        withFixture(primary = "[00:00.00]纯音乐，请欣赏", fallback = "[00:01.00]Real lyric") { client, calls ->
            val doc = client.lyrics(2607641032)
            assertEquals("Real lyric", doc.lines.single().text)
            assertEquals(listOf("POST", "GET"), calls.map { it.first })
            assertTrue(calls.first().second.contains("MUSIC_U=test"))
            assertNull(calls.last().second.takeIf { it.isNotEmpty() })
        }
    }

    @Test fun validPrimaryDoesNotRequestPublicEndpoint() = runBlocking {
        withFixture(primary = "[00:01.00]Primary lyric", fallback = "[00:02.00]Unexpected") { client, calls ->
            assertEquals("Primary lyric", client.lyrics(2607641033).lines.single().text)
            assertEquals(listOf("POST"), calls.map { it.first })
        }
    }

    @Test fun primaryFailureStillUsesPublicEndpoint() = runBlocking {
        withFixture(primary = null, fallback = "[00:01.00]Fallback lyric") { client, calls ->
            assertEquals("Fallback lyric", client.lyrics(2607641036).lines.single().text)
            assertEquals(listOf("POST", "GET"), calls.map { it.first })
        }
    }

    @Test fun publicPlainTextRemainsUntimedAndPlaceholdersRemainEmpty() = runBlocking {
        withFixture(primary = "[00:00.00]纯音乐，请欣赏", fallback = "First line\nSecond line") { client, _ ->
            val doc = client.lyrics(2607641034)
            assertTrue(doc.isUnsynchronized)
            assertEquals(listOf("First line", "Second line"), doc.lines.map { it.text })
        }
        withFixture(primary = "[00:00.00]纯音乐，请欣赏", fallback = "[00:00.00]暂无歌词") { client, _ ->
            assertTrue(client.lyrics(2607641035).lines.isEmpty())
        }
    }

    private suspend fun withFixture(primary: String?, fallback: String, block: suspend (NeteaseSearchClient, List<Pair<String, String>>) -> Unit) {
        val calls = CopyOnWriteArrayList<Pair<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val method = exchange.requestMethod
            calls += method to exchange.requestHeaders.getFirst("Cookie").orEmpty()
            val body = JSONObject().put("code", if (method == "POST" && primary == null) 503 else 200)
                .put("lrc", JSONObject().put("lyric", if (method == "POST") primary.orEmpty() else fallback))
                .toString().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val jar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: HttpUrl) = if (url.encodedPath.startsWith("/api/song/lyric"))
                    listOf(Cookie.Builder().name("jar-secret").value("private").hostOnlyDomain(url.host).build()) else emptyList()
            }
            val http = OkHttpClient.Builder().cookieJar(jar).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder()
                    .scheme("http").host("127.0.0.1").port(server.address.port).build()).build())
            }.build()
            block(NeteaseSearchClient(httpClient = http, cookieProvider = { "MUSIC_U=test" }), calls)
        } finally { server.stop(0) }
    }
}
