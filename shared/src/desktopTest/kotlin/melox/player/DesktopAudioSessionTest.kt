package melox.player

import java.io.File
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import kotlin.test.*

/** Actual production FFmpeg/process/PCM loop; only physical hardware is controlled. */
open class DesktopAudioSessionTest {
    protected open val autoMix: Boolean = false
    private val originalOutput = AudioPlayer.outputLineFactory
    private val directory = File(System.getProperty("user.home"), "audio-session-${UUID.randomUUID()}").apply { mkdirs() }
    private val outputs = CopyOnWriteArrayList<ControlledOutput>()
    private val track = MusicTrack(MusicResourceId(MusicSource.Local, "test-tone"), "test-tone", emptyList(), durationMs = 500)

    @BeforeTest fun controlledDevice() {
        AudioPlayer.stop()
        melox.playback.MeloXPlaybackModePreferences.reset()
        melox.playback.MeloXPlaybackModePreferences.setAutoMix(autoMix)
        AudioPlayer.outputLineFactory = { ControlledOutput().also(outputs::add).line }
    }

    @AfterTest fun cleanup() {
        AudioPlayer.stop()
        AudioPlayer.outputLineFactory = originalOutput
        melox.playback.MeloXPlaybackModePreferences.reset()
        directory.deleteRecursively()
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) = withTimeout(15_000) {
        while (!predicate()) delay(10)
    }

    private fun wav(name: String = "tone.wav", frames: Int = 22_050, corrupt: Boolean = false): File {
        val bytes = frames * AudioPlayer.PCM_BYTES_PER_FRAME
        val declared = if (corrupt) bytes * 2 else bytes
        val buffer = ByteBuffer.allocate(44 + bytes + if (corrupt) 1 else 0).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + declared).put("WAVEfmt ".toByteArray()).putInt(16)
        buffer.putShort(1).putShort(2).putInt(44_100).putInt(44_100 * 4).putShort(4).putShort(16)
        buffer.put("data".toByteArray()).putInt(declared)
        repeat(frames * 2) { buffer.putShort(if (it % 100 < 50) 1000 else -1000) }
        if (corrupt) buffer.put(1)
        return File(directory, name).apply { writeBytes(buffer.array()) }
    }

    @Test fun decoderEofWaitsForBufferedFramesAndPauseResumePreserveTheTail() = runBlocking<Unit> {
        val ended = CompletableDeferred<Unit>()
        AudioPlayer.play(wav().absolutePath, track, onEnded = { ended.complete(Unit) })
        awaitCondition { outputs.firstOrNull()?.acceptedBytes?.get() == 88_200L }
        val output = outputs.single()
        assertTrue(AudioPlayer.isReady())
        assertFalse(AudioPlayer.state.value.isFinished)
        assertFalse(ended.isCompleted)
        AudioPlayer.pause()
        delay(100)
        assertFalse(output.running.get())
        assertTrue(AudioPlayer.state.value.isPaused)
        assertFalse(ended.isCompleted)
        assertFalse(output.closed.get())
        AudioPlayer.resume()
        output.consumeAll()
        withTimeout(15_000) { ended.await() }
        assertEquals(500L, AudioPlayer.getCurrentPositionMs())
        assertTrue(AudioPlayer.state.value.isFinished)
        assertTrue(output.closed.get())
        assertEquals(88_200L, output.acceptedBytes.get()) // partial writes retained all frames
    }

    @Test fun seekingToTheDecoderOriginalZeroPositionActuallyRestartsIt() = runBlocking<Unit> {
        val ended = CompletableDeferred<Unit>()
        val failures = CopyOnWriteArrayList<AudioPlaybackFailure>()
        AudioPlayer.play(wav().absolutePath, track, onEnded = { ended.complete(Unit) }, onFailure = failures::add)
        awaitCondition { outputs.firstOrNull()?.acceptedBytes?.get() == 88_200L }
        AudioPlayer.seekTo(0)
        awaitCondition { outputs.size == 2 && outputs.last().acceptedBytes.get() == 88_200L }
        assertTrue(outputs.first().closed.get())
        assertFalse(ended.isCompleted)
        assertEquals(0L, AudioPlayer.getCurrentPositionMs())
        outputs.last().consumeAll()
        withTimeout(15_000) { ended.await() }
        assertTrue(failures.isEmpty())
        assertEquals(500L, AudioPlayer.getCurrentPositionMs())
    }

    @Test fun realFfmpegNonzeroExitAfterPcmIsContentFailureRatherThanNaturalEnd() = runBlocking<Unit> {
        val failed = CompletableDeferred<AudioPlaybackFailure>()
        val ended = AtomicBoolean(false)
        AudioPlayer.play(wav("corrupt.wav", 44_100, corrupt = true).absolutePath, track,
            onEnded = { ended.set(true) }, onFailure = { failed.complete(it) })
        val failure = withTimeout(15_000) { failed.await() }
        assertTrue(outputs.single().acceptedBytes.get() > 0)
        assertEquals(AudioFailureKind.Content, failure.kind)
        assertTrue(failure.message.orEmpty().contains("corrupt input packet"))
        assertFalse(ended.get())
        assertFalse(AudioPlayer.state.value.isFinished)
        assertFalse(AudioPlayer.state.value.isPlaying)
        assertNotNull(AudioPlayer.state.value.error)
    }

    @Test fun replacingTheSameUriKeepsEndCallbacksOwnedByTheNewRequest() = runBlocking<Unit> {
        val file = wav()
        val oldEnded = AtomicBoolean(false)
        val newEnded = CompletableDeferred<Unit>()
        val failures = CopyOnWriteArrayList<AudioPlaybackFailure>()
        AudioPlayer.play(file.absolutePath, track, onEnded = { oldEnded.set(true) }, onFailure = failures::add)
        awaitCondition { outputs.firstOrNull()?.acceptedBytes?.get() == 88_200L }
        AudioPlayer.play(file.absolutePath, track, onEnded = { newEnded.complete(Unit) }, onFailure = failures::add)
        awaitCondition { outputs.size == 2 && outputs.last().acceptedBytes.get() == 88_200L }
        assertTrue(outputs.first().closed.get())
        outputs.last().consumeAll()
        withTimeout(15_000) { newEnded.await() }
        assertFalse(oldEnded.get())
        assertTrue(failures.isEmpty())
    }

    @Test fun unavailableDeviceReportsOutputFailureWithoutProducingAnEndEvent() = runBlocking<Unit> {
        AudioPlayer.outputLineFactory = { throw LineUnavailableException("test device unavailable") }
        val failed = CompletableDeferred<AudioPlaybackFailure>()
        val ended = AtomicBoolean(false)
        AudioPlayer.play(wav().absolutePath, track, onEnded = { ended.set(true) }, onFailure = { failed.complete(it) })
        val failure = withTimeout(15_000) { failed.await() }
        assertEquals(AudioFailureKind.Output, failure.kind)
        assertEquals("test device unavailable", AudioPlayer.state.value.error)
        assertFalse(ended.get())
        assertFalse(AudioPlayer.state.value.isLoading)
    }

    @Test fun seekingToKnownTrackEndAcceptsAZeroPcmSuccessfulDecoderExit() = runBlocking<Unit> {
        val ended = CompletableDeferred<Unit>()
        val failures = CopyOnWriteArrayList<AudioPlaybackFailure>()
        AudioPlayer.play(wav().absolutePath, track, startPositionMs = 500,
            onEnded = { ended.complete(Unit) }, onFailure = failures::add)
        withTimeout(15_000) { ended.await() }
        assertEquals(0L, outputs.single().acceptedBytes.get())
        assertEquals(500L, AudioPlayer.getCurrentPositionMs())
        assertTrue(AudioPlayer.state.value.isFinished)
        assertEquals(1f, AudioPlayer.progress.value)
        assertTrue(failures.isEmpty())
    }

    @Test fun aDeviceStartFailureIsOutputFailureRatherThanCorruptContent() = runBlocking<Unit> {
        AudioPlayer.outputLineFactory = { ControlledOutput(failOnStart = true).also(outputs::add).line }
        val failed = CompletableDeferred<AudioPlaybackFailure>()
        AudioPlayer.play(wav().absolutePath, track, onFailure = { failed.complete(it) })
        assertEquals(AudioFailureKind.Output, withTimeout(15_000) { failed.await() }.kind)
        assertEquals(AudioFailureKind.Output, AudioPlayer.state.value.errorKind)
        assertFalse(AudioPlayer.state.value.isFinished)
    }

    @Test fun pausedWriteReturningZeroRetainsPcmAndResumesOnTheSameDevice() = runBlocking<Unit> {
        val output = InterruptedWriteOutput()
        AudioPlayer.outputLineFactory = { output.line }
        val file = wav()
        val failures = CopyOnWriteArrayList<AudioPlaybackFailure>()
        val ended = CompletableDeferred<Unit>()
        AudioPlayer.play(file.path, track, onEnded = { ended.complete(Unit) }, onFailure = failures::add)
        awaitCondition { output.waiting.get() }
        val beforePause = output.pcm()
        assertTrue(beforePause.isNotEmpty())
        AudioPlayer.pause()
        awaitCondition { output.returned.get() }
        delay(80)
        assertNull(AudioPlayer.state.value.error, "Stopping a blocked SourceDataLine.write may legally return zero")
        assertTrue(failures.isEmpty())
        assertContentEquals(beforePause, output.pcm(), "Paused output must not accept more frames")
        assertFalse(output.closed.get())
        AudioPlayer.resume()
        awaitCondition { output.pcm().size == 88_200 || failures.isNotEmpty() }
        assertTrue(failures.isEmpty())
        output.consumeAll()
        withTimeout(15_000) { ended.await() }
        assertContentEquals(file.readBytes().copyOfRange(44, 88_244), output.pcm())
        assertEquals(500L, AudioPlayer.getCurrentPositionMs())
        assertTrue(output.closed.get())
    }

    @Test fun quickPauseResumeBeforeInterruptedWriteReturnsKeepsZeroAndShortWriteRemainders() = runBlocking<Unit> {
        for (partial in listOf(0, 1024)) {
            val output = InterruptedWriteOutput(partial = partial, holdReturn = true)
            AudioPlayer.outputLineFactory = { output.line }
            val file = wav()
            val failures = CopyOnWriteArrayList<AudioPlaybackFailure>()
            val ended = CompletableDeferred<Unit>()
            AudioPlayer.play(file.path, track, onEnded = { ended.complete(Unit) }, onFailure = failures::add)
            awaitCondition { output.waiting.get() }
            AudioPlayer.pause()
            AudioPlayer.resume()
            output.releaseReturn.countDown()
            awaitCondition { output.pcm().size == 88_200 || failures.isNotEmpty() }
            assertTrue(failures.isEmpty(), "A pause/resume completed before write returned $partial: $failures")
            assertFalse(output.closed.get())
            output.consumeAll()
            withTimeout(15_000) { ended.await() }
            assertContentEquals(file.readBytes().copyOfRange(44, 88_244), output.pcm())
            assertEquals(500L, AudioPlayer.getCurrentPositionMs())
            AudioPlayer.stop()
        }
    }

    @Test fun genuinelyZeroProgressRunningDeviceStillReportsAnOutputFailure() = runBlocking<Unit> {
        val output = InterruptedWriteOutput(unrequestedZero = true)
        AudioPlayer.outputLineFactory = { output.line }
        val failed = CompletableDeferred<AudioPlaybackFailure>()
        AudioPlayer.play(wav().path, track, onFailure = { failed.complete(it) })
        assertEquals(AudioFailureKind.Output, withTimeout(15_000) { failed.await() }.kind)
        assertTrue(output.closed.get())
        assertEquals(2L, output.calls.get(), "A genuinely failed output must not busy-loop")
    }

    @Test fun realOutputConsumesEqualizerPreampAndSeekingRebuildsDspWithCurrentSettings() = runBlocking<Unit> {
        val prefs = melox.platform.getPreferences("melox_app_settings")
        val keys = listOf("equalizer_enabled", "equalizer_preset", "equalizer_preamp_db")
        val before = keys.associateWith { prefs.getString(it, null) }
        try {
            prefs.putValues(mapOf("equalizer_enabled" to "true", "equalizer_preset" to "Flat", "equalizer_preamp_db" to "6.0"))
            melox.playback.MeloXEqualizerSettings.refresh()
            val ended = CompletableDeferred<Unit>()
            AudioPlayer.play(wav().absolutePath, track, onEnded = { ended.complete(Unit) })
            awaitCondition { outputs.firstOrNull()?.acceptedBytes?.get() == 88_200L }
            val first = outputs.single().pcm()
            assertEquals(1995, kotlin.math.abs(MeloXPcmMix.sample(first, 44_100)))
            assertFalse(ended.isCompleted)
            prefs.putValues(mapOf("equalizer_preamp_db" to "-6.0"))
            melox.playback.MeloXEqualizerSettings.refresh()
            AudioPlayer.seekTo(0)
            awaitCondition { outputs.size == 2 && outputs.last().acceptedBytes.get() == 88_200L }
            val second = outputs.last().pcm()
            assertEquals(501, kotlin.math.abs(MeloXPcmMix.sample(second, 44_100)))
            assertTrue(outputs.first().closed.get())
            outputs.last().consumeAll()
            withTimeout(15_000) { ended.await() }
            assertTrue(AudioPlayer.state.value.isFinished)
            assertEquals(500L, AudioPlayer.getCurrentPositionMs())
            retainNativePcmEvidence("single-eq-boost-automix-$autoMix", first, "generated square ±1000; Flat preamp +6 dB")
            retainNativePcmEvidence("single-eq-cut-after-seek-automix-$autoMix", second, "seek 0 rebuild; generated square ±1000; Flat preamp -6 dB")
        } finally { AudioPlayer.stop(); prefs.putValues(before); melox.playback.MeloXEqualizerSettings.refresh() }
    }

    /** Models the documented Java Sound stop/short-write contract; never production output. */
    private class InterruptedWriteOutput(
        private val partial: Int = 0, holdReturn: Boolean = false, private val unrequestedZero: Boolean = false,
    ) {
        private val captured = ByteArrayOutputStream()
        private val stopped = CountDownLatch(1)
        val releaseReturn = CountDownLatch(if (holdReturn) 1 else 0)
        val waiting = AtomicBoolean(false)
        val returned = AtomicBoolean(false)
        val running = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val calls = AtomicLong()
        private val played = AtomicLong()
        fun pcm() = synchronized(captured) { captured.toByteArray() }
        fun consumeAll() { check(running.get()); played.set(pcm().size / 4L) }
        val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> {
                    val call = calls.incrementAndGet()
                    val accepted = if (call == 2L) {
                        if (unrequestedZero) 0 else {
                            waiting.set(true)
                            check(stopped.await(10, TimeUnit.SECONDS)) { "No stop during the pending write" }
                            check(releaseReturn.await(10, TimeUnit.SECONDS)) { "Pending write return not released" }
                            partial
                        }
                    } else minOf(args!![2] as Int, 4096)
                    if (accepted > 0) synchronized(captured) { captured.write(args!![0] as ByteArray, args[1] as Int, accepted) }
                    if (call == 2L) returned.set(true)
                    accepted
                }
                "start" -> { running.set(true); null }
                "stop" -> { running.set(false); stopped.countDown(); null }
                "close" -> { closed.set(true); running.set(false); stopped.countDown(); releaseReturn.countDown(); null }
                "getLongFramePosition" -> played.get()
                "getFramePosition" -> played.get().toInt()
                "isRunning", "isActive" -> running.get()
                "isOpen" -> !closed.get()
                "getBufferSize" -> 176400
                "available" -> 176400 - (pcm().size - played.get() * 4).toInt()
                "toString" -> "validation Java Sound stop/write contract"
                else -> error("Unexpected test device call: ${method.name}")
            }
        } as SourceDataLine
    }

    private class ControlledOutput(private val failOnStart: Boolean = false) {
        private val captured = ByteArrayOutputStream()
        fun pcm(): ByteArray = synchronized(captured) { captured.toByteArray() }
        val acceptedBytes = AtomicLong()
        val playedFrames = AtomicLong()
        val running = AtomicBoolean(false)
        val closed = AtomicBoolean(false)
        val line: SourceDataLine = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader,
            arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> {
                    check(!closed.get()) { "device closed" }
                    val accepted = minOf(args!![2] as Int, 4096) // legal short device write
                    synchronized(captured) { captured.write(args[0] as ByteArray, args[1] as Int, accepted) }
                    acceptedBytes.addAndGet(accepted.toLong())
                    accepted
                }
                "getLongFramePosition" -> playedFrames.get()
                "getFramePosition" -> playedFrames.get().toInt()
                "start" -> { check(!failOnStart) { "device start failed" }; running.set(true); null }
                "stop" -> { running.set(false); null }
                "close" -> { running.set(false); closed.set(true); null }
                "isRunning", "isActive" -> running.get()
                "isOpen" -> !closed.get()
                "getControl" -> throw IllegalArgumentException("No MASTER_GAIN")
                "toString" -> "ControlledOutput"
                else -> error("Unexpected device call: ${method.name}")
            }
        } as SourceDataLine

        fun consumeAll() {
            check(running.get()) { "Cannot consume a paused device" }
            playedFrames.set(acceptedBytes.get() / AudioPlayer.PCM_BYTES_PER_FRAME)
        }
    }
}

/** Run all original FFmpeg lifecycle regressions through the streaming/mix-capable path too. */
class DesktopStreamingAudioSessionTest : DesktopAudioSessionTest() {
    override val autoMix: Boolean = true
}
