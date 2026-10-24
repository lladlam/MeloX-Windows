package melox.lan

import java.io.File
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.*
import melox.model.SearchSong
import melox.player.AudioPlayer
import melox.playback.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.test.*

/** Actual TCP + FFmpeg + controlled, paced PCM sink. No cross-machine/acoustic claims. */
class MeloXLanBarrierTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val root = Files.createTempDirectory("melox-barrier").toFile()
    private val oldLine = AudioPlayer.outputLineFactory
    private val sinks = CopyOnWriteArrayList<Sink>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MeloXDesktopPlayer? = null
    private val resources = mutableListOf<AutoCloseable>()
    private val enabled = MeloXLanRuntime.identity.enabled
    private val audio = File(root, "generated.wav")
    @BeforeTest fun setup() {
        val frames = 44100 * 20
        val data = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        val second = ByteBuffer.allocate(44100 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(44100) { val sample = (5000 * sin(it * 2 * PI * 1000 / 44100)).toInt().toShort(); second.putShort(sample).putShort(sample) }
        repeat(20) { data.put(second.array()) }
        audio.writeBytes(data.array())
        AudioPlayer.stop(); AudioPlayer.setTogetherBarrier(false)
        AudioPlayer.outputLineFactory = { Sink().also(sinks::add).line }
        MeloXLanRuntime.identity.enabled = true
    }
    @AfterTest fun cleanup(): Unit = runBlocking {
        MeloXLanRuntime.endSession(); resources.reversed().forEach { runCatching { it.close() } }
        withContext(Dispatchers.Main) { player?.release() }
        scope.cancel(); AudioPlayer.stop(); AudioPlayer.outputLineFactory = oldLine
        MeloXLanRuntime.identity.enabled = enabled; root.deleteRecursively(); Unit
    }
    private suspend fun install() = withContext(Dispatchers.Main) {
        val controller = MeloXDesktopPlayer({ DesktopPlaybackRequest(audio.absolutePath) }, { start ->
            AudioPlayer.play(start.request.uri, start.track, start.request.headers, start.positionMs,
                start.durationMs, start.playWhenReady, start.onEnded, start.onFailure, autoMix = false)
        }, scope, Dispatchers.IO, {}, { _, _ -> })
        player = controller; PlaybackCommands.adoptController(controller)
        controller.setMediaItems(listOf(SearchSong(11, "First", "Generated", "Fixture", null, durationMs = 20000),
            SearchSong(12, "Second", "Generated", "Fixture", null, durationMs = 20000)), 0, 0)
        controller.prepare(); controller.play(); PlaybackCommands.publishQueue(controller)
    }
    private suspend fun until(timeoutMs: Long = 30_000, block: () -> Boolean) = withTimeout(timeoutMs) { while (!block()) delay(10) }
    private fun identity() = LanIdentity("lan-barrier-${UUID.randomUUID()}").also { it.port = 52000 + (it.deviceId.hashCode() and 511) }
    private fun connect(server: LanServer): LanClient {
        val client = LanClient(LanDevice(server.identity.deviceId, "Barrier fixture", "127.0.0.1", server.port), identity())
        resources += client
        assertEquals("auth_ok", client.connect(server.identity.pin).optString("type"))
        assertTrue(LAN_TOGETHER_BARRIER in client.capabilities)
        return client
    }
    @Test fun receiverAcknowledgesRealReadyPausedThenSchedulesAndFencesStaleRounds() = runBlocking {
        install(); until { AudioPlayer.isReady() }
        val control = LanServer(identity(), object : LanHost {
            override fun state() = JSONObject()
            override fun queue() = emptyList<LanTrack>()
            override fun request(action: String, message: JSONObject, peer: InetAddress) = JSONObject().put("ok", true)
        }, loopback, 0).also { it.start(); resources += it }
        val auth = MeloXLanRuntime.connect(LanDevice(control.identity.deviceId, "Control peer", "127.0.0.1", control.port), control.identity.pin, {})
        assertEquals("auth_ok", auth.optString("type"))
        val tracks = listOf(LanTrack("netease", "11", "First", "Generated", durationMs = 20000))
        val relay = LanAudioRelay(loopback, loopback, tracks, resolve = { DesktopPlaybackRequest(audio.absolutePath) }); resources += relay
        val server = LanServer(identity(), MeloXLanRuntime, loopback, 0); server.start(); resources += server
        val client = connect(server)
        val packet = JSONObject().put("sessionId", "round-fixture").put("generation", 7).put("queueRevision", "queue-a")
            .put("queue", JSONArray(tracks.map { it.json() })).put("index", 0).put("positionMs", 0).put("playing", true).put("relay", relay.description())
        val ack = client.request("together.prepare", packet, 35000)
        assertTrue(ack.optBoolean("ok"), ack.toString()); assertEquals(7, ack.optInt("generation"))
        assertTrue(AudioPlayer.isReady(), "Posting work is not a READY acknowledgement")
        assertEquals(1, MeloXLanRuntime.connectedDevices.size, "Preparing a round preserves independent control sessions")
        assertTrue(MeloXLanRuntime.remote("ping").optBoolean("ok"))
        assertFalse(AudioPlayer.state.value.isPlaying)
        assertEquals(0L, sinks.last().accepted.get(), "Prepared audio must not be written before commit")
        client.event(JSONObject().put("event", "together.commit").put("sessionId", "round-fixture").put("generation", 6)
            .put("startAtMs", System.currentTimeMillis()).put("playing", true))
        delay(100); assertFalse(AudioPlayer.state.value.isPlaying)
        client.event(JSONObject().put("event", "together.commit").put("sessionId", "round-fixture").put("generation", 7)
            .put("startAtMs", System.currentTimeMillis() + 300).put("clockOffsetMs", 0).put("playing", true).put("late", true))
        delay(80); assertFalse(AudioPlayer.state.value.isPlaying)
        until { AudioPlayer.state.value.isPlaying && sinks.last().accepted.get() > 0 }
        val count = sinks.size
        client.event(JSONObject().put("event", "together.state").put("sessionId", "round-fixture").put("generation", 7)
            .put("state", JSONObject().put("index", 0).put("positionMs", 15000).put("playing", true).put("ready", true)))
        delay(400)
        assertEquals(count, sinks.size, "Late participant must not seek to catch up")
        assertTrue(AudioPlayer.getCurrentPositionMs() < 4000)
        val stale = client.request("together.prepare", JSONObject(packet.toString()).put("generation", 6), 35000)
        assertFalse(stale.optBoolean("ok")); assertEquals(count, sinks.size)
        client.event(JSONObject().put("event", "together.state").put("sessionId", "round-fixture").put("generation", 7).put("seek", true)
            .put("state", JSONObject().put("index", 0).put("positionMs", 20000).put("playing", true).put("ready", true)))
        until { AudioPlayer.state.value.isFinished }
        val endedOutputs = sinks.size
        client.event(JSONObject().put("event", "together.state").put("sessionId", "round-fixture").put("generation", 7)
            .put("state", JSONObject().put("index", 0).put("positionMs", 20000).put("playing", true).put("ready", true)))
        delay(400)
        assertTrue(AudioPlayer.state.value.isFinished, "Heartbeat cannot restart a participant that reached its end barrier")
        assertEquals(endedOutputs, sinks.size)
        val next = client.request("together.prepare", JSONObject(packet.toString()).put("generation", 8).put("positionMs", 1000), 35000)
        assertTrue(next.optBoolean("ok"), next.toString())
        client.event(JSONObject().put("event", "together.commit").put("sessionId", "round-fixture").put("generation", 7)
            .put("startAtMs", System.currentTimeMillis()).put("playing", true))
        delay(100)
        assertFalse(AudioPlayer.state.value.isPlaying, "The old generation cannot release a replacement queue")
        assertTrue(MeloXLanRuntime.remote("ping").optBoolean("ok"))
        assertTrue(client.request("together.stop").optBoolean("ok"))
        assertTrue(client.request("ping").optBoolean("ok"), "Stopping output must retain control")
    }

    @Test fun hostPreparesMultiplePeersKeepsConnectionsAndReplacesQueueAtSameIndex() = runBlocking {
        install(); until { AudioPlayer.isReady() }
        val events = CopyOnWriteArrayList<JSONObject>(); val prepares = CopyOnWriteArrayList<JSONObject>()
        fun peer(): LanServer {
            val host = object : LanHost {
                override val supportsBarrier = true
                override fun state() = JSONObject().put("playing", false).put("ready", true)
                override fun queue() = emptyList<LanTrack>()
                override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
                    if (action == "together.prepare") prepares += JSONObject(message.toString())
                    return JSONObject().put("ok", true).put("sessionId", message.optString("sessionId")).put("generation", message.optLong("generation"))
                }
            }
            return LanServer(identity(), host, loopback, 0).also { it.start(); resources += it }
        }
        val servers = listOf(peer(), peer())
        servers.forEach { server ->
            val device = LanDevice(server.identity.deviceId, "Peer", "127.0.0.1", server.port)
            val auth = MeloXLanRuntime.connect(device, server.identity.pin, { events += JSONObject(it.toString()) })
            assertEquals("auth_ok", auth.optString("type")); MeloXLanRuntime.together(false)
        }
        until { prepares.size >= 2 && AudioPlayer.state.value.isPlaying }
        assertEquals(2, MeloXLanRuntime.connectedDevices.size)
        val previous = prepares.maxOf { it.optLong("generation") }
        withContext(Dispatchers.Main) {
            player!!.setMediaItems(listOf(SearchSong(12, "Replacement", "Generated", "Fixture", null, durationMs = 20000)), 0, 0)
            player!!.prepare(); player!!.play(); PlaybackCommands.publishQueue(player!!)
        }
        until { prepares.count { it.optLong("generation") > previous } >= 2 }
        assertTrue(prepares.filter { it.optLong("generation") > previous }.all { it.getJSONArray("queue").getJSONObject(0).getString("id") == "12" })
        assertEquals(2, MeloXLanRuntime.connectedDevices.size)
    }

    @Test fun timestampExtrapolationRequiresReadyPlayingAndCapsExtrapolation() {
        assertEquals(1000L, togetherTargetPosition(1000, false, 10000, 12000, 50))
        assertEquals(1250L, togetherTargetPosition(1000, true, 10000, 10200, 50))
        assertEquals(3000L, togetherTargetPosition(1000, true, 10000, 90000, 0))
    }
    @Test fun aRejectedPrepareRestoresLocalPlaybackAndRetainsItsControlConnection() = runBlocking {
        install(); until { AudioPlayer.state.value.isPlaying }
        val rejected = AtomicBoolean()
        val host = object : LanHost {
            override val supportsBarrier = true
            override fun state() = JSONObject()
            override fun queue() = emptyList<LanTrack>()
            override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
                if (action == "together.prepare") rejected.set(true)
                return JSONObject().put("ok", action != "together.prepare").put("error", "fixture_prepare_rejected")
            }
        }
        val server = LanServer(identity(), host, loopback, 0).also { it.start(); resources += it }
        MeloXLanRuntime.connect(LanDevice(server.identity.deviceId, "Rejecting peer", "127.0.0.1", server.port), server.identity.pin, {})
        MeloXLanRuntime.together(false)
        until { rejected.get() && !MeloXLanTogetherCoordinator.active && AudioPlayer.state.value.isPlaying }
        assertEquals(1, MeloXLanRuntime.connectedDevices.size)
        assertTrue(MeloXLanRuntime.remote("ping").optBoolean("ok"))
    }
    @Test fun boundedRateCorrectionChangesActualDecodedPcmAndItsMediaClockWithoutRestartingDecoder() = runBlocking {
        install(); until { AudioPlayer.isReady() }
        val sink = sinks.last()
        fun tailFrequency(): Int {
            val bytes = synchronized(sink.audio) { sink.audio.toByteArray() }.takeLast(44100 * 4).toByteArray()
            val samples = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var crossings = 0; var previous = 0
            while (samples.remaining() >= 4) {
                val left = samples.short.toInt(); val right = samples.short.toInt()
                assertEquals(left, right, "Correction preserves the decoded stereo channels")
                if (previous <= 0 && left > 0) crossings++
                previous = left
            }
            return crossings
        }
        AudioPlayer.setTogetherRate(1.03f)
        until { sink.accepted.get() >= 2 * 176400 }
        assertTrue(tailFrequency() in 1028..1032, "1.03 rate must reach the actual PCM sink")
        val physicalMs = sink.accepted.get() * 1000 / 176400
        until { AudioPlayer.getCurrentPositionMs() >= physicalMs + 20 }
        val previous = sink.accepted.get()
        AudioPlayer.setTogetherRate(.97f)
        until { sink.accepted.get() >= previous + 2 * 176400 }
        assertTrue(tailFrequency() in 968..972, "0.97 rate must reach the actual PCM sink")
        assertSame(sink, sinks.single(), "Drift correction cannot reopen the decoder/output")
    }
    @Test fun naturalEndWaitIsBoundedAndExplicitSkipCancelsThePendingGeneration() = runBlocking {
        install(); until { AudioPlayer.isReady() }
        val prepares = CopyOnWriteArrayList<JSONObject>()
        // The fixture's state() reaches the coordinator as the guest's reported
        // state through the server's 200ms event cadence, so both sides of the
        // five-second rule get deterministic preconditions.
        val caughtUp = java.util.concurrent.atomic.AtomicBoolean(false)
        val host = object : LanHost {
            override val supportsBarrier = true
            override fun state() = if (caughtUp.get())
                JSONObject().put("ready", true).put("playing", true).put("positionMs", 10_000_000L)
                    .put("serverTimeMs", System.currentTimeMillis()).put("ended", false)
            else JSONObject().put("ready", true).put("playing", false).put("positionMs", 0L).put("ended", false)
            override fun queue() = emptyList<LanTrack>()
            override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
                if (action == "together.prepare") prepares += JSONObject(message.toString())
                return JSONObject().put("ok", true).put("sessionId", message.optString("sessionId")).put("generation", message.optLong("generation"))
            }
        }
        val server = LanServer(identity(), host, loopback, 0); server.start(); resources += server
        MeloXLanRuntime.connect(LanDevice(server.identity.deviceId, "End barrier fixture", "127.0.0.1", server.port), server.identity.pin, {})
        MeloXLanRuntime.together(false)
        until { prepares.isNotEmpty() && AudioPlayer.state.value.isPlaying }
        val released = AtomicLong()
        // A guest stalled more than five seconds behind never holds the host's end.
        until { AudioPlayer.getCurrentPositionMs() > 6_000 }
        val stalledAt = System.nanoTime()
        withContext(Dispatchers.Main) { assertTrue(MeloXLanTogetherCoordinator.ended { released.incrementAndGet() }) }
        until { released.get() == 1L }
        assertTrue(System.nanoTime() - stalledAt < 3_000_000_000L, "A guest >5s behind must not delay the host")
        // A caught-up, never-ending guest holds the host exactly up to the cap.
        caughtUp.set(true)
        val generation = prepares.last().optLong("generation")
        withContext(Dispatchers.Main) { player!!.next() }
        until { prepares.any { it.optLong("generation") > generation } && AudioPlayer.state.value.isPlaying }
        delay(700)
        val cappedAt = System.nanoTime()
        withContext(Dispatchers.Main) { assertTrue(MeloXLanTogetherCoordinator.ended { released.incrementAndGet() }) }
        delay(300); assertEquals(1L, released.get())
        until { released.get() == 2L }
        assertTrue(System.nanoTime() - cappedAt >= 4_800_000_000L, "A caught-up guest must hold the host, capped at five seconds")
        // A completed generation transitions only once.
        withContext(Dispatchers.Main) { MeloXLanTogetherCoordinator.ended { released.incrementAndGet() } }
        delay(250); assertEquals(2L, released.get(), "One generation may transition only once")
        // An explicit user skip cancels a pending end barrier instead of advancing later.
        val skipGeneration = prepares.last().optLong("generation")
        withContext(Dispatchers.Main) { player!!.seekToIndex(0) }
        until { prepares.any { it.optLong("generation") > skipGeneration } && AudioPlayer.state.value.isPlaying }
        delay(700)
        withContext(Dispatchers.Main) { assertTrue(MeloXLanTogetherCoordinator.ended { released.incrementAndGet() }) }
        delay(150)
        val nextGeneration = prepares.last().optLong("generation")
        withContext(Dispatchers.Main) { player!!.seekToIndex(1) }
        until { prepares.any { it.optLong("generation") > nextGeneration } }
        delay(5200)
        assertEquals(2L, released.get(), "A cancelled pending barrier must never advance")
    }
    private class Sink {
        val audio = ByteArrayOutputStream()
        val accepted = AtomicLong(); private val running = AtomicBoolean(); private val closed = AtomicBoolean()
        val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> { val size = minOf(args!![2] as Int, 1764); if (closed.get()) 0 else {
                    Thread.sleep(10)
                    synchronized(audio) { audio.write(args[0] as ByteArray, args[1] as Int, size) }
                    accepted.addAndGet(size.toLong()); size
                } }
                "start" -> { running.set(true); null }
                "stop" -> { running.set(false); null }
                "close" -> { closed.set(true); null }
                "isOpen" -> !closed.get()
                "isRunning", "isActive" -> running.get()
                "getLongFramePosition" -> accepted.get() / 4
                "getFramePosition" -> (accepted.get() / 4).toInt()
                "available", "getBufferSize" -> 17640
                "getLevel" -> 0f
                "getMicrosecondPosition" -> accepted.get() * 1_000_000 / 176400
                else -> null
            }
        } as SourceDataLine
    }
}
