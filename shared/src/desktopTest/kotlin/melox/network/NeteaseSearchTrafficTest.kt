package melox.network

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

class NeteaseSearchTrafficTest {
    @Test fun searchCacheDeduplicatesAndSeparatesSessionsAndKinds() {
        var time = 0L
        val cache = NeteaseSearchResponseCache(100) { time }
        var calls = 0
        fun search(cookie: String = "session-a", kind: Int = 1): JSONObject = cache.get(
            "/api/search/get", JSONObject().put("s", "测试").put("type", kind), cookie, null,
        ) { calls++; JSONObject().put("code", 200).put("result", calls) }
        search().put("result", 999)
        assertEquals(1, search().getInt("result"))
        assertEquals(1, calls)
        search("session-b"); search(kind = 10)
        assertEquals(3, calls)
        time = 101; search()
        assertEquals(4, calls)
    }

    @Test fun concurrentIdenticalSearchesOnlyLoadOnce() {
        val cache = NeteaseSearchResponseCache()
        val calls = AtomicInteger()
        val pool = Executors.newFixedThreadPool(6)
        try {
            val tasks = (1..6).map { pool.submit<Int> {
                cache.get("/api/search/get", JSONObject().put("s", "同一搜索"), "", null) {
                    calls.incrementAndGet(); Thread.sleep(50); JSONObject().put("code", 200)
                }.getInt("code")
            } }
            tasks.forEach { assertEquals(200, it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, calls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun failedAndCancelledSearchesAreNotReused() {
        val cache = NeteaseSearchResponseCache()
        val data = JSONObject().put("s", "retry")
        assertFailsWith<IOException> { cache.get("/api/search/get", data, "", null) { throw IOException("failure") } }
        val owner = Job().apply { cancel() }
        assertFailsWith<CancellationException> { cache.get("/api/search/get", data, "", owner) { error("must not execute") } }
        assertEquals(200, cache.get("/api/search/get", data, "", null) { JSONObject().put("code", 200) }.getInt("code"))
    }

    @Test fun rateLimitStopsOtherApiHostsUntilCooldownButNotAudio() {
        var time = 0L
        val governor = NeteaseRequestGovernor(0, 1000) { time }
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor(governor).addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":-462,"message":"操作频繁"}""".toResponseBody()).build()
        }.build()
        fun request(url: String) = client.newCall(Request.Builder().url(url).build()).execute().close()
        assertFailsWith<NeteaseRateLimitException> { request("https://interface.music.163.com/eapi/search/get") }
        assertFailsWith<NeteaseRateLimitException> { request("https://music.163.com/weapi/search/get") }
        assertEquals(1, calls.get())
        request("https://m801.music.126.net/audio.mp3")
        assertEquals(2, calls.get())
        time = 1001
        assertFailsWith<NeteaseRateLimitException> { request("https://music.163.com/api/search/get") }
        assertEquals(3, calls.get())
    }

    @Test fun normalErrorsDoNotTriggerRiskCooldown() {
        val governor = NeteaseRequestGovernor()
        assertFalse(governor.isRateLimitedBody("""{"code":301,"message":"请先登录"}"""))
        assertFalse(governor.isRateLimitedBody("""{"code":200,"result":{"songs":[]}}"""))
        assertFalse(governor.isApi("music.163.com.evil.example", "/eapi/search/get"))
        assertFalse(governor.isApi("p1.music.126.net", "/image.jpg"))
        assertTrue(governor.isRateLimitedBody("""{"code":405,"message":"请求过多"}"""))
    }

    @Test fun apiBurstIsSpacedAndConcurrencyIsBounded() {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val starts = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val client = OkHttpClient.Builder().addInterceptor(NeteaseRequestGovernor(40)).addInterceptor { chain ->
            starts.add(System.nanoTime())
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            try { Thread.sleep(120) } finally { active.decrementAndGet() }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":200}""".toResponseBody()).build()
        }.build()
        val pool = Executors.newFixedThreadPool(6)
        try {
            (1..6).map { pool.submit {
                client.newCall(Request.Builder().url("https://music.163.com/api/search/get").build()).execute().close()
            } }.forEach { it.get(5, TimeUnit.SECONDS) }
            assertTrue(peak.get() <= 2)
            assertEquals(6, starts.size)
            starts.sorted().zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 30_000_000, "Requests started in a burst") }
        } finally { pool.shutdownNow() }
    }
}
