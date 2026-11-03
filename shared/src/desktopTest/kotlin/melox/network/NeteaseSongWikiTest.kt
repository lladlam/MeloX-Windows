package melox.network

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeteaseSongWikiTest {
    private val blocks = """[{"code":"SONG_PLAY_ABOUT_SONG_BASIC","creatives":[{"uiElement":{"mainTitle":{"title":"创作信息"},"descriptions":[{"text":"词曲"}],"buttons":[{"title":"更多"}]},"resources":[{"uiElement":{"mainTitle":{"title":"词曲"},"subTitles":[{"title":"专辑"}]}}]}],"resources":[{"resourceType":"FIRST_LISTEN","uiElement":{"mainTitle":{"title":"初听"},"descriptions":[{"description":"回忆"}]},"extensionInfo":{"musicFirstListen":{"date":"2020-01-01"}}},{"resourceType":"TOTAL_PLAY","extensionInfo":{"musicTotalPlay":{"playCount":42}}}]}]"""

    @Test fun anonymousAndExpiredSessionReturnAllWikiBlocks() = runBlocking {
        val cookies = mutableListOf<String?>()
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            cookies.add(request.header("Cookie"))
            paths.add(request.url.encodedPath)
            val body = if (cookies.size == 1) """{"code":401,"message":"expired"}"""
                else """{"code":200,"blocks":$blocks}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val client = NeteaseMusicOperationsClient({ "MUSIC_U=expired" }, http)
        val section = client.songWiki(123).single()
        assertEquals("歌曲信息", section.title)
        assertEquals(listOf("创作信息", "词曲", "更多", "专辑", "初听", "回忆", "第一次听：2020-01-01", "累计播放：42 次"), section.lines)
        assertEquals(listOf("/eapi/song/play/about/block/page", "/eapi/song/play/about/block/page"), paths)
        assertTrue(cookies.first().orEmpty().contains("MUSIC_U=expired"))
        assertEquals(null, cookies.last())
    }

    @Test fun anonymousUsesNestedBlocksWithoutLogin() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(null, chain.request().header("Cookie"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":200,"data":{"blocks":$blocks}}""".toResponseBody()).build()
        }.build()
        assertEquals("歌曲信息", NeteaseMusicOperationsClient({ "" }, http).songWiki(123).single().title)
    }

    @Test fun malformedAuthenticatedResponseFallsBackAnonymously() = runBlocking {
        val cookies = mutableListOf<String?>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            cookies.add(chain.request().header("Cookie"))
            val body = if (cookies.size == 1) "<html>expired</html>" else """{"code":200,"blocks":$blocks}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        assertEquals("歌曲信息", NeteaseMusicOperationsClient({ "MUSIC_U=expired" }, http).songWiki(123).single().title)
        assertTrue(cookies.first().orEmpty().contains("MUSIC_U=expired"))
        assertEquals(null, cookies.last())
    }

    @Test fun cancellingRequestCancelsHttpAndDoesNotStartAnonymousFallback() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val fallback = AtomicBoolean(false)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            if (started.count == 0L) fallback.set(true)
            started.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(10)
            cancelled.set(chain.call().isCanceled())
            throw IOException("fixture stopped")
        }.build()
        val request = launch(Dispatchers.Default) { NeteaseMusicOperationsClient({ "MUSIC_U=expired" }, http).songWiki(123) }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        withTimeout(5_000) { request.cancelAndJoin() }
        assertTrue(cancelled.get())
        assertFalse(fallback.get())
    }
}
