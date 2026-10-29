package melox.autosync

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import melox.player.FfmpegPcmDecoder
import melox.player.FfmpegLocator
import melox.lyrics.LrcLyricsParser
import melox.lyrics.KugouKrcLyricsParser
import melox.lyrics.LyricLine
import melox.lyrics.LyricQuality
import melox.lyrics.LyricSyllable
import melox.lyrics.LyricsDocument
import melox.lyrics.QQMusicQrcLyricsParser

class AutoSyncAudioAndTextTest {
    @Test fun realFfmpegDecodesBoundedMono16kWindows() = runBlocking<Unit> {
        val root = Files.createTempDirectory("melox-autosync-audio-test-").toFile()
        try {
            val wav = File(root, "original.wav")
            writeStereoWave(wav)
            val originalLength = wav.length()
            AutoSyncAudioDecoder.withPcm(wav.absolutePath, emptyMap(), 250, 1_250) { pcm ->
                assertEquals(16_000L, pcm.sampleCount)
                val samples = pcm.window(500, 1_000)
                assertEquals(8_000, samples.size)
                val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
                assertTrue(rms in .18.. .25, "Actual decoded signal RMS: $rms")
            }
            assertEquals(originalLength, wav.length(), "The source recording must remain intact")
            assertEquals(0, FfmpegPcmDecoder.activeProcesses())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationClosesBlockedFfmpegNetworkRead() = runBlocking<Unit> {
        // The first lookup verifies/extracts the large packaged decoder. Do not
        // count that cold-start I/O against the network connection deadline.
        assertTrue(FfmpegLocator.command().isNotEmpty(), "Bundled FFmpeg is missing")
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val connected = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread(isDaemon = true, name = "autosync-stalled-audio") {
            runCatching {
                server.accept().use { socket ->
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: 1000000\r\n\r\n".toByteArray())
                    socket.getOutputStream().flush()
                    connected.countDown()
                    release.await()
                }
            }
        }
        try {
            val job = launch { AutoSyncAudioDecoder.withPcm("http://127.0.0.1:${server.localPort}/audio", emptyMap(), 0, 5_000) { error("No audio was sent") } }
            assertTrue(withContext(Dispatchers.IO) { connected.await(30, TimeUnit.SECONDS) }, "FFmpeg did not connect")
            assertTrue(job.isActive, "FFmpeg must be blocked on the stalled response before cancellation")
            withTimeout(5_000) { job.cancelAndJoin() }
            assertEquals(0, FfmpegPcmDecoder.activeProcesses(), "Cancellation must release the native decoder")
        } finally {
            release.countDown(); server.close(); worker.join(1_000)
        }
    }

    @Test fun routesAndMapsTextWithoutInventingMixedOrCreditTimings() {
        assertEquals(AutoSyncLanguage.English, AutoSyncText.language("Hello world"))
        assertEquals(AutoSyncLanguage.Chinese, AutoSyncText.language("你好世界"))
        assertEquals(AutoSyncLanguage.Japanese, AutoSyncText.language("愛してる"))
        assertEquals(AutoSyncLanguage.Unknown, AutoSyncText.language("爱 and love"))
        assertFalse(AutoSyncText.alignable("作曲：Someone"))
        assertFalse(AutoSyncText.alignable("[instrumental]"))
        assertEquals("HELLO WORLD", AutoSyncText.english("Hello, world!"))
        assertEquals("WE'RE HERE", AutoSyncText.english("We’re here"))

        val tokens = listOf("<pad>", "<s>", "</s>", "<unk>", "|", "H", "E", "L", "O", "W", "R", "D")
        val manifest = AutoSyncModelManifest("en", "ctc", "input_values", null, listOf("log_probs"), 0, 4, 3, .02, tokens, "test")
        val ids = AutoSyncText.englishIds("HELLO WORLD", manifest)
        val spans = ids.mapIndexed { index, token -> CtcForcedAligner.Span(token, index, index, 0.0) }
        assertEquals(listOf(AutoSyncUnit("Hello", 0, 100), AutoSyncUnit("world", 120, 220)),
            AutoSyncText.englishWords("Hello, world!", "HELLO WORLD", ids, spans, manifest))
        val contraction = "We’re"
        val contractManifest = manifest.copy(tokens = tokens + "'")
        val normalized = AutoSyncText.english(contraction)
        val contractIds = AutoSyncText.englishIds(normalized, contractManifest)
        val contractSpans = contractIds.mapIndexed { index, token -> CtcForcedAligner.Span(token, index, index, 0.0) }
        assertEquals(contraction, AutoSyncText.englishWords(contraction, normalized, contractIds, contractSpans, contractManifest).single().text)
        assertTrue(AutoSyncText.englishIds("HELLO 2", contractManifest).isEmpty(), "Unknown spoken tokens must keep line timing")
        assertFalse(AutoSyncText.supportsEnglishSurface("Hello café"), "Do not silently omit unsupported letters")

        val chinese = manifest.copy(language = "zh", tokens = listOf("<blank>", "你", "好", "1", "2"))
        val zhSpans = listOf(ZhCifPostprocess.TokenSpan(10, 20), ZhCifPostprocess.TokenSpan(20, 40))
        assertEquals(listOf(AutoSyncUnit("你", 10, 20), AutoSyncUnit("好", 20, 40)),
            AutoSyncText.chineseCharacters("你，好！", AutoSyncText.chinese("你，好！"), chinese, zhSpans))
        assertTrue(AutoSyncText.chineseCharacters("１２", AutoSyncText.chinese("１２"), chinese, zhSpans).isEmpty(),
            "NFKC positions that cannot be mapped to displayed text must stay line-timed")
    }

    @Test fun timestampsFromProviderLrcAreEligibleButPlainAndAuthoredLyricsAreNot() {
        val lrc = LrcLyricsParser.parse("[00:01.00]Hello world\n[00:05.00]Good night")
        assertEquals(LyricQuality.Fallback, lrc.quality) // This parser retains its historical provider quality.
        assertTrue(MeloXAutoSync.eligible(lrc))
        assertTrue(MeloXAutoSync.eligible(KugouKrcLyricsParser.parse("[1000,2000]Hello world\n[5000,2000]Good night")))
        assertTrue(MeloXAutoSync.eligible(QQMusicQrcLyricsParser.parse("[1000,2000]Hello world\n[5000,2000]Good night")))
        assertFalse(MeloXAutoSync.eligible(LyricsDocument(listOf(LyricLine(0, text = "plain lyrics")))))
        assertFalse(MeloXAutoSync.eligible(lrc.copy(lines = lrc.lines.mapIndexed { i, line ->
            if (i == 0) line.copy(syllables = listOf(LyricSyllable("Hello", 1_000, 1_500))) else line
        })))
        assertFalse(MeloXAutoSync.eligible(lrc.copy(lines = lrc.lines.reversed())))
    }

    @Test fun timelineCacheRequiresExactSourceAudioAndModelAndKeepsOtherLines() {
        val root = Files.createTempDirectory("melox-autosync-timeline-test-").toFile()
        try {
            val store = AutoSyncTimelineStore(root)
            val document = LrcLyricsParser.parse("[00:01.00]Hello world\n[00:05.00]作曲：Somebody")
            val source = AutoSyncTimelineStore.sourceHash(document)
            val units = mapOf(0 to listOf(AutoSyncUnit("Hello", 1_100, 1_400), AutoSyncUnit("world", 1_500, 2_000)))
            store.save("track", source, "audio", "model", units)
            assertEquals(units, store.load("track", source, "audio", "model"))
            assertEquals(null, store.load("track", source, "different-audio", "model"))
            assertEquals(null, store.load("track", source, "audio", "different-model"))
            assertEquals(null, store.load("track", AutoSyncTimelineStore.sourceHash(document.copy(lines = document.lines.drop(1))), "audio", "model"))
            val merged = requireNotNull(AutoSyncTimelineStore.merge(document, units))
            assertEquals(listOf("Hello", "world"), merged.lines[0].syllables.map { it.text })
            assertEquals(document.lines[1], merged.lines[1])
            assertFalse(merged.pseudoTimingAllowed)
        } finally { root.deleteRecursively() }
    }

    private fun writeStereoWave(file: File) {
        val frames = 3 * 44_100
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44_100).putInt(176_400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { frame ->
            val sample = (10_000 * sin(2 * PI * 440 * frame / 44_100.0)).toInt().toShort()
            bytes.putShort(sample); bytes.putShort(sample)
        }
        file.writeBytes(bytes.array())
    }
}
