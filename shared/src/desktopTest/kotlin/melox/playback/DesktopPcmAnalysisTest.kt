package melox.playback

import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.*
import kotlin.test.*

/** Legal generated WAV/PCM: tempo, silence and frequency content are known independently of the analyzer. */
class DesktopPcmAnalysisTest {
    private val rate = 44_100
    private fun pcm(seconds: Double, sample: (Double) -> Double): ByteArray {
        val buffer = ByteBuffer.allocate((seconds * rate).toInt() * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(buffer.capacity() / 4) { frame ->
            val value = (sample(frame.toDouble() / rate).coerceIn(-1.0, 1.0) * 32767).roundToInt().toShort()
            buffer.putShort(value); buffer.putShort(value)
        }
        return buffer.array()
    }
    private fun beats(bpm: Double) = pcm(24.0) { time ->
        if (time < 2 || time > 22) 0.0 else {
            val phase = (time - 2) % (60 / bpm)
            if (phase < .085) .65 * sin(2 * PI * 130 * phase) * exp(-phase * 35) else 0.0
        }
    }
    private fun wav(file: File, pcm: ByteArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(pcm.size + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16).put("data".toByteArray()).putInt(pcm.size)
        file.outputStream().use { it.write(header.array()); it.write(pcm) }
    }
    @Test fun knownBeatRatesAndSilentBoundariesAreMeasuredFromSignedPcm() {
        val analyzer = MeloXAutoMixAudioAnalyzer()
        for (bpm in listOf(90.0, 105.0, 140.0)) {
            val result = analyzer.analyzePcm(beats(bpm), rate)
            assertTrue(abs(result.bpm - bpm) < 4, "$bpm BPM measured ${result.bpm}")
            assertTrue(result.confidence > .42, "confidence ${result.confidence} for $bpm")
            assertTrue(result.firstAudibleMs in 1850..2100, "first=${result.firstAudibleMs}")
            assertTrue(result.lastAudibleMs in 21000..22300, "last=${result.lastAudibleMs}")
            assertTrue(result.beatTimesMs.size > 20)
            assertTrue(result.downbeatTimesMs.size > 5)
            assertTrue(result.phraseBoundariesMs.toList().zipWithNext().all { it.first < it.second })
        }
    }
    @Test fun silenceHasNoBpmOrSyntheticBeatTimeline() {
        val result = MeloXAutoMixAudioAnalyzer().analyzePcm(ByteArray(rate * 4 * 3), rate)
        assertEquals(0.0, result.bpm); assertEquals(0.0, result.confidence)
        assertTrue(result.beatTimesMs.isEmpty()); assertTrue(result.downbeatTimesMs.isEmpty())
        assertTrue(result.frames.all { it.energy == 0f })
    }
    @Test fun longFractionalFrameBeatGridTracksActualOnsets() {
        val bpm = 120.0
        val start = 2.17
        val audio = pcm(80.0) { time ->
            val phase = (time - start) % (60 / bpm)
            if (time >= start && phase < .06) .65 * sin(2 * PI * 130 * phase) * exp(-phase * 35) else 0.0
        }
        val result = MeloXAutoMixAudioAnalyzer().analyzePcm(audio, rate)
        assertTrue(abs(result.bpm - bpm) < 1.0, "tempo=${result.bpm}")
        val errors = (0..140).map { index ->
            val expected = ((start + index * 60 / bpm) * 1000).roundToLong()
            result.beatTimesMs.minOf { abs(it - expected) }
        }
        println("80-second 120 BPM PCM: measured=${result.bpm}, mean beat error=${errors.average()}ms, max=${errors.max()}ms")
        assertTrue(errors.average() < 70, "mean beat error=${errors.average()}ms, max=${errors.max()}ms")
        assertTrue(errors.max() < 120, "beat grid drift=${errors.max()}ms")
    }
    @Test fun fftBandsDistinguishBassMidAndTrebleWithoutUnsignedByteEnergy() {
        for ((frequency, band) in listOf(100 to 0, 1000 to 1, 4000 to 2)) {
            val result = MeloXAutoMixAudioAnalyzer().analyzePcm(pcm(3.0) { .4 * sin(2 * PI * frequency * it) }, rate)
            val average = result.frames.drop(2).map { listOf(it.lowRatio, it.midRatio, it.highRatio)[band] }.average()
            assertTrue(average > .95, "$frequency Hz wrong band: $average")
        }
    }
    @Test fun bundledFfmpegAnalysisAgreesWithRawPcmAndConcurrentWaiters() = runBlocking {
        val root = Files.createTempDirectory("melox-analysis").toFile()
        try {
            val audio = beats(105.0)
            val file = File(root, "beat105.wav").also { wav(it, audio) }
            val analyzer = MeloXAutoMixAudioAnalyzer()
            val first = async { analyzer.analyze("generated:105", file.path) }
            val second = async { analyzer.analyze("generated:105", file.path) }
            val result = withTimeout(30_000) { first.await() }
            assertSame(result, second.await())
            val raw = analyzer.analyzePcm(audio, rate)
            assertEquals(raw.bpm, result.bpm)
            assertEquals(raw.firstAudibleMs, result.firstAudibleMs)
            assertEquals(raw.lastAudibleMs, result.lastAudibleMs)
            assertContentEquals(raw.beatTimesMs, result.beatTimesMs)
        } finally { root.deleteRecursively() }
    }
    @Test fun malformedAndShortAudioAreRejectedRatherThanReturningDefaults() = runBlocking<Unit> {
        val analyzer = MeloXAutoMixAudioAnalyzer()
        assertFailsWith<IllegalArgumentException> { analyzer.analyzePcm(ByteArray(3), rate) }
        assertFailsWith<IllegalArgumentException> { analyzer.analyzePcm(ByteArray(100), rate) }
        val root = Files.createTempDirectory("melox-invalid-analysis").toFile()
        try {
            val file = File(root, "invalid.wav").apply { writeText("not an audio file") }
            assertFailsWith<java.io.IOException> { analyzer.analyze("invalid", file.path) }
        } finally { root.deleteRecursively() }
    }
}
