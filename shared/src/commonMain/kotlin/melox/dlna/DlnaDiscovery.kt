package melox.dlna

import java.net.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object DlnaDiscovery {
    internal fun location(response: String): String? = if (!response.startsWith("HTTP/1.1 200", true)) null else response.lineSequence()
        .firstOrNull { it.substringBefore(':').equals("location", true) }?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http://", true) }
    suspend fun scan(): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 250
            val target = InetAddress.getByName("239.255.255.250")
            listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "ssdp:all").forEach { st ->
                val bytes = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\nUSER-AGENT: MeloX/1.0 UPnP/1.1\r\n\r\n").toByteArray(Charsets.US_ASCII)
                repeat(2) { socket.send(DatagramPacket(bytes, bytes.size, target, 1900)) }
            }
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline && locations.size < 48) {
                ensureActive(); val packet = DatagramPacket(ByteArray(16 * 1024), 16 * 1024)
                try { socket.receive(packet); location(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let(locations::add) }
                catch (_: SocketTimeoutException) {}
            }
        }
        val permits = Semaphore(4)
        locations.map { url -> async { permits.withPermit {
            try { val response = DlnaHttp.request(url); if (response.code == 200) DlnaXml.devices(response.body, url) else emptyList() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
        } } }.awaitAll().flatten().distinctBy { it.id }
    }
}
