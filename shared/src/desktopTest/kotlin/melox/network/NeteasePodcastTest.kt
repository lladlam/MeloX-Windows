package melox.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NeteasePodcastTest {
    private fun client(response: String, paths: MutableList<String> = mutableListOf()) =
        NeteaseUniversalSearchClient(httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            paths.add(chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(response.toResponseBody()).build()
        }.build())

    @Test fun programPagingHonorsServerMoreAndRetainsPlayableIdentity() = runBlocking {
        val paths = mutableListOf<String>()
        val program = """{"id":123,"name":"节目标题","description":"节目简介","coverUrl":"http://img.test/cover","duration":0,"dj":{"nickname":"主播"},"radio":{"id":99,"name":"播客"},"mainSong":{"id":456,"name":"原始音频","duration":7000}}"""
        val result = client("""{"code":200,"more":true,"programs":[$program]}""", paths).podcastPrograms(99)
        assertTrue(result.hasMore)
        val episode = result.values.single()
        assertEquals(123, episode.id)
        assertEquals("节目简介", episode.description)
        assertEquals(456, episode.playbackSong?.id)
        assertEquals("节目标题", episode.playbackSong?.name)
        assertEquals("主播", episode.playbackSong?.artists)
        assertEquals("播客", episode.playbackSong?.album)
        assertEquals("https://img.test/cover", episode.playbackSong?.artworkUrl)
        assertEquals(7000, episode.durationMs)
        assertEquals(listOf("/eapi/dj/program/byradio"), paths)
        assertFalse(client("""{"code":200,"more":false,"count":100,"programs":[$program]}""").podcastPrograms(99).hasMore)
        assertTrue(client("""{"code":200,"count":2,"programs":[$program]}""").podcastPrograms(99).hasMore)
    }

    @Test fun subscribedPagingHonorsServerHasMore() = runBlocking {
        val radio = """{"id":99,"name":"播客"}"""
        assertTrue(client("""{"code":200,"hasMore":true,"djRadios":[$radio]}""").subscribedPodcasts(0, 1).hasMore)
        assertFalse(client("""{"code":200,"hasMore":false,"count":100,"djRadios":[$radio]}""").subscribedPodcasts(0, 1).hasMore)
        assertTrue(client("""{"code":200,"count":2,"djRadios":[$radio]}""").subscribedPodcasts(0, 1).hasMore)
    }

    @Test fun detailAcceptsBothResponseShapes() = runBlocking {
        val paths = mutableListOf<String>()
        val program = """{"id":123,"name":"节目","radio":{"id":99,"name":"播客"}}"""
        assertEquals(123, client("""{"code":200,"program":$program}""", paths).podcastProgramDetail(123)?.id)
        assertEquals(99, client("""{"code":200,"data":$program}""").podcastProgramDetail(123)?.radioId)
        assertEquals(listOf("/eapi/dj/program/detail"), paths)
    }

    @Test fun secondProgramPageUsesTheRequestedOffsetAndOrder() = runBlocking {
        val requests = mutableListOf<JSONObject>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val encrypted = (chain.request().body as FormBody).value(0).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
            val payload = String(cipher.doFinal(encrypted), Charsets.UTF_8).split("-36cd479b6b5-")[1]
            requests.add(JSONObject(payload))
            val response = if (requests.size == 1)
                """{"code":200,"more":true,"programs":[{"id":1}]}"""
            else """{"code":200,"more":false,"programs":[{"id":2}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody()).build()
        }.build()
        val client = NeteaseUniversalSearchClient(httpClient = http)
        val first = client.podcastPrograms(99, limit = 1, ascending = true)
        val second = client.podcastPrograms(99, offset = first.values.size, limit = 1, ascending = true)
        assertEquals(listOf(1L, 2L), (first.values + second.values).map { it.id })
        assertTrue(first.hasMore)
        assertFalse(second.hasMore)
        assertEquals(listOf(0, 1), requests.map { it.getInt("offset") })
        assertTrue(requests.all { it.getLong("radioId") == 99L && it.getBoolean("asc") })
    }

    @Test fun cancellingDetailCancelsItsHttpCall() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(10)
            cancelled.set(chain.call().isCanceled())
            throw java.io.IOException("fixture stopped")
        }.build()
        val client = NeteaseUniversalSearchClient(httpClient = http)
        val request = launch(Dispatchers.Default) { client.podcastDetail(987654321) }
        assertTrue(started.await(5, TimeUnit.SECONDS), "detail request did not start")
        withTimeout(5_000) { request.cancelAndJoin() }
        assertTrue(cancelled.get(), "cancellation did not reach the underlying HTTP call")
    }
}
