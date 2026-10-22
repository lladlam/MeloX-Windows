package melox.lan

import com.sun.net.httpserver.HttpServer
import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Semaphore
import melox.playback.DesktopPlaybackRequest
import org.json.JSONObject
import okhttp3.Request

/** Session-only, IP/queue-bound proxy. No upstream URL or credential is sent to a guest. */
internal class LanAudioRelay(private val bind: InetAddress, private val peer: InetAddress,
    tracks: List<LanTrack>, private val resolve: (LanTrack) -> DesktopPlaybackRequest) : AutoCloseable {
    private val allowed = tracks.associateBy { it.id }
    private val issuedAt = System.nanoTime()
    private val used = AtomicBoolean()
    private val active = AtomicBoolean(true)
    private val permits = Semaphore(4)
    private val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val workers = Executors.newFixedThreadPool(4) { Thread(it, "MeloX-LAN-relay").apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress(bind, 0), 4).apply {
        executor = workers
        createContext("/audio") { exchange ->
            try {
                val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate {
                    val p = it.split('=', limit = 2)
                    p[0] to URLDecoder.decode(p.getOrElse(1) { "" }, "UTF-8")
                }
                val elapsed = System.nanoTime() - issuedAt
                val track = allowed[query["songId"]]
                if (!active.get() || exchange.remoteAddress.address != peer || query["token"] != token || track == null ||
                    (!used.get() && elapsed > 10_000_000_000L) || elapsed > 21_600_000_000_000L) {
                    exchange.sendResponseHeaders(403, -1); return@createContext
                }
                if (exchange.requestMethod !in listOf("GET", "HEAD")) { exchange.sendResponseHeaders(405, -1); return@createContext }
                if (!permits.tryAcquire()) { exchange.sendResponseHeaders(503, -1); return@createContext }
                try {
                    used.set(true)
                    val source = resolve(track)
                    val range = exchange.requestHeaders.getFirst("Range")
                    val uri = runCatching { URI(source.uri) }.getOrNull()
                    if (uri?.scheme in listOf("http", "https")) {
                        val request = Request.Builder().url(source.uri).apply {
                            source.headers.forEach { (key, value) -> header(key, value) }
                            if (range != null) header("Range", range)
                            if (exchange.requestMethod == "HEAD") head()
                        }.build()
                        melox.network.MeloXHttpClient.shared.newCall(request).execute().use { response ->
                            // Never forward Set-Cookie, redirects or upstream error bodies.
                            if (response.code !in listOf(200, 206, 416)) { exchange.sendResponseHeaders(502, -1); return@use }
                            listOf("Content-Type", "Content-Range", "Accept-Ranges", "Content-Length").forEach { h ->
                                response.header(h)?.let { exchange.responseHeaders.set(h, it) }
                            }
                            if (exchange.requestMethod == "HEAD" || response.code == 416) exchange.sendResponseHeaders(response.code, -1)
                            else {
                                exchange.sendResponseHeaders(response.code, response.body.contentLength().takeIf { it > 0 } ?: 0)
                                response.body.byteStream().use { it.copyTo(exchange.responseBody, 32768) }
                            }
                        }
                    } else {
                        val file = if (uri?.scheme == "file") File(uri) else File(source.uri)
                        require(file.isFile && file.canRead())
                        val length = file.length()
                        val match = range?.let { Regex("bytes=(\\d*)-(\\d*)").matchEntire(it) }
                        var start = 0L; var end = length - 1
                        if (range != null) {
                            if (match == null) { exchange.sendResponseHeaders(416, -1); return@createContext }
                            val a = match.groupValues[1]; val b = match.groupValues[2]
                            if (a.isBlank()) start = (length - (b.toLongOrNull() ?: 0)).coerceAtLeast(0)
                            else { start = a.toLong(); if (b.isNotBlank()) end = minOf(end, b.toLong()) }
                        }
                        if (start !in 0 until length || end < start) {
                            exchange.responseHeaders.set("Content-Range", "bytes */$length")
                            exchange.sendResponseHeaders(416, -1); return@createContext
                        }
                        val count = end - start + 1
                        exchange.responseHeaders.set("Accept-Ranges", "bytes")
                        exchange.responseHeaders.set("Content-Length", count.toString())
                        if (range != null) exchange.responseHeaders.set("Content-Range", "bytes $start-$end/$length")
                        exchange.sendResponseHeaders(if (range == null) 200 else 206, if (exchange.requestMethod == "HEAD") -1 else count)
                        if (exchange.requestMethod != "HEAD") RandomAccessFile(file, "r").use { input ->
                            input.seek(start); val buffer = ByteArray(32768); var remaining = count
                            while (remaining > 0 && active.get()) {
                                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()); if (n < 0) break
                                exchange.responseBody.write(buffer, 0, n); remaining -= n
                            }
                        }
                    }
                } finally { permits.release() }
            } catch (_: Exception) { runCatching { exchange.sendResponseHeaders(502, -1) } }
            finally { exchange.close() }
        }
        start()
    }
    fun description() = JSONObject().put("baseUrl", "http://${bind.hostAddress}:${server.address.port}").put("token", token)
    override fun close() { if (active.compareAndSet(true, false)) { server.stop(0); workers.shutdownNow() } }
}
