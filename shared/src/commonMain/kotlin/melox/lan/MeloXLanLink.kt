package melox.lan

import java.io.*
import java.net.*
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/** Wire-compatible with Android's MeloXLanServer/Client and MELOX-LAN.md v1. */
class LanServer(val identity: LanIdentity, private val host: LanHost,
    val address: InetAddress = lanAddress(), private val discoveryPort: Int = LAN_DISCOVERY_PORT) : AutoCloseable {
    private val workers = Executors.newCachedThreadPool { Thread(it, "MeloX-LAN").apply { isDaemon = true } }
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "MeloX-LAN-events").apply { isDaemon = true } }
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val clients = ConcurrentHashMap<Socket, OutputStream>()
    private val failedPins = ConcurrentHashMap<String, Pair<Int, Long>>()
    private val together = ConcurrentHashMap.newKeySet<Socket>()
    private val preparations = ConcurrentHashMap<Socket, JSONObject>()
    private fun capabilities() = JSONArray(listOf("control", "cast", "transfer", "together") +
        if (host.supportsBarrier) listOf(LAN_TOGETHER_BARRIER) else emptyList())
    @Volatile private var togetherOwner: Socket? = null
    @Volatile private var castOwner: Socket? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    val port: Int get() = server?.localPort ?: -1

    @Synchronized fun start(): Int {
        if (server != null) return port
        require(identity.enabled)
        val bound = (0..9).firstNotNullOfOrNull { offset ->
            val socket = ServerSocket()
            try { socket.reuseAddress = true; socket.bind(InetSocketAddress(address, identity.port + offset)); socket }
            catch (_: IOException) { socket.close(); null }
        } ?: throw IOException("Unable to bind LAN control port")
        server = bound
        workers.execute {
            while (!bound.isClosed) {
                val socket = try { bound.accept() } catch (_: IOException) { break }
                if (!isLanAddress(socket.inetAddress) || sockets.size >= 8) { socket.close(); continue }
                sockets += socket
                workers.execute { serve(socket) }
            }
        }
        try {
            udp = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(discoveryPort)) }
            workers.execute { discoverLoop() }
        } catch (_: IOException) { /* Manual address connection remains available. */ }
        timer.scheduleAtFixedRate({
            clients.forEach { (socket, out) ->
                runCatching {
                    writeLanJson(out, JSONObject().put("type", "event").put("event", "state").put("state", host.state()))
                    if (socket === togetherOwner) host.togetherStatus()?.let { writeLanJson(out, it) }
                }.onFailure { runCatching { socket.close() } }
            }
        }, 200, 200, TimeUnit.MILLISECONDS)
        return port
    }

    private fun announcement(requester: String, nonce: String) = JSONObject()
        .put("magic", "MELOX_LAN").put("v", 1).put("type", "announce")
        .put("deviceId", identity.deviceId).put("name", identity.deviceName).put("platform", identity.platform)
        .put("model", System.getProperty("os.name")).put("appVersion", "0.6.5")
        .put("port", port).put("pinRequired", !identity.isTrusted(requester)).put("trusted", identity.isTrusted(requester))
        .put("capabilities", capabilities())
        .put("state", host.state()).put("nonce", nonce)

    private fun discoverLoop() {
        val socket = udp ?: return
        while (!socket.isClosed) {
            try {
                val packet = DatagramPacket(ByteArray(65507), 65507); socket.receive(packet)
                if (!isLanAddress(packet.address)) continue
                val j = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                if (j.optString("magic") != "MELOX_LAN" || j.optInt("v") != 1 || j.optString("type") != "discover" || j.optString("deviceId") == identity.deviceId) continue
                val bytes = announcement(j.optString("deviceId"), j.optString("nonce")).toString().toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
            } catch (_: Exception) { if (socket.isClosed) break }
        }
    }

    private fun serve(socket: Socket) {
        var joined = false
        try {
            socket.soTimeout = 15000; socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream()); val out = socket.getOutputStream()
            var authenticated = false
            val ip = socket.inetAddress.hostAddress
            for (attempt in 1..5) {
                val msg = readLanJson(input) ?: return
                if (msg.optString("type") != "hello" || msg.optInt("v") != 1) return
                val id = msg.optString("deviceId"); if (id.isBlank() || id.length > 128) return
                val failure = failedPins[ip]
                if (failure != null && failure.first >= 5 && System.currentTimeMillis() - failure.second < 60000) return
                val trusted = identity.isTrusted(id)
                if (trusted || msg.optString("pin") == identity.pin) {
                    identity.remember(id, msg.optString("deviceName"), msg.optString("platform"), ip)
                    failedPins.remove(ip)
                    writeLanJson(out, JSONObject().put("type", "auth_ok").put("trusted", trusted)
                        .put("sessionId", UUID.randomUUID().toString()).put("deviceId", identity.deviceId)
                        .put("capabilities", capabilities()))
                    authenticated = true; break
                }
                if (failedPins.size > 128) failedPins.clear()
                failedPins.compute(ip) { _, old -> ((old?.takeIf { System.currentTimeMillis() - it.second < 60000 }?.first ?: 0) + 1) to System.currentTimeMillis() }
                writeLanJson(out, JSONObject().put("type", if (attempt == 5) "auth_failed" else "auth_required")
                    .put("pinRequired", true).put("reason", "pin").put("attemptsLeft", 5 - attempt))
            }
            if (!authenticated) return
            clients[socket] = out
            while (!socket.isClosed) {
                val message = readLanJson(input) ?: break
                if (message.optString("type") == "event") {
                    if (socket === togetherOwner && message.optString("event") in setOf("together.state", "together.commit")) {
                        host.request(message.optString("event"), message, socket.inetAddress)
                    }
                    continue
                }
                if (message.optString("type") != "request") continue
                val action = message.optString("action")
                if (action == "together.prepare") {
                    preparations[socket] = message
                    workers.execute {
                        val result = runCatching { host.request(action, message, socket.inetAddress) }
                            .getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "prepare_failed") }
                        if (result.optBoolean("ok") && !socket.isClosed) {
                            takeTogetherOwnership(socket)
                        }
                        preparations.remove(socket, message)
                        runCatching { writeLanJson(out, result.put("type", "response").put("id", message.optString("id"))) }
                    }
                    continue
                }
                val result = try {
                    when (action) {
                        "state" -> JSONObject().put("ok", true).put("state", host.state())
                        "ping" -> JSONObject().put("ok", true).put("t0", message.opt("t0")).put("serverTimeMs", System.currentTimeMillis())
                        "together.stop" -> if (socket === togetherOwner) host.request(action, message, socket.inetAddress)
                            else JSONObject().put("ok", false).put("error", "not_session_owner")
                        else -> host.request(action, message, socket.inetAddress)
                    }
                } catch (error: Exception) { JSONObject().put("ok", false).put("error", error.message ?: "request_failed") }
                if (action in setOf("together.start", "together.prepare") && result.optBoolean("ok")) {
                    joined = true; takeTogetherOwnership(socket)
                }
                if (action == "cast.start" && result.optBoolean("ok")) castOwner = socket
                if (action == "together.stop" && socket === togetherOwner) {
                    joined = false; together -= socket; togetherOwner = null
                }
                writeLanJson(out, result.put("type", "response").put("id", message.optString("id")))
            }
        } catch (_: Exception) { /* Connection-local failure, close all owned resources. */ }
        finally {
            preparations.remove(socket)?.let { host.cancelPreparation(it.optString("sessionId"), it.optLong("generation")) }
            if (socket === togetherOwner) { togetherOwner = null; host.disconnected() }
            if (socket === castOwner) {
                castOwner = null
                runCatching { host.request("cast.stop", JSONObject(), socket.inetAddress) }
            }
            together -= socket; clients.remove(socket); sockets -= socket; runCatching { socket.close() }
        }
    }

    fun sendTogetherControl(command: String, position: Long? = null): Boolean {
        val socket = together.firstOrNull() ?: return false
        val out = clients[socket] ?: return false
        return runCatching { writeLanJson(out, JSONObject().put("type", "request").put("id", UUID.randomUUID().toString())
            .put("action", "control").put("command", command).put("positionMs", position)); true }.getOrDefault(false)
    }
    @Synchronized private fun takeTogetherOwnership(socket: Socket) {
        val previous = togetherOwner
        together.clear(); together += socket; togetherOwner = socket
        if (previous != null && previous !== socket) clients[previous]?.let { out -> runCatching {
            writeLanJson(out, JSONObject().put("type", "event").put("event", "together.stopped"))
        } }
    }

    fun stopTogetherSession() {
        val socket = togetherOwner ?: return
        togetherOwner = null; together -= socket
        host.disconnected()
        clients[socket]?.let { out -> runCatching {
            writeLanJson(out, JSONObject().put("type", "event").put("event", "together.stopped"))
        } }
    }
    fun castStopped() {
        val socket = castOwner ?: return
        castOwner = null
        clients[socket]?.let { out -> runCatching {
            writeLanJson(out, JSONObject().put("type", "event").put("event", "cast.stopped"))
        } }
    }

    override fun close() {
        runCatching { udp?.close() }; runCatching { server?.close() }; server = null
        sockets.toList().forEach { runCatching { it.close() } }; sockets.clear(); clients.clear()
        timer.shutdownNow(); workers.shutdownNow()
    }
}

class LanClient(device: LanDevice, private val identity: LanIdentity,
    private val onEvent: (JSONObject) -> Unit = {}, private val onRequest: (JSONObject) -> JSONObject = { JSONObject().put("ok", false) },
    private val onClose: () -> Unit = {}) : AutoCloseable {
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private val pending = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    private val closed = AtomicBoolean()
    var device = device
        private set
    val isConnected get() = socket?.isConnected == true && !closed.get()
    @Volatile var capabilities = device.capabilities
        private set
    private val workers = Executors.newScheduledThreadPool(2) { Thread(it, "MeloX-LAN-client").apply { isDaemon = true } }
    fun connect(pin: String): JSONObject {
        require(socket == null)
        val address = InetAddress.getByName(device.address); require(isLanAddress(address)) { "LAN address required" }
        val s = Socket(); socket = s
        try {
            s.connect(InetSocketAddress(address, device.port), 5000); s.soTimeout = 15000; s.tcpNoDelay = true
            val out = s.getOutputStream(); output = out; val input = BufferedInputStream(s.getInputStream())
            writeLanJson(out, JSONObject().put("type", "hello").put("v", 1).put("deviceId", identity.deviceId)
                .put("deviceName", identity.deviceName).put("platform", identity.platform).put("appVersion", "0.6.5").put("pin", pin))
            val auth = readLanJson(input) ?: throw EOFException()
            if (auth.optString("type") != "auth_ok") { close(); return auth }
            auth.optJSONArray("capabilities")?.let { values ->
                capabilities = (0 until values.length()).map { values.optString(it) }.toSet()
            }
            val authenticatedId = auth.optString("deviceId").ifBlank { device.id }
            device = device.copy(id = authenticatedId, capabilities = capabilities)
            identity.remember(authenticatedId, device.name, device.platform, device.address)
            workers.execute {
                try { while (!s.isClosed) {
                    val j = readLanJson(input) ?: break
                    when (j.optString("type")) {
                        "response" -> pending.remove(j.optString("id"))?.complete(j)
                        "event" -> onEvent(j)
                        "request" -> {
                            val result = runCatching { onRequest(j) }.getOrElse { JSONObject().put("ok", false) }
                            writeLanJson(out, result.put("type", "response").put("id", j.optString("id")))
                        }
                    }
                } } catch (_: Exception) {} finally { close() }
            }
            workers.scheduleAtFixedRate({ runCatching { request("ping", JSONObject().put("t0", System.currentTimeMillis())) } }, 5, 5, TimeUnit.SECONDS)
            return auth
        } catch (error: Exception) { close(); throw error }
    }
    fun request(action: String, fields: JSONObject = JSONObject(), timeoutMs: Long = 12000): JSONObject {
        check(!closed.get()) { "LAN connection closed" }
        val id = UUID.randomUUID().toString(); val future = CompletableFuture<JSONObject>(); pending[id] = future
        try {
            writeLanJson(checkNotNull(output), JSONObject(fields.toString()).put("id", id).put("type", "request").put("action", action))
            return future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } finally { pending.remove(id) }
    }
    fun event(event: JSONObject) { writeLanJson(checkNotNull(output), event.put("type", "event")) }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket?.close() }; pending.values.forEach { it.completeExceptionally(IOException("LAN disconnected")) }; pending.clear()
        workers.shutdownNow(); onClose()
    }
}

fun scanLan(identity: LanIdentity, timeoutMs: Int = 1500, target: InetAddress = InetAddress.getByName("255.255.255.255"), port: Int = LAN_DISCOVERY_PORT): List<LanDevice> {
    require(identity.enabled)
    DatagramSocket().use { socket ->
        socket.broadcast = true; socket.soTimeout = 150
        val nonce = UUID.randomUUID().toString()
        val bytes = JSONObject().put("magic", "MELOX_LAN").put("v", 1).put("type", "discover")
            .put("deviceId", identity.deviceId).put("nonce", nonce).toString().toByteArray(Charsets.UTF_8)
        val targets = (listOf(target) + NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses.mapNotNull { a -> a.broadcast } }).distinct()
        targets.forEach { runCatching { socket.send(DatagramPacket(bytes, bytes.size, it, port)) } }
        val result = linkedMapOf<String, LanDevice>(); val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) try {
            val p = DatagramPacket(ByteArray(65507), 65507); socket.receive(p)
            if (!isLanAddress(p.address)) continue
            val j = JSONObject(String(p.data, 0, p.length, Charsets.UTF_8))
            if (j.optString("magic") != "MELOX_LAN" || j.optInt("v") != 1 || j.optString("nonce") != nonce || j.optString("type") != "announce") continue
            val id = j.optString("deviceId"); val controlPort = j.optInt("port")
            if (id.isBlank() || id == identity.deviceId || controlPort !in 1024..65535) continue
            result[id] = LanDevice(id, j.optString("name"), p.address.hostAddress, controlPort, j.optString("platform"), j.optBoolean("trusted"), j.optJSONObject("state") ?: JSONObject())
        } catch (_: SocketTimeoutException) {} catch (_: org.json.JSONException) {}
        return result.values.toList()
    }
}
