package melox.lan

import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.UUID
import melox.platform.MeloXPreferences
import org.json.JSONArray
import org.json.JSONObject

const val LAN_DISCOVERY_PORT = 47830
const val LAN_CONTROL_PORT = 47831
const val LAN_MAX_LINE = 256 * 1024
const val LAN_TOGETHER_BARRIER = "together_barrier_v2"

data class LanTrack(val source: String, val id: String, val title: String, val artist: String,
    val album: String = "", val artworkUrl: String? = null, val durationMs: Long = 0) {
    fun json() = JSONObject().put("source", source).put("id", id).put("title", title)
        .put("artist", artist).put("album", album).put("artworkUrl", artworkUrl).put("durationMs", durationMs)
    companion object {
        fun parse(j: JSONObject): LanTrack {
            val source = j.getString("source"); val id = j.getString("id")
            require(source.isNotBlank() && id.isNotBlank() && id.length <= 1024)
            return LanTrack(source, id, j.optString("title"), j.optString("artist"), j.optString("album"),
                j.optString("artworkUrl").takeIf { it.startsWith("https://") || it.startsWith("http://") }, j.optLong("durationMs").coerceAtLeast(0))
        }
        fun list(j: JSONArray?): List<LanTrack> {
            if (j == null) return emptyList()
            require(j.length() <= 1000) { "queue_too_large" }
            return (0 until j.length()).map { parse(j.getJSONObject(it)) }
        }
    }
}

data class LanDevice(val id: String, val name: String, val address: String, val port: Int,
    val platform: String = "", val trusted: Boolean = false, val state: JSONObject = JSONObject(),
    val capabilities: Set<String> = emptySet())

class LanIdentity(name: String = "melox_lan") {
    private val prefs = MeloXPreferences(name)
    val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.putString("device_id", it) }
    var deviceName: String
        get() = prefs.getString("device_name", null) ?: "${runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Desktop")} · MeloX"
        set(v) { prefs.putString("device_name", v.take(100)) }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) { prefs.putBoolean("enabled", v) }
    var port: Int
        get() = prefs.getInt("port", LAN_CONTROL_PORT).coerceIn(1024, 65526)
        set(v) { prefs.putInt("port", v.coerceIn(1024, 65526)) }
    val pin: String get() = prefs.getString("pin", null) ?: regeneratePin()
    fun regeneratePin() = "%04d".format(SecureRandom().nextInt(10000)).also { prefs.putString("pin", it) }
    val platform: String get() = if (System.getProperty("os.name").startsWith("Windows")) "windows" else "linux"
    private fun list(key: String): List<JSONObject> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]")); (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())
    @Synchronized fun recent() = list("recent_peers")
    @Synchronized fun trusted() = list("trusted_devices")
    fun isTrusted(id: String) = trusted().any { it.optString("deviceId") == id }
    @Synchronized fun remember(id: String, name: String, platform: String, address: String) {
        val peer = JSONObject().put("deviceId", id).put("name", name).put("platform", platform).put("address", address)
        prefs.putString("recent_peers", JSONArray((recent().filterNot { it.optString("deviceId") == id } + peer).takeLast(10)).toString())
    }
    @Synchronized fun trust(id: String) {
        val peer = recent().firstOrNull { it.optString("deviceId") == id } ?: return
        prefs.putString("trusted_devices", JSONArray(trusted().filterNot { it.optString("deviceId") == id } + peer).toString())
    }
    @Synchronized fun untrust(id: String) { prefs.putString("trusted_devices", JSONArray(trusted().filterNot { it.optString("deviceId") == id }).toString()) }
}

internal fun readLanJson(input: InputStream): JSONObject? {
    val bytes = ByteArrayOutputStream()
    while (true) {
        val b = input.read()
        if (b < 0) { if (bytes.size() == 0) return null; throw EOFException("Unterminated LAN message") }
        if (b == 10) break
        if (bytes.size() >= LAN_MAX_LINE) throw IOException("LAN message exceeds 256 KiB")
        bytes.write(b)
    }
    return JSONObject(bytes.toString("UTF-8").trimEnd('\r'))
}

internal fun writeLanJson(output: OutputStream, value: JSONObject) {
    val bytes = value.toString().toByteArray(Charsets.UTF_8)
    require(bytes.size <= LAN_MAX_LINE) { "LAN message exceeds 256 KiB" }
    synchronized(output) { output.write(bytes); output.write(10); output.flush() }
}

internal fun lanAddress(): InetAddress = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual && !Regex("(?i).*(tun|tap|wg|tailscale|docker|veth).* ".trim()).matches(it.name) }
    .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    ?: InetAddress.getLoopbackAddress()

internal fun isLanAddress(address: InetAddress) = address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress

interface LanHost {
    val supportsBarrier: Boolean get() = false
    fun state(): JSONObject
    fun queue(): List<LanTrack>
    fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject
    fun disconnected() {}
    fun togetherStatus(): JSONObject? = null
    fun cancelPreparation(sessionId: String, generation: Long) {}
}
