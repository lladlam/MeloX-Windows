package melox.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

/** One budget for all NetEase API hosts; media/CDN traffic never enters this queue. */
internal class NeteaseRequestGovernor(
    private val spacingMs: Long = 350,
    private val cooldownMs: Long = 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : Interceptor {
    private val monitor = Object()
    private var active = 0
    private var nextStart = 0L
    private var blockedUntil = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!isApi(url.host, url.encodedPath)) return chain.proceed(chain.request())
        synchronized(monitor) {
            while (true) {
                if (chain.call().isCanceled()) throw IOException("请求已取消")
                val time = now()
                if (time < blockedUntil) throw NeteaseRateLimitException(blockedUntil - time)
                if (active < 2 && time >= nextStart) {
                    active++
                    nextStart = time + spacingMs
                    break
                }
                try {
                    monitor.wait(if (active >= 2) 100L else (nextStart - time).coerceIn(1, 100))
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("网易云请求等待已中断", error)
                }
            }
        }
        try {
            val response = chain.proceed(chain.request())
            val limited = try {
                response.code == 429 || isRateLimitedBody(response.peekBody(16_384).string())
            } catch (error: Exception) {
                response.close()
                throw error
            }
            if (limited) {
                val retry = response.header("Retry-After")
                val retryMs = retry?.toLongOrNull()?.coerceIn(0, 86_400)
                    ?.let { TimeUnit.SECONDS.toMillis(it) }
                    ?: runCatching {
                        java.time.ZonedDateTime.parse(retry, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli() - System.currentTimeMillis()
                    }.getOrDefault(0L).coerceIn(0L, 86_400_000L)
                val wait = maxOf(cooldownMs, retryMs)
                synchronized(monitor) {
                    blockedUntil = maxOf(blockedUntil, now() + wait)
                    monitor.notifyAll()
                }
                response.close()
                throw NeteaseRateLimitException(wait)
            }
            return response
        } finally {
            synchronized(monitor) { active--; monitor.notifyAll() }
        }
    }

    internal fun isApi(host: String, path: String): Boolean =
        (host == "music.163.com" || host.endsWith(".music.163.com")) &&
            listOf("/api/", "/eapi/", "/weapi/").any(path::startsWith)

    internal fun isRateLimitedBody(body: String): Boolean {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false
        if (json.optInt("code") in listOf(429, -462)) return true
        val message = json.optString("message") + json.optString("msg")
        return message.contains("请求过多") || message.contains("too many requests", ignoreCase = true) ||
            (message.contains("频繁") && listOf("请求", "操作", "访问").any(message::contains))
    }
}

internal class NeteaseRateLimitException(waitMs: Long) : IOException(
    "网易云请求过于频繁，已暂停请求，请在${(waitMs + 999) / 1000}秒后重试",
)
