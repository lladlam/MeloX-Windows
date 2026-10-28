package melox.provider.lxuser

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.sun.net.httpserver.HttpServer
import com.caoccao.javet.interop.V8Host
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import melox.music.model.AudioQualityTier
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import melox.playback.LxUserPlaybackResolver
import melox.player.FfmpegPcmDecoder
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class LxUserRuntimeTest {
    @Test fun asyncHttpHeadersTimersAndConcurrentCallsRunInRealV8() {
        val seen = AtomicInteger()
        val fixture = OkHttpClient.Builder().addInterceptor { chain ->
            if (seen.incrementAndGet() == 1) Thread.sleep(2_300)
            assertEquals("application/json", chain.request().header("Accept"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .addHeader("Set-Cookie", "a=1").addHeader("Set-Cookie", "b=2")
                .body("""{"data":{"url":"https://example.invalid/audio"}}""".toResponseBody()).build()
        }.build()
        val source = """
            globalThis.lx.on('request', ({source, info}) => new Promise((resolve, reject) => {
              lx.request('https://example.invalid/api', {}, (err, resp, body) => {
                if (err) reject(err); else setTimeout(() => resolve({url: body.url, id: info.musicInfo.id,
                  cookie: resp.headers['set-cookie'], source, status: resp.statusCode}), 5)
              })
            }));
            lx.send('inited', {sources:{tx:{type:'music',qualitys:['128k','320k'],actions:['musicUrl']}}});
        """.trimIndent()
        val pool = Executors.newFixedThreadPool(2)
        try {
            LxUserRuntime(fixture).use { runtime ->
                runtime.load(LxUserScript(source))
                assertTrue(runtime.supportsSource("tx")); assertFalse(runtime.supportsSource("kg"))
                assertFalse(runtime.supportsAction("tx", "lyric")); assertEquals("320k", runtime.qualityFor("tx", "flac"))
                val futures = (1..2).map { id -> pool.submit<Any?> {
                    runtime.callAction("musicUrl", mapOf("source" to "tx", "musicInfo" to mapOf("id" to id)))
                } }
                futures.forEachIndexed { index, future ->
                    val result = future.get(10, TimeUnit.SECONDS) as Map<*, *>
                    assertEquals(index + 1, result["id"]); assertEquals("https://example.invalid/audio", result["url"])
                    assertEquals("a=1, b=2", result["cookie"]); assertEquals("tx", result["source"]); assertEquals(200, result["status"])
                }
                assertEquals(2, seen.get())
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun buffersCryptoErrorsAndCommonJsAreAvailable() {
        val source = """
            globalThis.module.exports.musicUrl = (song, quality) => {
              const b = lx.utils.buffer;
              const c = lx.utils.crypto;
              let modeError = false;
              try { c.aesEncrypt([], 'unsupported', [], []); } catch (_) { modeError = true }
              return {songmid:song.songmid, quality, md5:c.md5('abc'), hex:b.bufToString(b.from('6869','hex'),'hex'),
                binary:b.bufToString(b.from('aGk=','base64'),'binary'), random:c.randomBytes(8).length, modeError,
                typed:b.from('hi') instanceof Uint8Array && c.randomBytes(2) instanceof Uint8Array}
            };
        """.trimIndent()
        LxUserRuntime().use { runtime ->
            runtime.load(LxUserScript(source))
            val result = runtime.callAction("musicUrl", mapOf("source" to "wy", "type" to "320k", "musicInfo" to mapOf("songmid" to "123"))) as Map<*, *>
            assertEquals("123", result["songmid"]); assertEquals("320k", result["quality"])
            assertEquals("900150983cd24fb0d6963f7d28e17f72", result["md5"]); assertEquals("6869", result["hex"])
            assertEquals(listOf(104, 105), result["binary"]); assertEquals(8, result["random"]); assertEquals(true, result["modeError"])
            assertEquals(true, result["typed"])
        }
    }

    @Test fun sandboxDeniesDynamicCodeAndClosesItsV8Isolates() {
        val host = V8Host.getV8Instance()
        val before = host.v8RuntimeCount
        repeat(3) {
            LxUserRuntime().use { runtime ->
                runtime.load(LxUserScript("""
                    globalThis.lx.on('request', () => {
                      let evalBlocked = false, functionBlocked = false;
                      try { eval('1') } catch (_) { evalBlocked = true }
                      try { (async function(){}).constructor('return 1') } catch (_) { functionBlocked = true }
                      return {evalBlocked, functionBlocked, nativeHidden: typeof globalThis.__lxNative === 'undefined'};
                    });
                """.trimIndent()))
                val result = runtime.callAction("musicUrl", mapOf("source" to "wy")) as Map<*, *>
                assertEquals(true, result["evalBlocked"]); assertEquals(true, result["functionBlocked"])
                assertEquals(true, result["nativeHidden"])
            }
        }
        assertEquals(before, host.v8RuntimeCount)
    }

    @Test fun asynchronousConfigurationCanRegisterTheHandler() {
        val fixture = OkHttpClient.Builder().addInterceptor { chain ->
            Thread.sleep(150)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"ready\":true}".toResponseBody()).build()
        }.build()
        LxUserRuntime(fixture).use { runtime ->
            runtime.load(LxUserScript("""
                lx.request('https://example.invalid/config', {}, (err, resp, body) => {
                  if (err || !body.ready) return;
                  lx.on('request', ({info}) => 'https://example.invalid/' + info.musicInfo.songmid);
                  lx.send('inited', {sources:{wy:{type:'music',qualitys:['128k'],actions:['musicUrl']}}});
                });
            """.trimIndent()))
            assertTrue(runtime.supportsSource("wy")); assertFalse(runtime.supportsSource("tx"))
            assertEquals("https://example.invalid/123", runtime.callAction("musicUrl", mapOf("source" to "wy", "musicInfo" to mapOf("songmid" to "123"))))
        }
    }

    @Test fun interruptionCancelsRequestsAndEventuallyClosesTheIsolate() {
        val host = V8Host.getV8Instance()
        val before = host.v8RuntimeCount
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val interrupted = java.util.concurrent.atomic.AtomicBoolean(false)
        val fixture = OkHttpClient.Builder().addInterceptor { chain ->
            entered.countDown(); release.await(5, TimeUnit.SECONDS)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("late".toResponseBody()).build()
        }.build()
        val runtime = LxUserRuntime(fixture)
        try {
            runtime.load(LxUserScript("""
                lx.on('request', () => new Promise(resolve => lx.request('https://example.invalid/wait', {},
                    (err, resp, body) => resolve(body))));
            """.trimIndent()))
            val caller = Thread {
                try { runtime.callAction("musicUrl", mapOf("source" to "wy")) }
                catch (_: InterruptedException) { interrupted.set(Thread.currentThread().isInterrupted) }
                finally { finished.countDown() }
            }
            caller.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            caller.interrupt()
            assertTrue(finished.await(5, TimeUnit.SECONDS)); assertTrue(interrupted.get())
            assertFailsWith<IllegalStateException> { runtime.supportsSource("wy") }
            release.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (host.v8RuntimeCount != before && System.nanoTime() < deadline) Thread.sleep(10)
            assertEquals(before, host.v8RuntimeCount)
        } finally { release.countDown(); runtime.close() }
    }

    @Test fun multipartRequestsCarryTheirGeneratedBoundary() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = java.util.concurrent.CompletableFuture<Pair<String, String>>()
        server.createContext("/form") { exchange ->
            received.complete(exchange.requestHeaders.getFirst("Content-Type").orEmpty() to
                exchange.requestBody.readBytes().toString(Charsets.ISO_8859_1))
            val response = "ok".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            LxUserRuntime().use { runtime ->
                runtime.load(LxUserScript("""
                    lx.on('request', () => new Promise((resolve, reject) =>
                      lx.request('http://127.0.0.1:${server.address.port}/form',
                        {method:'POST',headers:{'Content-Type':'multipart/form-data'},formData:{title:'song',data:[1,2,3]}},
                        (err, resp, body) => err ? reject(err) : resolve(body))));
                """.trimIndent()))
                assertEquals("ok", runtime.callAction("musicUrl", mapOf("source" to "wy")))
                val (contentType, body) = received.get(5, TimeUnit.SECONDS)
                val boundary = contentType.substringAfter("boundary=", "").trim('"')
                assertTrue(contentType.startsWith("multipart/form-data;")); assertTrue(boundary.isNotBlank())
                assertTrue(body.contains("--$boundary")); assertTrue(body.contains("name=\"title\""))
                assertTrue(body.contains("song")); assertTrue(body.contains("name=\"data\""))
            }
        } finally { server.stop(0) }
    }

    @Test fun requestFailureIsAnErrorAndDoesNotExposeAccountCookies() {
        val fixture = OkHttpClient.Builder().addInterceptor { throw IOException("fixture network failure") }.build()
        LxUserRuntime(fixture).use { runtime ->
            runtime.load(LxUserScript("""
                lx.on('request', () => new Promise(resolve => lx.request('https://example.invalid', {}, (err) =>
                  resolve({isError:err instanceof Error, message:err.message}))));
            """.trimIndent()))
            val value = runtime.callAction("lyric", mapOf("source" to "wy")) as Map<*, *>
            assertEquals(true, value["isError"]); assertEquals("fixture network failure", value["message"])
        }
    }

    @Test fun redirectAcrossOriginsDropsSensitiveHeaders() {
        val destination = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = java.util.concurrent.CompletableFuture<Map<String, String?>>()
        destination.createContext("/final") { exchange ->
            received.complete(mapOf("cookie" to exchange.requestHeaders.getFirst("Cookie"),
                "authorization" to exchange.requestHeaders.getFirst("Authorization"),
                "key" to exchange.requestHeaders.getFirst("X-API-Key"),
                "normal" to exchange.requestHeaders.getFirst("X-Normal")))
            val response = "ok".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        destination.start()
        origin.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${destination.address.port}/final")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        origin.start()
        try {
            LxUserRuntime().use { runtime ->
                runtime.load(LxUserScript("""
                    lx.on('request', () => new Promise((resolve, reject) => {
                      lx.request('http://127.0.0.1:${origin.address.port}/redirect',
                        {headers:{Cookie:'private=1',Authorization:'secret','X-API-Key':'key','X-Normal':'kept'}},
                        (err, resp, body) => err ? reject(err) : resolve(body));
                    }));
                """.trimIndent()))
                assertEquals("ok", runtime.callAction("musicUrl", mapOf("source" to "wy")))
                val headers = received.get(5, TimeUnit.SECONDS)
                assertEquals(null, headers["cookie"]); assertEquals(null, headers["authorization"])
                assertEquals(null, headers["key"]); assertEquals("kept", headers["normal"])
            }
        } finally { origin.stop(0); destination.stop(0) }
    }

    @Test fun sourceStoreAndPlaybackResolverUseFlatTrackIdentity() {
        val source = """
            // @name LX fixture
            // @version 1
            globalThis.lx.on('request', ({source,info}) => {
              const m = info.musicInfo;
              if (source !== 'tx' || m.strMediaMid !== 'media-mid' || m.songmid !== 'song-mid') throw Error('wrong track');
              return 'https://example.invalid/' + info.type + '/' + m.strMediaMid;
            });
            lx.send('inited', {sources:{tx:{type:'music',qualitys:['128k','320k'],actions:['musicUrl']}}});
        """.trimIndent()
        val record = LxUserSourceStore.importScript(source)
        try {
            assertEquals(source, LxUserSourceStore.script(record.id))
            assertEquals("LX fixture", LxUserSourceStore.list().first { it.id == record.id }.name)
            val track = MusicTrack(MusicResourceId(MusicSource.QQMusic, "song-mid"), "song", listOf(MusicArtistRef(name = "artist")),
                providerMetadata = ProviderTrackMetadata.QQMusic("song-mid", "media-mid"))
            val result = LxUserPlaybackResolver().resolve(track, AudioQualityTier.High)
            assertEquals("https://example.invalid/320k/media-mid", result?.url)
            assertEquals(record.id, result?.sourceId)
            val cached = LxUserRuntime.session(record.id, source)
            assertTrue(cached === LxUserRuntime.session(record.id, source))
        } finally { LxUserSourceStore.remove(record.id) }
        assertEquals(null, LxUserSourceStore.script(record.id))
        val replacement = LxUserRuntime.session(record.id, source)
        try { assertTrue(replacement.supportsSource("tx")) } finally { LxUserRuntime.evict(record.id) }
    }

    @Test fun invalidScriptsAndEscapedPathsAreRejectedWithoutTouchingOtherFiles() {
        assertFailsWith<IllegalArgumentException> { LxUserSourceStore.importScript("hello") }
        assertEquals(null, LxUserSourceStore.script("../outside"))
    }

    @Test fun concurrentResolutionUsesRealHttpAndFfmpegPcm() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val decodingRequests = AtomicInteger()
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        val wav = toneWav()
        server.createContext("/resolve") { exchange ->
            requests.incrementAndGet(); requested.countDown()
            release.await(5, TimeUnit.SECONDS)
            val response = """{"data":{"url":"http://127.0.0.1:${server.address.port}/tone.wav"}}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/tone.wav") { exchange ->
            decodingRequests.incrementAndGet()
            exchange.responseHeaders.set("Content-Type", "audio/wav")
            exchange.sendResponseHeaders(200, wav.size.toLong())
            exchange.responseBody.use { it.write(wav) }
        }
        server.start()
        val script = """
            // @name LX PCM fixture
            // @version 1
            globalThis.lx.on('request', ({source, info}) => new Promise((resolve, reject) => {
              if (source !== 'wy' || info.musicInfo.songmid !== '123') return reject(Error('wrong track'));
              lx.request('http://127.0.0.1:${server.address.port}/resolve', {}, (err, resp, body) =>
                err ? reject(err) : resolve({data: {url: body.url}}));
            }));
            lx.send('inited', {sources:{wy:{type:'music',qualitys:['128k'],actions:['musicUrl']}}});
        """.trimIndent()
        val record = LxUserSourceStore.importScript(script)
        val clients = Executors.newFixedThreadPool(2)
        try {
            val track = MusicTrack(MusicResourceId(MusicSource.Netease, "123"), "song", listOf(MusicArtistRef(name = "artist")))
            val resolver = LxUserPlaybackResolver()
            val first = clients.submit<LxUserPlaybackResultForTest> {
                val result = resolver.resolve(track, AudioQualityTier.Standard)
                LxUserPlaybackResultForTest(result?.url, result?.sourceId)
            }
            assertTrue(requested.await(10, TimeUnit.SECONDS))
            val second = clients.submit<LxUserPlaybackResultForTest> {
                val result = resolver.resolve(track, AudioQualityTier.Standard)
                LxUserPlaybackResultForTest(result?.url, result?.sourceId)
            }
            Thread.sleep(150)
            release.countDown()
            val result = first.get(15, TimeUnit.SECONDS)
            assertEquals(result, second.get(15, TimeUnit.SECONDS))
            assertEquals(record.id, result.sourceId); assertEquals(1, requests.get())
            val pcm = ByteArray(4096)
            FfmpegPcmDecoder(checkNotNull(result.url)).use { decoder ->
                assertEquals(pcm.size, decoder.read(pcm))
                assertTrue(pcm.any { it != 0.toByte() })
            }
            assertTrue(decodingRequests.get() > 0)
        } finally {
            release.countDown(); clients.shutdownNow(); LxUserSourceStore.remove(record.id); server.stop(0)
        }
    }

    private data class LxUserPlaybackResultForTest(val url: String?, val sourceId: String?)

    private fun toneWav(): ByteArray {
        val samples = 4_410
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36 + samples * 2); buffer.put("WAVEfmt ".toByteArray())
        buffer.putInt(16); buffer.putShort(1); buffer.putShort(1); buffer.putInt(44_100)
        buffer.putInt(88_200); buffer.putShort(2); buffer.putShort(16); buffer.put("data".toByteArray()); buffer.putInt(samples * 2)
        repeat(samples) { buffer.putShort((kotlin.math.sin(2 * Math.PI * 440 * it / 44_100) * 12_000).toInt().toShort()) }
        return buffer.array()
    }
}
