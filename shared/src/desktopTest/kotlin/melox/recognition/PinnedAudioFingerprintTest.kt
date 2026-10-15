package melox.recognition

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.Base64
import kotlin.math.*
import kotlin.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PinnedAudioFingerprintTest {
    @Test fun pinnedWasmProducesStableSignalDependentFingerprintsAndClosesEachIsolate() {
        fun signal(seed: Int) = FloatArray(8_000 * 3) { index ->
            val time = index / 8_000.0
            ((sin(2 * PI * (220 + seed * 113) * time) * .35 +
                sin(2 * PI * (440 + seed * 71) * time) * .2) * (.6 + .4 * sin(time * 9))).toFloat()
        }
        val first = PinnedAudioFingerprint.generate(signal(1))
        val again = PinnedAudioFingerprint.generate(signal(1))
        val different = PinnedAudioFingerprint.generate(signal(7))
        assertEquals(first, again)
        assertNotEquals(first, different)
        assertTrue(Base64.getDecoder().decode(first).size > 16)
        assertEquals(0, com.caoccao.javet.interop.V8Host.getV8Instance().v8RuntimeCount)
    }

    @Test fun responsePreservesNativeCollectionIdsStartTimeAndRejectsServiceError() {
        val response = JSONObject("""{"code":200,"data":{"result":[{"startTime":3456,"song":{"id":9,"name":"曲目","ar":[{"id":3,"name":"艺人"}],"al":{"id":4,"name":"专辑"},"dt":180000}},{"song":{"id":9}},{"song":{"id":0}}]}}""")
        val result = SongRecognitionClient.parseMatches(response).single()
        assertEquals(9L, result.song.neteaseSongId)
        assertEquals(3L, result.song.artistId)
        assertEquals(4L, result.song.albumId)
        assertEquals(3456L, result.startTimeMs)
        assertFailsWith<java.io.IOException> { SongRecognitionClient.parseMatches(JSONObject("""{"code":500,"message":"service unavailable"}""")) }
    }

    @Test fun closedClientCannotRecordOrDecode() = runBlocking<Unit> {
        val client = SongRecognitionClient()
        client.close()
        assertFailsWith<IllegalStateException> { client.recognize(3) }
        assertFailsWith<IllegalStateException> { client.recognizeLocal("missing.wav", 3_000) }
    }

    @Test fun realFileDecodeAndWasmReachTheMatchProtocolWithSignalData() = runBlocking<Unit> {
        val file = File(System.getProperty("user.home"), "recognition-generated.wav")
        val count = 44_100 * 3
        val buffer = ByteBuffer.allocate(44 + count * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + count * 4).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44_100).putInt(44_100 * 4).putShort(4).putShort(16)
            .put("data".toByteArray()).putInt(count * 4)
        repeat(count) { i ->
            val value = (sin(2 * PI * (200.0 + i / 400.0) * i / 44_100) * 20_000).toInt().toShort()
            buffer.putShort(value).putShort(value)
        }
        file.writeBytes(buffer.array())
        var captured: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test fixture")
                .body("""{"code":200,"data":{"result":[]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            // The standalone extractor must produce real non-silent 8 kHz PCM from FFmpeg,
            // with a sample count derived from decoded audio rather than fabricated padding.
            val pcm = LocalAudioSegmentExtractor.extractBlocking(file.toURI().toString(), segmentStartMs = 0, segmentLengthMs = 3_000)
            assertTrue(pcm.size in 23_000..24_100, "unexpected decoded length ${pcm.size}")
            assertTrue(pcm.any { kotlin.math.abs(it) > .01f }, "decoded segment is silent")
            assertTrue(pcm.all { it.isFinite() && it in -1f..1f })
            assertEquals(pcm.size, LocalAudioSegmentExtractor.extractBlocking(file.absolutePath, 0, 3_000).size)
            assertEquals(0, melox.player.FfmpegPcmDecoder.activeProcesses())

            SongRecognitionClient(client).use { assertTrue(it.recognizeLocal(file.toURI().toString(), 3_000, 0).isEmpty()) }
            val request = assertNotNull(captured)
            assertEquals("interface.music.163.com", request.url.host)
            assertEquals("/api/music/audio/match", request.url.encodedPath)
            assertEquals("shazam_v2", request.url.queryParameter("algorithmCode"))
            assertEquals("3", request.url.queryParameter("duration"))
            assertEquals("MeloX-Desktop/1.0", request.header("User-Agent"))
            assertTrue(Base64.getDecoder().decode(request.url.queryParameter("rawdata")).size > 16)
            assertEquals(0, melox.player.FfmpegPcmDecoder.activeProcesses())
        } finally { file.delete(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
}
