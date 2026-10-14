package melox.playback

import kotlinx.coroutines.*
import melox.model.SearchSong
import melox.music.model.*
import melox.player.AudioPlayer
import melox.player.FfmpegPcmDecoder
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

/** Real controller -> streaming AudioPlayer -> bundled FFmpeg -> two PCM sources.
 * Legal generated audio; only physical output and fixture URL resolution are controlled.
 */
class DesktopAutoMixIntegrationTest {
    private val original = AudioPlayer.outputLineFactory
    private val root = Files.createTempDirectory("melox-controller-mix").toFile()
    private val outputs = CopyOnWriteArrayList<Output>()
    private val starts = CopyOnWriteArrayList<DesktopPlaybackStart>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var player: MeloXDesktopPlayer? = null
    @BeforeTest fun setup() {
        AudioPlayer.stop(); MeloXPlaybackModePreferences.reset()
        MeloXPlaybackModePreferences.setAutoMix(true)
        MeloXPlaybackModePreferences.setAutoMixString("automix_mode", "Fixed")
        MeloXPlaybackModePreferences.setAutoMixString("automix_fade_curve", "Linear")
        MeloXPlaybackModePreferences.setAutoMixLong("automix_fixed_duration_ms", 3000)
        AudioPlayer.outputLineFactory = { Output().also(outputs::add).line }
    }
    @AfterTest fun cleanup() {
        player?.release(); scope.cancel(); AudioPlayer.stop(); AudioPlayer.setVolume(1f)
        AudioPlayer.outputLineFactory = original; MeloXPlaybackModePreferences.reset(); root.deleteRecursively()
    }
    private fun song(id: Long, frequency: Int, ms: Int, bpm: Double? = null): SearchSong {
        val frames = ms * 44100 / 1000
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { frame ->
            val time = frame / 44100.0
            val phase = bpm?.let { time % (60 / it) }
            val gain = if (phase == null) 1.0 else if (phase < .085) exp(-phase * 35) else 0.0
            val value = (6000 * gain * sin(2 * PI * frequency * time)).roundToInt().toShort()
            bytes.putShort(value); bytes.putShort(value)
        }
        val file = File(root, "$id.wav").also { it.writeBytes(bytes.array()) }
        val track = MusicTrack(MusicResourceId(MusicSource.Local, "fixture-$id"), "generated-$frequency", emptyList(), durationMs = ms.toLong(),
            providerMetadata = ProviderTrackMetadata.Local(file.toURI().toString(), "fixture-$id"))
        return SearchSong(id, track.title, "generated", "fixture", null, durationMs = ms.toLong(), providerTrack = track)
    }
    private fun install(songs: List<SearchSong>, immediateResolution: Boolean = false) {
        val controller = MeloXDesktopPlayer(if (immediateResolution) { entry ->
            DesktopPlaybackRequest((entry.song.providerTrack!!.providerMetadata as ProviderTrackMetadata.Local).contentUri)
        } else null, { start ->
            starts += start
            AudioPlayer.play(start.request.uri, start.track, start.request.headers, start.positionMs, start.durationMs,
                start.playWhenReady, start.onEnded, start.onFailure)
        }, if (immediateResolution) CoroutineScope(scope.coroutineContext + Dispatchers.Unconfined) else scope,
            if (immediateResolution) Dispatchers.Unconfined else Dispatchers.IO, {}, { _, _ -> })
        player = controller; PlaybackCommands.adoptController(controller)
        controller.setMediaItems(songs, 0, 0); controller.prepare(); controller.play(); PlaybackCommands.publishQueue(controller)
    }
    private suspend fun until(check: () -> Boolean) = withTimeout(30_000) { while (!check()) delay(10) }
    private suspend fun advanceTo(ms: Long) {
        val output = outputs.last()
        val frame = ms * 44100 / 1000
        while (output.rendered.get() < frame) {
            until { output.accepted.get() / 4 > output.rendered.get() || AudioPlayer.state.value.error != null }
            assertNull(AudioPlayer.state.value.error)
            output.advance(minOf(frame, output.rendered.get() + 2205, output.accepted.get() / 4))
            delay(2)
        }
        delay(40)
    }
    @Test fun actualControllerPreloadsMixesPromotesIncomingAndFinishesOnTheSameOutput() = runBlocking<Unit> {
        val a = song(1, 1000, 8000); val b = song(2, 4000, 6000)
        install(listOf(a, b))
        until { MeloXAutoMixTransitionRuntime.state.value.status == "prepared" }
        assertEquals(1, outputs.size)
        advanceTo(6400)
        val mix = MeloXAutoMixTransitionRuntime.state.value
        assertTrue(mix.active, "real transition: $mix")
        assertTrue(mix.progress in .4f.. .6f)
        assertEquals(a.providerTrack!!.id, AudioPlayer.currentTrack.value?.id)
        assertEquals(0, PlaybackCommands.queue.value.index)
        advanceTo(8050)
        until { PlaybackCommands.queue.value.index == 1 }
        assertEquals(b.providerTrack!!.id, AudioPlayer.currentTrack.value?.id)
        assertTrue(AudioPlayer.getCurrentPositionMs() in 3000..3150)
        assertEquals(1, outputs.size, "handoff must retain output ownership")
        advanceTo(11000)
        until { AudioPlayer.state.value.isFinished }
        assertEquals(6000L, AudioPlayer.getCurrentPositionMs())
        assertEquals(1, outputs.size)
        assertTrue(outputs.single().closed.get())
        until { FfmpegPcmDecoder.activeProcesses() == 0 }
        println("Production controller AutoMix: same_output=true accepted_bytes=${outputs.single().accepted.get()} queue_index=${PlaybackCommands.queue.value.index} final_position_ms=${AudioPlayer.getCurrentPositionMs()}")
    }
    @Test fun pauseDuringOverlapCancelsBothDecksKeepsPrimaryPositionAndSeekZeroStillWorks() = runBlocking<Unit> {
        install(listOf(song(1, 1000, 8000), song(2, 4000, 6000)))
        until { MeloXAutoMixTransitionRuntime.state.value.status == "prepared" }
        advanceTo(6200)
        assertTrue(MeloXAutoMixTransitionRuntime.state.value.active)
        player!!.pause()
        until { outputs.size == 2 }
        delay(100)
        assertFalse(player!!.playWhenReady)
        assertTrue(AudioPlayer.state.value.isPaused)
        assertEquals(0, PlaybackCommands.queue.value.index)
        assertFalse(MeloXAutoMixTransitionRuntime.state.value.active)
        assertEquals(0L, outputs.last().accepted.get())
        assertTrue(AudioPlayer.getCurrentPositionMs() in 6150..6250)
        player!!.seekTo(0)
        until { outputs.size == 3 }
        delay(100)
        assertEquals(0L, AudioPlayer.getCurrentPositionMs())
        assertEquals(0L, outputs.last().accepted.get())
        player!!.play()
        until { outputs.last().accepted.get() > 0 }
        player!!.clearQueue()
        until { FfmpegPcmDecoder.activeProcesses() == 0 }
        assertTrue(PlaybackCommands.queue.value.songs.isEmpty())
        assertNull(AudioPlayer.currentTrack.value)
        assertTrue(outputs.all { it.closed.get() })
    }
    @Test fun muteAndSoftwareVolumeChangeActualPcmWhenDeviceHasNoMasterGain() = runBlocking<Unit> {
        install(listOf(song(1, 1000, 8000)))
        until { outputs.firstOrNull()?.accepted?.get()?.let { it > 0 } == true }
        player!!.setVolume(0f)
        advanceTo(2000)
        val bytes = outputs.single().bytes()
        val mutedStart = 176400
        assertTrue(bytes.copyOfRange(mutedStart, mutedStart + 17640).all { it == 0.toByte() })
        player!!.setVolume(.5f)
        advanceTo(4000)
        val changed = outputs.single().bytes()
        val samples = (529200 until 546840 step 2).map { index ->
            ((changed[index].toInt() and 255) or (changed[index + 1].toInt() shl 8)).toShort().toDouble()
        }
        val rms = sqrt(samples.sumOf { it * it } / samples.size)
        assertTrue(rms in 2100.0..2140.0, "actual 0.5-gain RMS $rms")
    }
    @Test fun smartQueueActuallyAnalyzesCandidatePcmAndAppendsCompatibleTempoBeforeOriginalOrder() = runBlocking<Unit> {
        MeloXPlaybackModePreferences.setSmartQueue(true)
        val songs = listOf(song(1, 130, 16000, 90.0), song(2, 130, 16000, 140.0), song(3, 130, 16000, 92.0))
        install(songs)
        until { PlaybackCommands.queue.value.songs.size == 2 }
        assertEquals(listOf(1L, 3L), PlaybackCommands.queue.value.songs.map { it.id })
        assertEquals(0, PlaybackCommands.queue.value.index)
        player!!.setSmartQueueEnabled(false)
        assertEquals(listOf(1L, 3L, 2L), PlaybackCommands.queue.value.songs.map { it.id })
        println("Real analyzed Smart Queue: selected 92 BPM candidate over original 140 BPM candidate for 90 BPM source; unconsumed candidate restored on disable")
    }
    @Test fun disablingSmartQueueRetainsOriginalUnconsumedTrackEvenWhenItsAnalysisFailed() {
        val entries = listOf(song(1, 130, 16000, 90.0), song(2, 130, 16000, 140.0)).mapIndexed { index, song ->
            QueueEntry(song, PlaybackCommands.QUEUE_ORIGIN_BASE, index, "failed-analysis-$index")
        }
        val builder = MeloXSmartQueueBuilder()
        builder.begin(entries, 0)
        val token = builder.snapshot().generation
        builder.failure(token, entries[1])
        assertTrue(builder.snapshot().items.isEmpty(), "Failed analysis must not be selected as an analyzed match")
        assertEquals(listOf(entries[1]), builder.takeRemaining(), "Disabling Smart Queue must restore all unconsumed user entries")
    }
    @Test fun threeRealTracksPerformTwoConsecutiveHandoffsWithoutReopeningTheOutput() = runBlocking<Unit> {
        install(listOf(song(1, 1000, 8000), song(2, 2000, 10000), song(3, 4000, 6000)))
        until { MeloXAutoMixTransitionRuntime.state.value.status == "prepared" }
        advanceTo(8050)
        until { PlaybackCommands.queue.value.index == 1 }
        until { MeloXAutoMixTransitionRuntime.state.value.status == "prepared" }
        advanceTo(15050)
        until { PlaybackCommands.queue.value.index == 2 }
        assertEquals(1, outputs.size)
        assertEquals("generated-4000", AudioPlayer.currentTrack.value?.title)
        advanceTo(18000)
        until { AudioPlayer.state.value.isFinished }
        assertEquals(6000L, AudioPlayer.getCurrentPositionMs())
        assertEquals(3175200L, outputs.single().accepted.get())
        until { FfmpegPcmDecoder.activeProcesses() == 0 }
        println("Production chained AutoMix: 3 real tracks / 2 rendered-clock handoffs / 1 output / 18s PCM / zero native decoder leaks")
    }
    @Test fun nextDuringOverlapContinuesAtHeardIncomingPositionAndPreviousReturnsToPrimary() = runBlocking<Unit> {
        install(listOf(song(1, 1000, 8000), song(2, 4000, 6000)))
        until { MeloXAutoMixTransitionRuntime.state.value.status == "prepared" }
        advanceTo(6200)
        player!!.next()
        until { outputs.size >= 2 && AudioPlayer.isReady() && AudioPlayer.currentTrack.value?.title == "generated-4000" }
        assertEquals(1, PlaybackCommands.queue.value.index)
        assertTrue(AudioPlayer.getCurrentPositionMs() in 1100..1300, "Incoming heard position: ${AudioPlayer.getCurrentPositionMs()}, starts=${starts.map { it.positionMs }}")
        assertFalse(MeloXAutoMixTransitionRuntime.state.value.active)
        player!!.previous()
        until { AudioPlayer.isReady() && AudioPlayer.currentTrack.value?.title == "generated-1000" }
        assertEquals(0, PlaybackCommands.queue.value.index)
        assertEquals(0L, AudioPlayer.getCurrentPositionMs())
    }
    @Test fun immediatelyResolvedNextStartsAtHeardIncomingPositionWithoutOpeningAtZero() = runBlocking<Unit> {
        install(listOf(song(1, 1000, 8000), song(2, 4000, 6000)), immediateResolution = true)
        until { MeloXAutoMixTransitionRuntime.state.value.status == "prepared" }
        advanceTo(6200)
        until { AudioPlayer.incomingMixPosition()?.let { it in 1100..1300 } == true }
        val heard = assertNotNull(AudioPlayer.incomingMixPosition())
        player!!.next()
        until { starts.size == 2 && AudioPlayer.isReady() && AudioPlayer.currentTrack.value?.title == "generated-4000" }
        assertEquals(heard, starts.last().positionMs, "Resolution completing inline must never start the incoming deck at zero")
        assertEquals(heard, AudioPlayer.getCurrentPositionMs())
        assertEquals(1, PlaybackCommands.queue.value.index)
        player!!.previous()
        until { starts.size == 3 && AudioPlayer.isReady() && AudioPlayer.currentTrack.value?.title == "generated-1000" }
        assertEquals(0L, starts.last().positionMs)
        assertEquals(0L, AudioPlayer.getCurrentPositionMs())
        assertEquals(0, PlaybackCommands.queue.value.index)
    }
    private class Output {
        val accepted = AtomicLong(); val rendered = AtomicLong(); val closed = AtomicBoolean(false); val running = AtomicBoolean(false)
        private val data = ByteArrayOutputStream(); private val gate = Object()
        fun bytes() = synchronized(data) { data.toByteArray() }
        fun advance(frames: Long) = synchronized(gate) { if (running.get()) rendered.set(frames); gate.notifyAll() }
        val line: SourceDataLine = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> synchronized(gate) {
                    while (!closed.get() && (accepted.get() / 4 - rendered.get() >= 8820 || !running.get())) gate.wait(10)
                    check(!closed.get()) { "output closed" }
                    val count = minOf(args!![2] as Int, 2048, ((8820 - (accepted.get() / 4 - rendered.get())) * 4).toInt())
                    synchronized(data) { data.write(args[0] as ByteArray, args[1] as Int, count) }; accepted.addAndGet(count.toLong()); count
                }
                "getLongFramePosition" -> rendered.get()
                "start" -> synchronized(gate) { running.set(true); gate.notifyAll(); null }
                "stop" -> { running.set(false); null }
                "close" -> synchronized(gate) { running.set(false); closed.set(true); gate.notifyAll(); null }
                "isRunning", "isActive" -> running.get()
                "isOpen" -> !closed.get()
                "getControl" -> throw IllegalArgumentException("No MASTER_GAIN")
                "toString" -> "ControlledProductionOutput"
                else -> error("Unexpected endpoint ${method.name}")
            }
        } as SourceDataLine
    }
}
