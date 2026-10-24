package melox.lan

import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.*
import melox.model.SearchSong
import melox.playback.*
import melox.player.AudioPlayer
import melox.player.writePcmBlock
import org.json.JSONObject
import kotlin.test.*

/** Real loopback control/PCM sockets; controlled Java Sound endpoint, not Android hardware. */
class MeloXLanSessionTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val requests = CopyOnWriteArrayList<JSONObject>()
    private var receiver: LanCastReceiver? = null
    private var rejectCast = false
    private val identity = LanIdentity("lan-session-test-${UUID.randomUUID()}")
    private val server = LanServer(identity, object : LanHost {
        override fun state() = JSONObject().put("title", "Protocol peer").put("playing", false)
        override fun queue() = emptyList<LanTrack>()
        override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
            requests += JSONObject(message.toString())
            if (action == "cast.start") {
                if (rejectCast) return JSONObject().put("ok", false).put("error", "receiver_unavailable")
                val format = LanPcmFormat.parse(message.optJSONObject("format"))
                receiver = LanCastReceiver(loopback, loopback, format, openLine = { outputLine(ByteArrayOutputStream()) }).also { it.start() }
                return JSONObject().put("ok", true).put("castPort", receiver!!.port).put("format", format.json())
            }
            if (action == "cast.stop") { receiver?.close(); receiver = null }
            return JSONObject().put("ok", true)
        }
    }, loopback, discoveryPort = 0)
    private var wasEnabled = true
    @BeforeTest fun setup() {
        wasEnabled = MeloXLanRuntime.identity.enabled; MeloXLanRuntime.identity.enabled = true
        identity.port = 51000 + (identity.deviceId.hashCode() and 511)
        server.start()
    }
    @AfterTest fun cleanup() = runBlocking {
        runCatching { MeloXLanSession.disconnect() }; receiver?.close(); server.close()
        LanPcmTap.muteLocalOutput = false; MeloXLanRuntime.identity.enabled = wasEnabled
    }
    private suspend fun connect() {
        val peer = LanDevice(identity.deviceId, "Protocol peer", "127.0.0.1", server.port)
        assertEquals("auth_ok", MeloXLanSession.connect(peer, identity.pin).optString("type"))
    }
    private suspend fun until(block: () -> Boolean) = withTimeout(5000) { while (!block()) delay(10) }

    @Test fun processConnectionSurvivesModeResetAndServerDisconnectClearsSnapshot() = runBlocking {
        val peer = LanDevice(identity.deviceId, "Protocol peer", "127.0.0.1", server.port)
        assertEquals("auth_required", MeloXLanSession.connect(peer).optString("type"))
        assertNull(MeloXLanSession.state.value.device)
        connect()
        assertEquals("Protocol peer", MeloXLanSession.state.value.remote?.optString("title"))
        // Same process connection is reused, including when PIN isn't supplied again.
        assertEquals("auth_ok", MeloXLanSession.connect(peer).optString("type"))
        MeloXLanSession.useLocal()
        MeloXLanSession.control("pause")
        assertEquals("pause", requests.last().optString("command"))
        assertTrue(MeloXLanRuntime.isConnected)
        server.close()
        until { MeloXLanSession.state.value.device == null }
        assertEquals(LanOutputMode.Local, MeloXLanSession.state.value.mode)
        assertFalse(MeloXLanSession.state.value.busy)
    }

    @Test fun transferCarriesFullQueueAndTogetherStopRetainsControlSocket() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var starts = 0
        val player = MeloXDesktopPlayer({ DesktopPlaybackRequest("fixture-only://not-played") }, { starts++ }, scope, Dispatchers.IO, {}, { _, _ -> })
        try {
            player.setMediaItems(listOf(SearchSong(11, "First", "Artist", "Album", null),
                SearchSong(12, "Second", "Artist", "Album", null)), 1, 0)
            PlaybackCommands.adoptController(player); PlaybackCommands.publishQueue(player)
            connect(); MeloXLanSession.transfer()
            val transfer = requests.first { it.optString("action") == "transfer" }
            assertEquals(2, transfer.getJSONArray("queue").length())
            assertEquals("12", transfer.getJSONObject("track").getString("id"))
            assertEquals(0, starts, "Transfer must not start local playback")
            MeloXLanSession.together(false)
            assertEquals(LanOutputMode.Together, MeloXLanSession.state.value.mode)
            until { requests.any { it.optString("action") == "together.start" } }
            MeloXLanSession.useLocal()
            assertEquals(LanOutputMode.Local, MeloXLanSession.state.value.mode)
            assertTrue(requests.any { it.optString("action") == "together.stop" })
            MeloXLanSession.together(false)
            rejectCast = true
            assertFailsWith<IllegalStateException> { MeloXLanSession.cast() }
            assertEquals(LanOutputMode.Local, MeloXLanSession.state.value.mode, "A failed output switch must not retain the previous mode")
            assertFalse(LanPcmTap.muteLocalOutput)
            MeloXLanSession.control("next")
            assertTrue(MeloXLanRuntime.isConnected)
        } finally { player.release(); scope.cancel() }
    }

    @Test fun castSilencesOnlyLocalAcceptedPcmAndRestoresItWithoutDisconnecting() = runBlocking {
        connect(); MeloXLanSession.cast()
        assertTrue(LanPcmTap.muteLocalOutput)
        assertEquals(LanOutputMode.Cast, MeloXLanSession.state.value.mode)
        val original = ByteArray(1024) { (it % 100 + 1).toByte() }
        val heard = ByteArrayOutputStream(); val sent = ByteArrayOutputStream()
        val tap: (ByteArray) -> Unit = { sent.write(it) }
        LanPcmTap.subscribe(tap)
        try {
            assertEquals(original.size, writePcmBlock(outputLine(heard), original, original.size,
                { true }, { false }, { 0L }, { true }, {}))
            assertContentEquals(original, sent.toByteArray(), "Tap must receive original PCM, not silence")
            assertTrue(heard.toByteArray().all { it == 0.toByte() })
        } finally { LanPcmTap.unsubscribe(tap) }
        MeloXLanSession.useLocal()
        assertFalse(LanPcmTap.muteLocalOutput)
        assertTrue(MeloXLanRuntime.isConnected)
        MeloXLanSession.control("play")
    }

    @Test fun togetherCorrectionWaitsForReadyAndThreeSecondsAfterSeek() {
        assertFalse(shouldCorrectTogetherPosition(false, 0, 8000, 9000))
        assertFalse(shouldCorrectTogetherPosition(true, 0, 8000, 2999))
        assertFalse(shouldCorrectTogetherPosition(true, 0, 250, 3000))
        assertTrue(shouldCorrectTogetherPosition(true, 0, 251, 3000))
    }

    @Test fun selectingNewAndReusedConnectionsStopsTheOriginalCastBeforeChangingItsOwner() = runBlocking {
        connect(); MeloXLanSession.cast()
        val otherIdentity = LanIdentity("lan-selection-${UUID.randomUUID()}")
        otherIdentity.port = 54000 + (otherIdentity.deviceId.hashCode() and 255)
        val other = LanServer(otherIdentity, object : LanHost {
            override fun state() = JSONObject().put("playing", false)
            override fun queue() = emptyList<LanTrack>()
            override fun request(action: String, message: JSONObject, peer: InetAddress) = JSONObject().put("ok", true)
        }, loopback, 0)
        other.start()
        val target = LanDevice(otherIdentity.deviceId, "Second protocol peer", "127.0.0.1", other.port)
        try {
            assertEquals("auth_ok", MeloXLanSession.connect(target, otherIdentity.pin).optString("type"))
            assertFalse(LanPcmTap.muteLocalOutput)
            assertEquals(1, requests.count { it.optString("action") == "cast.stop" })
            connect(); MeloXLanSession.cast()
            // Select the second existing socket without supplying its PIN again.
            assertEquals("auth_ok", MeloXLanSession.connect(target).optString("type"))
            assertFalse(LanPcmTap.muteLocalOutput)
            assertEquals(2, requests.count { it.optString("action") == "cast.stop" })
            assertEquals(2, MeloXLanRuntime.connectedDevices.size)
            assertEquals(target.id, MeloXLanSession.state.value.device?.id)
        } finally { other.close() }
    }

    private fun outputLine(bytes: ByteArrayOutputStream) = Proxy.newProxyInstance(
        SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
        when (method.name) {
            "write" -> { val count = minOf(args!![2] as Int, 128); synchronized(bytes) { bytes.write(args[0] as ByteArray, args[1] as Int, count) }; count }
            "isOpen", "isRunning", "isActive" -> true
            "getBufferSize", "available", "getFramePosition" -> 4096
            "getLongFramePosition", "getMicrosecondPosition" -> 0L
            "getLevel" -> 0f
            else -> null
        }
    } as SourceDataLine
}
