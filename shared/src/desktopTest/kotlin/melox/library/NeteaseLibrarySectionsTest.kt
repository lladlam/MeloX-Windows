package melox.library

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.json.JSONObject
import kotlin.test.*

/** Loopback protocol fixture only; never installs an app account or contacts NetEase. */
class NeteaseLibrarySectionsTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val started = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val cancelled = AtomicBoolean()
    private var blocked = false
    private var empty = false
    private val workers = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = workers
        createContext("/") { exchange ->
            try {
                val path = exchange.requestURI.path
                requests += path
                check(exchange.requestHeaders.getFirst("Cookie").contains("MUSIC_U="))
                val form = exchange.requestBody.bufferedReader().use { it.readText() }
                if (path.startsWith("/eapi/")) {
                    val params = URLDecoder.decode(form.substringAfter("params="), "UTF-8")
                    val bytes = params.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
                    val payload = JSONObject(cipher.doFinal(bytes).toString(Charsets.UTF_8).split("-36cd479b6b5-")[1])
                    check(payload.getLong("uid") == 42L)
                }
                if (blocked) { started.countDown(); release.await(10, TimeUnit.SECONDS) }
                val body = when (path) {
                    "/eapi/user/playlist" -> """{"code":200,"playlist":[{"id":73,"name":"Owned","creator":{"userId":42}},{"id":74,"name":"Subscribed","subscribed":true,"creator":{"userId":9}}]}"""
                    "/eapi/song/like/get" -> if (empty) """{"code":200,"ids":[]}""" else """{"code":200,"ids":[1002,1001,1002]}"""
                    "/weapi/v3/song/detail" -> """{"code":200,"songs":[{"id":1001,"name":"First"},{"id":1002,"name":"Second"}]}"""
                    else -> """{"code":503,"message":"Unrelated endpoint must not be needed"}"""
                }.toByteArray()
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
    private val client = NeteaseLibraryClient({ "MUSIC_U=protocol-fixture-only" }, http)

    @AfterTest fun close() {
        release.countDown(); server.stop(0); workers.shutdownNow()
        http.connectionPool.evictAll(); http.dispatcher.executorService.shutdownNow()
    }

    @Test fun playlistsDoNotDependOnLikedSongsOrHistory() = runBlocking {
        assertEquals(listOf(73L, 74L), client.userPlaylists(42).map { it.id })
        assertEquals(listOf("/eapi/user/playlist"), requests.toList())
    }

    @Test fun likedSongsDoNotDependOnPlaylistsOrHistoryAndRetainIdOrder() = runBlocking {
        assertEquals(listOf(1002L, 1001L), client.likedSongs(42).map { it.id })
        assertEquals(listOf("/eapi/song/like/get", "/weapi/v3/song/detail"), requests.toList())
    }

    @Test fun genuinelyEmptyFavoritesDoNotFetchSongDetails() = runBlocking {
        empty = true
        assertTrue(client.likedSongs(42).isEmpty())
        assertEquals(listOf("/eapi/song/like/get"), requests.toList())
    }

    @Test fun leavingPlaylistsCancelsActualHttpCall() = runBlocking {
        blocked = true
        val work = async(Dispatchers.IO) { client.userPlaylists(42) }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        withTimeout(2000) { work.cancelAndJoin() }
        assertTrue(cancelled.get())
    }
}
