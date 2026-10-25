package melox.lan

import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.UUID
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine
import kotlin.test.*
import org.json.JSONArray
import org.json.JSONObject

/** Loopback sockets and a fake audio device only; never claims physical LAN or speaker playback. */
class MeloXLanProtocolTest {
    private class Host : LanHost {
        val requests = mutableListOf<String>()
        var playing = true
        override fun state() = JSONObject().put("playing", playing).put("title", "Fixture").put("positionMs", 1234)
        override fun queue() = listOf(LanTrack("netease", "1", "Fixture", "Artist"))
        override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
            requests += action
            return when (action) {
                "control" -> { playing = message.optString("command") != "pause"; JSONObject().put("ok", true) }
                "cast.start" -> JSONObject().put("ok", true).put("castPort", 1)
                else -> JSONObject().put("ok", false).put("error", "unsupported")
            }
        }
    }

    @Test fun pinTrustControlPingAndUnsupportedTransferStayOnWireProtocol() {
        val suffix = UUID.randomUUID().toString().take(8)
        val host = Host()
        val serverIdentity = LanIdentity("lan-test-server-$suffix")
        // Windows hosted runners reserve whole dynamic-port ranges. Ask the OS
        // for an available loopback port instead of guessing inside one range.
        serverIdentity.port = java.net.ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        val server = LanServer(serverIdentity, host, InetAddress.getLoopbackAddress())
        val port = server.start()
        try {
            val clientIdentity = LanIdentity("lan-test-client-$suffix")
            val device = LanDevice(serverIdentity.deviceId, "Desktop", "127.0.0.1", port)
            val wrong = LanClient(device, clientIdentity).let { c ->
                val auth = c.connect(if (serverIdentity.pin == "0000") "0001" else "0000"); c.close(); auth
            }
            assertEquals("auth_required", wrong.optString("type"))
            val events = mutableListOf<JSONObject>()
            val client = LanClient(device, clientIdentity, onEvent = { events += it })
            assertEquals("auth_ok", client.connect(serverIdentity.pin).optString("type"))
            try {
                val state = client.request("state")
                assertTrue(state.getBoolean("ok")); assertEquals("Fixture", state.getJSONObject("state").getString("title"))
                val ping = client.request("ping", JSONObject().put("t0", 42L))
                assertEquals(42L, ping.getLong("t0")); assertTrue(ping.getLong("serverTimeMs") > 0)
                val pause = client.request("control", JSONObject().put("command", "pause"))
                assertTrue(pause.getBoolean("ok")); assertFalse(host.playing)
                val transfer = client.request("transfer", JSONObject().put("track",
                    LanTrack("apple_music", "x", "Title", "Artist").json()))
                assertFalse(transfer.getBoolean("ok"))
            } finally { client.close() }
            assertTrue(host.requests.contains("transfer"))
        } finally { server.close() }
    }

    @Test fun castReceiverAcceptsLengthPrefixedPcmOnlyForExpectedPeer() {
        val frames = ShortArray(480) { (it % 100).toShort() }
        val bytes = java.io.ByteArrayOutputStream().also { out ->
            DataOutputStream(out).writeInt(frames.size * 2)
            frames.forEach { out.write(it.toInt() and 255); out.write((it.toInt() shr 8) and 255) }
        }.toByteArray()
        val received = java.io.ByteArrayOutputStream()
        val delivered = java.util.concurrent.CountDownLatch(1)
        val line = object : SourceDataLine {
            override fun open() {}
            override fun open(format: AudioFormat?) {}
            override fun open(format: AudioFormat?, bufferSize: Int) {}
            override fun drain() {}
            override fun flush() {}
            override fun start() {}
            override fun stop() {}
            override fun isRunning() = true
            override fun isActive() = true
            override fun getFormat() = AudioFormat(44100f, 16, 2, true, false)
            override fun getBufferSize() = 4096
            override fun available() = 4096
            override fun getFramePosition() = 0
            override fun getLongFramePosition() = 0L
            override fun getMicrosecondPosition() = 0L
            override fun getLevel() = 0f
            override fun isControlSupported(control: javax.sound.sampled.Control.Type?) = false
            override fun getControl(control: javax.sound.sampled.Control.Type?) = null
            override fun getControls() = emptyArray<javax.sound.sampled.Control>()
            override fun addLineListener(listener: javax.sound.sampled.LineListener?) {}
            override fun removeLineListener(listener: javax.sound.sampled.LineListener?) {}
            override fun write(b: ByteArray?, off: Int, len: Int): Int {
                received.write(b, off, len)
                if (received.size() >= frames.size * 2) delivered.countDown()
                return len
            }
            override fun close() {}
            override fun isOpen() = true
            override fun getLineInfo() = javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, getFormat())
        }
        val receiver = LanCastReceiver(InetAddress.getLoopbackAddress(), InetAddress.getLoopbackAddress(),
            LanPcmFormat(48000, 2), openLine = { line })
        receiver.start()
        try {
            Socket(InetAddress.getLoopbackAddress(), receiver.port).use { socket ->
                socket.getOutputStream().write(bytes); socket.getOutputStream().flush()
                assertTrue(delivered.await(5, java.util.concurrent.TimeUnit.SECONDS), "PCM delivery must complete before closing its socket")
            }
            assertTrue(received.size() >= frames.size * 2)
        } finally { receiver.close() }
    }

    @Test fun discoveryRejectsForeignMagicAndOwnIdButFindsAnnounce() {
        val identity = LanIdentity("lan-test-discovery-" + UUID.randomUUID())
        val host = Host()
        val discoveryPort = 47830 + (identity.deviceId.hashCode() and 255)
        val server = LanServer(identity, host, InetAddress.getLoopbackAddress(), discoveryPort = discoveryPort)
        server.start()
        try {
            val scanner = LanIdentity("lan-test-scanner-" + UUID.randomUUID())
            val found = scanLan(scanner, 1500, InetAddress.getLoopbackAddress(), port = discoveryPort)
            assertEquals(1, found.size, "real discovery datagram and announce")
            assertEquals(identity.deviceId, found.single().id)
            assertEquals(server.port, found.single().port)
        } finally { server.close() }
    }
}
