package melox.player

import kotlinx.coroutines.*
import melox.playback.MeloXAutoMixPlan
import melox.playback.MeloXAutoMixFadeCurve
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.SourceDataLine
import kotlin.math.*
import kotlin.test.*

/** Real simultaneous native decoders and signed PCM. Only the physical sound device is controlled. */
class DesktopDualPcmTest {
    private val root = Files.createTempDirectory("melox-dual-pcm").toFile()
    private val outputs = CopyOnWriteArrayList<Output>()
    private val sessions = CopyOnWriteArrayList<MeloXDualDecodeSession>()
    private val plan = MeloXAutoMixPlan(1600, 0, outgoingStartMs = 1000)
    @AfterTest fun release() { sessions.forEach { it.close() }; root.deleteRecursively() }
    private fun wave(name: String, hz: Int, ms: Int, amplitude: Int): File {
        val frames = ms * 44100 / 1000
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { frame ->
            val sample = (amplitude * sin(2 * PI * hz * frame / 44100.0)).roundToInt().toShort()
            bytes.putShort(sample); bytes.putShort(sample)
        }
        return File(root, name).apply { writeBytes(bytes.array()) }
    }
    private suspend fun until(check: () -> Boolean) = withTimeout(30_000) { while (!check()) delay(10) }
    private fun session(a: File, b: File, handoff: () -> Unit = {}, fallback: (Throwable) -> Unit = {}): MeloXDualDecodeSession =
        MeloXDualDecodeSession(MeloXDeckSource(a.path), MeloXDeckSource(b.path), plan, MeloXAutoMixFadeCurve.Linear,
            { Output().also(outputs::add).line }, bassSwap = false, onHandoff = handoff, onFallback = fallback).also(sessions::add)
    private fun magnitude(bytes: ByteArray, hz: Int, startMs: Int, lengthMs: Int): Double {
        val start = startMs * 44100 / 1000
        val size = lengthMs * 44100 / 1000
        var real = 0.0; var imaginary = 0.0
        repeat(size) { index ->
            val value = MeloXPcmMix.sample(bytes, (start + index) * 4).toDouble()
            val phase = 2 * PI * hz * index / 44100.0
            real += value * cos(phase); imaginary += value * sin(phase)
        }
        return 2 * hypot(real, imaginary) / size
    }
    @Test fun twoDecodedFrequencyComponentsCrossfadeAndHandoffOnlyAfterRenderedFrames() = runBlocking<Unit> {
        val a = wave("primary.wav", 220, 2600, 6000)
        val b = wave("incoming.wav", 880, 2200, 8000)
        val handoffs = AtomicLong()
        val failures = CopyOnWriteArrayList<Throwable>()
        val engine = session(a, b, { handoffs.incrementAndGet() }, failures::add)
        val run = async(Dispatchers.IO) { engine.run() }
        until {
            assertTrue(failures.isEmpty(), "Valid decoded incoming fell back: ${failures.map { it.message }}; accepted=${outputs.firstOrNull()?.accepted?.get()}")
            outputs.firstOrNull()?.accepted?.get() == 3200L * 44100 * 4 / 1000
        }
        val output = outputs.single()
        assertFalse(run.isCompleted)
        assertEquals(0L, engine.clock().renderedFrames)
        assertEquals(0L, handoffs.get())
        val bytes = output.bytes()
        val aStart = magnitude(bytes, 220, 100, 100)
        val bStart = magnitude(bytes, 880, 100, 100)
        val aMid = magnitude(bytes, 220, 1750, 100)
        val bMid = magnitude(bytes, 880, 1750, 100)
        val aTail = magnitude(bytes, 220, 2800, 100)
        val bTail = magnitude(bytes, 880, 2800, 100)
        assertTrue(aStart in 5990.0..6010.0 && bStart < 1)
        assertTrue(aMid in 2900.0..3100.0 && bMid in 3900.0..4100.0, "actual overlap $aMid / $bMid")
        assertTrue(aTail < 1 && bTail in 7990.0..8010.0)
        output.consume()
        val result = withTimeout(30_000) { run.await() }
        assertEquals(70560L, result.overlapFrames)
        assertEquals(114660L, result.primaryFrames)
        assertEquals(97020L, result.incomingFrames)
        assertEquals(result.writtenFrames, result.renderedFrames)
        assertEquals(1L, handoffs.get())
        assertEquals(1, engine.clock().deck)
        assertEquals(2200L, engine.clock().positionMs)
        assertTrue(output.closed.get())
        assertEquals(0, FfmpegPcmDecoder.activeProcesses())
        println("Actual dual-decoder PCM: $result; overlap projections 220Hz=$aMid 880Hz=$bMid; sha256=" +
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }
    @Test fun pauseSeekZeroFlushesBothOldDecksAndRestartsSelectedAudioWithPauseIntent() = runBlocking<Unit> {
        val a = wave("seek-primary.wav", 220, 2600, 6000)
        val b = wave("seek-incoming.wav", 880, 2200, 8000)
        val handoffs = AtomicLong()
        val engine = session(a, b, { handoffs.incrementAndGet() })
        val run = async(Dispatchers.IO) { engine.run() }
        until { outputs.firstOrNull()?.accepted?.get() == 564480L }
        engine.pause()
        engine.seek(0)
        until { outputs.size == 2 }
        delay(100)
        assertEquals(0L, outputs.last().accepted.get())
        assertTrue(outputs.first().closed.get())
        assertEquals(0L, handoffs.get())
        engine.resume()
        until { outputs.last().accepted.get() == 458640L }
        assertContentEquals(a.readBytes().copyOfRange(44, a.length().toInt()), outputs.last().bytes())
        outputs.last().consume()
        withTimeout(30_000) { run.await() }
        assertEquals(0, engine.clock().deck)
        assertEquals(2600L, engine.clock().positionMs)
        assertEquals(0, FfmpegPcmDecoder.activeProcesses())
    }
    @Test fun genuineDualDecodeAppliesUserEqualizerToPrimaryOverlapAndIncomingOnSameClock() = runBlocking<Unit> {
        val preferences = melox.platform.getPreferences("melox_app_settings")
        val keys = listOf("equalizer_enabled", "equalizer_preset", "equalizer_custom_band_5", "equalizer_preamp_db")
        val before = keys.associateWith { preferences.getString(it, null) }
        try {
            preferences.putValues(mapOf("equalizer_enabled" to "true", "equalizer_preset" to "Custom", "equalizer_custom_band_5" to "-6.0", "equalizer_preamp_db" to "0.0"))
            melox.playback.MeloXEqualizerSettings.refresh()
            val engine = session(wave("eq-primary.wav", 1000, 2600, 6000), wave("eq-incoming.wav", 4000, 2200, 8000))
            val run = async(Dispatchers.IO) { engine.run() }
            until { outputs.firstOrNull()?.accepted?.get() == 564480L }
            val bytes = outputs.single().bytes()
            assertTrue(magnitude(bytes, 1000, 100, 100) in 2980.0..3040.0)
            assertTrue(magnitude(bytes, 1000, 1750, 100) in 1450.0..1580.0)
            assertTrue(magnitude(bytes, 4000, 1750, 100) in 3650.0..4050.0)
            assertTrue(magnitude(bytes, 4000, 2800, 100) in 7500.0..8010.0)
            outputs.single().consume()
            val result = withTimeout(30_000) { run.await() }
            assertEquals(result.writtenFrames, result.renderedFrames)
            assertEquals(70560L, result.overlapFrames)
            assertEquals(0L, result.clippedSamples)
            retainNativePcmEvidence("dual-eq-band5-cut", bytes, "generated primary 1000Hz amplitude 6000 / incoming 4000Hz amplitude 8000; linear overlap 1600ms at primary 1000ms; band 5 -6dB; preamp 0")
            assertTrue(outputs.single().closed.get())
            assertEquals(0, FfmpegPcmDecoder.activeProcesses())
        } finally { preferences.putValues(before); melox.playback.MeloXEqualizerSettings.refresh() }
    }
    @Test fun invalidIncomingFileFallsBackToActualPrimaryPcmAndNeverHandsOff() = runBlocking<Unit> {
        val a = wave("fallback.wav", 220, 2600, 6000)
        val b = File(root, "invalid.wav").apply { writeText("invalid audio") }
        val failures = CopyOnWriteArrayList<Throwable>()
        val handoffs = AtomicLong()
        val engine = session(a, b, { handoffs.incrementAndGet() }, failures::add)
        val run = async(Dispatchers.IO) { engine.run() }
        until { outputs.firstOrNull()?.accepted?.get() == 458640L }
        assertContentEquals(a.readBytes().copyOfRange(44, a.length().toInt()), outputs.single().bytes())
        outputs.single().consume()
        assertEquals(0L, withTimeout(30_000) { run.await() }.overlapFrames)
        assertEquals(1, failures.size)
        assertEquals(0L, handoffs.get())
        assertEquals(0, FfmpegPcmDecoder.activeProcesses())
    }
    @Test fun cancellationReleasesBothNativeProcessesAndSuppressesLateHandoff() = runBlocking<Unit> {
        val handoffs = AtomicLong()
        val engine = session(wave("stop-a.wav", 220, 2600, 6000), wave("stop-b.wav", 880, 2200, 8000), { handoffs.incrementAndGet() })
        val run = async(Dispatchers.IO) { engine.run() }
        until { outputs.firstOrNull()?.accepted?.get() == 564480L }
        run.cancelAndJoin()
        assertEquals(0L, handoffs.get())
        assertTrue(outputs.single().closed.get())
        assertEquals(0, FfmpegPcmDecoder.activeProcesses())
    }
    @Test fun nativeTempoRampChangesDurationWhilePreservingPitch() {
        val file = wave("tempo.wav", 440, 3000, 6000)
        val bytes = ByteArrayOutputStream()
        FfmpegPcmDecoder(file.path, filters = MeloXPcmTempo.filter(1.05f, 1f, 1600)).use { decoder ->
            val buffer = ByteArray(3528)
            while (true) { val count = decoder.read(buffer); if (count == 0) break; bytes.write(buffer, 0, count) }
            decoder.verifyExit()
        }
        val decoded = bytes.toByteArray()
        val duration = decoded.size * 1000.0 / 176400
        assertTrue(duration in 2930.0..2980.0, "Measured tempo-ramp duration $duration")
        assertTrue(magnitude(decoded, 440, 500, 100) > 5700, "Pitch changed")
        assertEquals(0, FfmpegPcmDecoder.activeProcesses())
        println("Native pitch-preserving tempo ramp duration_ms=$duration")
    }
    @Test fun softwareBassSwapActuallyAttenuatesLowEnergyWithoutCuttingTrebleOrWrappingPcm() {
        val a = wave("bass.wav", 100, 1000, 6000).readBytes().drop(44).toByteArray()
        val b = wave("treble.wav", 4000, 1000, 8000).readBytes().drop(44).toByteArray()
        val flat = a.copyOf(); val equalized = a.copyOf()
        MeloXPcmMix(false).mix(flat, b, flat.size, 500_000_000, 1_000_000_000, MeloXAutoMixFadeCurve.EqualPower)
        MeloXPcmMix(true).mix(equalized, b, equalized.size, 500_000_000, 1_000_000_000, MeloXAutoMixFadeCurve.EqualPower)
        val bassRatio = magnitude(equalized, 100, 500, 100) / magnitude(flat, 100, 500, 100)
        val trebleRatio = magnitude(equalized, 4000, 500, 100) / magnitude(flat, 4000, 500, 100)
        assertTrue(bassRatio < .5, "bass ratio $bassRatio")
        assertTrue(trebleRatio in .95..1.05, "treble ratio $trebleRatio")
        val full = ByteArray(400) { if (it % 2 == 0) 0xff.toByte() else 0x7f }
        val mixer = MeloXPcmMix(false)
        mixer.mix(full, full.copyOf(), full.size, 500_000_000, 1_000_000_000, MeloXAutoMixFadeCurve.EqualPower)
        assertTrue(mixer.clippedSamples > 0)
        assertTrue((0 until full.size step 2).all { MeloXPcmMix.sample(full, it) == 32767 })
        println("Real software shelf: bass_ratio=$bassRatio treble_ratio=$trebleRatio")
    }
    private class Output {
        val accepted = AtomicLong(); val rendered = AtomicLong(); val closed = AtomicBoolean(false); val running = AtomicBoolean(false)
        private val data = ByteArrayOutputStream()
        fun bytes(): ByteArray = synchronized(data) { data.toByteArray() }
        fun consume() { check(running.get()); rendered.set(accepted.get() / 4) }
        val line: SourceDataLine = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> {
                    check(!closed.get())
                    val count = minOf(args!![2] as Int, 2048)
                    synchronized(data) { data.write(args[0] as ByteArray, args[1] as Int, count) }
                    accepted.addAndGet(count.toLong()); count
                }
                "getLongFramePosition" -> rendered.get()
                "start" -> { running.set(true); null }
                "stop" -> { running.set(false); null }
                "close" -> { running.set(false); closed.set(true); null }
                "isRunning", "isActive" -> running.get()
                "isOpen" -> !closed.get()
                "toString" -> "ControlledDualOutput"
                else -> error("Unexpected output method ${method.name}")
            }
        } as SourceDataLine
    }
}
