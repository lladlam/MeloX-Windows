package melox.dlna

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.*
import melox.model.SearchSong
import melox.player.AudioPlayer
import melox.playback.*
import kotlin.math.*
import kotlin.test.*

/** Explicit loopback UPnP renderer + real SOAP/HTTP/audio bytes; not a physical DLNA speaker. */
class DlnaIntegrationTest {
    private val root = Files.createTempDirectory("melox-dlna").toFile()
    private val audio = File(root, "generated.wav")
    private val original = AudioPlayer.outputLineFactory
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MeloXDesktopPlayer? = null
    private val renderer = Renderer()
    @BeforeTest fun setup() {
        val frames = 44100 * 15; val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        val second = ByteBuffer.allocate(44100 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(44100) { val value = (5000 * sin(it * 2 * PI * 1000 / 44100)).toInt().toShort(); second.putShort(value).putShort(value) }
        repeat(15) { bytes.put(second.array()) }
        audio.writeBytes(bytes.array()); AudioPlayer.stop(); AudioPlayer.setTogetherBarrier(false)
        AudioPlayer.outputLineFactory = { Sink().line }
    }
    @AfterTest fun cleanup(): Unit = runBlocking {
        DlnaSession.disconnect(false); melox.lan.MeloXLanRuntime.endSession()
        withContext(Dispatchers.Main) { player?.release() }
        AudioPlayer.stop(); AudioPlayer.outputLineFactory = original; scope.cancel(); renderer.close(); root.deleteRecursively(); Unit
    }
    private suspend fun install() = withContext(Dispatchers.Main) {
        val controller = MeloXDesktopPlayer({ DesktopPlaybackRequest(audio.absolutePath) }, { start ->
            AudioPlayer.play(start.request.uri, start.track, start.request.headers, start.positionMs, start.durationMs,
                start.playWhenReady, start.onEnded, start.onFailure, autoMix = false)
        }, scope, Dispatchers.IO, {}, { _, _ -> })
        player = controller; PlaybackCommands.adoptController(controller)
        controller.setMediaItems(listOf(SearchSong(11, "Generated <tone>", "Fixture & artist", "Fixture", null, durationMs = 15000)), 0, 0)
        controller.prepare(); controller.play(); PlaybackCommands.publishQueue(controller)
    }
    private suspend fun until(timeoutMs: Long = 30_000, block: () -> Boolean) = withTimeout(timeoutMs) { while (!block()) delay(10) }

    @Test fun nativeWindowsPathsAndEscapedUrisKeepTheirAudioFormat() {
        assertEquals("audio/wav", dlnaMimeType("D:\\Music files\\测试 tone.WAV"))
        assertEquals("audio/flac", dlnaMimeType("\\\\server\\Music files\\tone.flac"))
        assertEquals("audio/wav", dlnaMimeType("file:///D:/Music%20files/tone.wav"))
        assertEquals("audio/flac", dlnaMimeType("https://example.com/tone.flac?token=x.mp3"))
        assertEquals("audio/wav", dlnaMimeType("/home/music files/tone.wav"))
    }

    @Test fun confirmedRendererFetchesRealAudioAndAllTransportControlsRouteToSoap() = runBlocking {
        // Decoder extraction of the bundled 172MB FFmpeg is disk-bound; allow for
        // a concurrently loaded machine without touching protocol deadlines.
        install(); until(120_000) { AudioPlayer.isReady() && AudioPlayer.state.value.isPlaying }
        DlnaSession.cast(renderer.device)
        assertTrue(DlnaSession.active); assertTrue(DlnaSession.state.value.playing)
        assertFalse(AudioPlayer.state.value.isPlaying, "Local output pauses only after renderer confirms PLAYING")
        assertContentEquals(audio.readBytes().copyOf(4096), renderer.mediaBytes)
        assertEquals("audio/wav", renderer.contentType)
        assertFalse(renderer.mediaUrl.contains(audio.name), "Renderer gets a session grant, not a local path")
        assertTrue(renderer.metadata.contains("object.item.audioItem.musicTrack"))
        assertTrue(renderer.metadata.contains("Generated &lt;tone&gt;"))
        withContext(Dispatchers.Main) { player!!.togglePlay() }
        until { renderer.calls.contains("Pause") && !DlnaSession.state.value.playing }
        withContext(Dispatchers.Main) { player!!.seekTo(4000); player!!.setVolume(.37f); player!!.togglePlay() }
        until { renderer.position == "00:00:04" && renderer.volume == "37" && DlnaSession.state.value.playing }
        assertTrue(renderer.calls.contains("SetVolume"))
        DlnaSession.disconnect()
        assertFalse(DlnaSession.active)
        until { AudioPlayer.state.value.isPlaying && AudioPlayer.getCurrentPositionMs() >= 4000 }
        assertTrue(renderer.calls.last() == "Stop")
    }
    @Test fun rendererThatNeverConfirmsPlaybackIsAnErrorAndDoesNotPauseTheLocalPlayer() = runBlocking {
        renderer.confirm = false
        install(); until { AudioPlayer.state.value.isPlaying }
        assertFailsWith<IllegalStateException> { DlnaSession.cast(renderer.device) }
        assertFalse(DlnaSession.active); assertNotNull(DlnaSession.state.value.error)
        assertTrue(AudioPlayer.state.value.isPlaying)
    }
    @Test fun changingTheRealQueueRetainsTheRendererAndInstallsTheNewTrackAfterThePanelIsGone() = runBlocking {
        install(); until { AudioPlayer.state.value.isPlaying }
        DlnaSession.cast(renderer.device)
        val oldUrl = renderer.mediaUrl
        withContext(Dispatchers.Main) {
            player!!.setMediaItems(listOf(SearchSong(12, "Replacement tone", "Fixture", "Fixture", null, durationMs = 15000)), 0, 0)
            player!!.prepare(); PlaybackCommands.publishQueue(player!!)
        }
        until { renderer.metadata.contains("Replacement tone") && !DlnaSession.state.value.busy }
        assertTrue(DlnaSession.active); assertEquals(renderer.device.id, DlnaSession.state.value.device?.id)
        assertTrue(DlnaSession.state.value.playing); assertFalse(AudioPlayer.state.value.isPlaying)
        assertNotEquals(oldUrl, renderer.mediaUrl, "Each track gets a fresh, revocable audio grant")
        DlnaSession.disconnect(false)
        delay(500)
        assertFalse(DlnaSession.active, "A pending track switch cannot reconnect after explicit disconnect")
    }
    @Test fun descriptionAndChunkedSoapUseRealHttpAndRejectExternalEntityXml() {
        val description = DlnaHttp.request(renderer.base + "/description")
        val device = DlnaXml.devices(description.body, renderer.base + "/description").single()
        assertEquals(renderer.device.transport.controlUrl, device.transport.controlUrl)
        assertEquals("Loopback renderer", device.name)
        assertEquals("STOPPED", DlnaController.call(device.transport, "GetTransportInfo")["CurrentTransportState"])
        assertFailsWith<IllegalArgumentException> { DlnaXml.parse("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///etc/passwd'>]><x>&y;</x>") }
        assertEquals(renderer.base + "/description", DlnaDiscovery.location("HTTP/1.1 200 OK\r\nLOCATION: ${renderer.base}/description\r\n\r\n"))
    }
    private class Renderer : AutoCloseable {
        private val executor = Executors.newCachedThreadPool { Thread(it, "DLNA-fixture").apply { isDaemon = true } }
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 4)
        val base = "http://127.0.0.1:${server.address.port}"
        private val service = "urn:schemas-upnp-org:service:AVTransport:1"
        val device = DlnaDevice("fixture", "Loopback renderer", "Fixture", "Fixture", "$base/description",
            DlnaService(service, "$base/transport"), DlnaService("urn:schemas-upnp-org:service:RenderingControl:1", "$base/rendering"))
        val calls = CopyOnWriteArrayList<String>()
        @Volatile var confirm = true
        @Volatile var mediaBytes = ByteArray(0)
        @Volatile var contentType: String? = null
        @Volatile var mediaUrl = ""
        @Volatile var metadata = ""
        @Volatile var playing = false
        @Volatile var position = "00:00:00"
        @Volatile var volume = "50"
        init {
            server.executor = executor
            server.createContext("/description") { exchange ->
                val body = """<root><device><UDN>fixture</UDN><friendlyName>Loopback renderer</friendlyName><serviceList><service><serviceType>$service</serviceType><controlURL>/transport</controlURL></service></serviceList></device></root>""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }; exchange.close()
            }
            fun endpoint(path: String) { server.createContext(path) { exchange ->
                try {
                    val method = exchange.requestHeaders.getFirst("SOAPACTION").trim('"').substringAfter('#')
                    val fields = DlnaXml.fields(exchange.requestBody.bufferedReader().readText()); calls += method
                    val values = when (method) {
                        "SetAVTransportURI" -> { mediaUrl = fields.getValue("CurrentURI"); metadata = fields.getValue("CurrentURIMetaData"); emptyMap() }
                        "Play" -> {
                            if (confirm) {
                                val connection = URI(mediaUrl).toURL().openConnection(); connection.setRequestProperty("Range", "bytes=0-4095")
                                connection.connectTimeout = 3000; connection.readTimeout = 3000
                                contentType = connection.contentType; mediaBytes = connection.getInputStream().use { it.readNBytes(4096) }; playing = mediaBytes.isNotEmpty()
                            }; emptyMap()
                        }
                        "Pause", "Stop" -> { playing = false; emptyMap() }
                        "Seek" -> { position = fields.getValue("Target"); emptyMap() }
                        "SetVolume" -> { volume = fields.getValue("DesiredVolume"); emptyMap() }
                        "GetVolume" -> mapOf("CurrentVolume" to volume)
                        "GetPositionInfo" -> mapOf("RelTime" to position, "TrackDuration" to "00:00:15")
                        "GetTransportInfo" -> mapOf("CurrentTransportState" to if (playing) "PLAYING" else "STOPPED")
                        else -> emptyMap()
                    }
                    val bytes = ("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:${method}Response xmlns:u=\"$service\">" +
                        values.entries.joinToString("") { "<${it.key}>${DlnaXml.escape(it.value)}</${it.key}>" } + "</u:${method}Response></s:Body></s:Envelope>").toByteArray()
                    exchange.sendResponseHeaders(200, 0); exchange.responseBody.use { it.write(bytes) }
                } finally { exchange.close() }
            } }
            endpoint("/transport"); endpoint("/rendering"); server.start()
        }
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
    private class Sink {
        private val count = AtomicLong(); private val running = AtomicBoolean(); private val closed = AtomicBoolean()
        val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args -> when (method.name) {
            "write" -> if (closed.get()) 0 else { val size = minOf(args!![2] as Int, 1764); Thread.sleep(10); count.addAndGet(size.toLong()); size }
            "start" -> { running.set(true); null }; "stop" -> { running.set(false); null }; "close" -> { closed.set(true); null }
            "isOpen" -> !closed.get(); "isRunning", "isActive" -> running.get(); "getLongFramePosition" -> count.get() / 4
            "getFramePosition" -> (count.get() / 4).toInt(); "getMicrosecondPosition" -> count.get() * 1_000_000 / 176400
            "available", "getBufferSize" -> 17640; "getLevel" -> 0f; else -> null
        } } as SourceDataLine
    }
}
