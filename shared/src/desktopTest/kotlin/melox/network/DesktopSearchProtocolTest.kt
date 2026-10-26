package melox.network

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import melox.music.model.MusicSource
import melox.provider.netease.NeteaseProvider
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.json.JSONObject
import kotlin.test.*

/** Real owned HTTP calls and EAPI encryption; only the remote service is a loopback protocol fixture. */
class DesktopSearchProtocolTest {
    private val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val cancelled = AtomicBoolean()
    private val workers = Executors.newCachedThreadPool { Thread(it, "search-protocol-fixture").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = workers
        createContext("/") { exchange ->
            try {
                val form = exchange.requestBody.bufferedReader().use { it.readText() }
                val params = URLDecoder.decode(form.substringAfter("params="), "UTF-8")
                val encrypted = params.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
                val decoded = cipher.doFinal(encrypted).toString(Charsets.UTF_8).split("-36cd479b6b5-")
                val payload = JSONObject(decoded[1])
                val digest = MessageDigest.getInstance("MD5").digest("nobody${decoded[0]}use${decoded[1]}md5forencrypt".toByteArray()).joinToString("") { "%02x".format(it) }
                check(digest == decoded[2])
                requests += decoded[0] to payload
                val query = payload.optString("s")
                if (query == "blocked") { started.countDown(); release.await(20, TimeUnit.SECONDS) }
                val body = when {
                    query == "error" -> """{"code":503,"message":"Service unavailable"}"""
                    decoded[0] == "/api/v3/song/detail" -> """{"code":200,"songs":[{"id":12345,"al":{"picUrl":"http://example.test/artwork.jpg"}}]}"""
                    else -> """{"code":200,"result":{"songCount":2,"songs":[{"id":12345,"name":"Sparse song","artists":[{"id":42,"name":"Artist"}],"album":{"id":73,"name":"Album"},"duration":180000,"sq":{"br":1000000}},{"id":12346,"name":"Authored song","ar":[{"id":43,"name":"Artist B"}],"al":{"id":74,"name":"Album B","picUrl":"https://example.test/b.jpg"},"dt":181000}]}}"""
                }.toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } finally { exchange.close() }
        }
        start()
    }
    private val http = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY)
        .eventListener(object : EventListener() { override fun canceled(call: Call) { cancelled.set(true) } })
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder()
            .scheme("http").host("127.0.0.1").port(server.address.port).build()).build()) }.build()
    @AfterTest fun cleanup() { release.countDown(); server.stop(0); workers.shutdownNow(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdownNow() }

    @Test fun canonicalSearchEnrichesMissingArtworkAndRetainsRealSongArtistAlbumAndDurationIds() = runBlocking<Unit> {
        val client = NeteaseSearchClient(httpClient = http)
        val songs = client.ensureArtwork(client.searchSongs("fixture"))
        assertEquals(listOf(12345L, 12346L), songs.map { it.id })
        assertEquals(42L, songs.first().artistId); assertEquals(73L, songs.first().albumId)
        assertEquals(180000L, songs.first().durationMs)
        assertEquals("https://example.test/artwork.jpg", songs.first().artworkUrl)
        assertEquals(melox.music.model.AudioQualityTier.Lossless, songs.first().bestQuality)
        assertEquals(1_000_000, songs.first().bestBitrate)
        assertNull(songs.last().bestQuality)
        assertEquals("/api/search/get", requests.first().first)
        assertEquals(1, requests.first().second.getInt("type"))
        assertEquals(30, requests.first().second.getInt("limit"))
        assertEquals("/api/v3/song/detail", requests.last().first)
        assertEquals(12345L, JSONObject(requests.last().second.getString("c").removePrefix("[").removeSuffix("]")).getLong("id"))
    }
    @Test fun providerSearchPreservesNativeCollectionIdsForUnifiedResultActions() = runBlocking<Unit> {
        val tracks = NeteaseProvider(httpClient = http).searchSongs("fixture", 1, 25).items
        assertEquals(MusicSource.Netease, tracks.first().id.source)
        assertEquals("12345", tracks.first().id.value)
        assertEquals("42", tracks.first().artists.first().id?.value)
        assertEquals("73", tracks.first().album?.id?.value)
        assertEquals(melox.music.model.AudioQualityTier.Lossless, tracks.first().bestQuality)
        assertEquals(1_000_000, tracks.first().bestBitrate)
    }
    @Test fun paginatedUniversalSearchUsesRealPinnedRouteAndPropagatesServiceErrors() = runBlocking<Unit> {
        val client = NeteaseUniversalSearchClient(httpClient = http)
        val page = client.searchSongs("fixture", 2, 40)
        assertEquals(2, page.items.size)
        assertEquals("/api/search/get", requests.first().first)
        assertEquals(40, requests.first().second.getInt("offset"))
        assertEquals(1, requests.first().second.getInt("type"))
        assertNull(page.items.first().artworkUrl)
        assertEquals(180000L, page.items.first().durationMs)
        assertEquals(melox.music.model.AudioQualityTier.Lossless, page.items.first().bestQuality)
        assertEquals(1_000_000, page.items.first().bestBitrate)
        assertFailsWith<IOException> { client.searchSongs("error") }
        assertFailsWith<IOException> { NeteaseSearchClient(httpClient = http).searchSongs("error") }
    }
    @Test fun replacingSearchCancelsTheActualInflightSocketWithoutPublishingOldResults() = runBlocking<Unit> {
        val work = async(Dispatchers.IO) { NeteaseSearchClient(httpClient = http).searchSongs("blocked") }
        assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
        withTimeout(2000) { work.cancelAndJoin() }
        assertTrue(work.isCancelled)
        assertTrue(cancelled.get(), "The coroutine must cancel its real OkHttp Call")
        release.countDown()
        assertEquals(2, NeteaseSearchClient(httpClient = http).searchSongs("fixture").size)
    }

    @Test fun repeatedQueriesAcrossClientsReuseOneRealHttpResponse() = runBlocking<Unit> {
        NeteaseSearchClient(httpClient = http).searchSongs("repeated")
        NeteaseUniversalSearchClient(httpClient = http).searchSongs("repeated")
        NeteaseSearchClient(httpClient = http).searchSongs("repeated")
        assertEquals(1, requests.count { it.first == "/api/search/get" })
        NeteaseUniversalSearchClient(httpClient = http).searchMedia("repeated", MeloXSearchKind.Albums)
        assertEquals(2, requests.count { it.first == "/api/search/get" })
    }

    @Test fun replacingMediaSearchCancelsTheActualSocket() = runBlocking<Unit> {
        val work = async(Dispatchers.IO) {
            NeteaseUniversalSearchClient(httpClient = http).searchMedia("blocked", MeloXSearchKind.Albums)
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
        withTimeout(2000) { work.cancelAndJoin() }
        assertTrue(cancelled.get(), "Media search must cancel its real OkHttp Call")
    }

    @Test fun replacingSuggestionsCancelsTheActualSocket() = runBlocking<Unit> {
        val work = async(Dispatchers.IO) {
            NeteaseUniversalSearchClient(httpClient = http).searchSuggestions("blocked")
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
        withTimeout(2000) { work.cancelAndJoin() }
        assertTrue(cancelled.get(), "Suggestions must cancel their real OkHttp Call")
    }
}
