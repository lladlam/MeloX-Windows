package melox.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class LanOutputMode { Local, Cast, Together }
internal object MeloXOutputArbiter { val operations = Mutex() }

data class LanSessionSnapshot(
    val device: LanDevice? = null,
    val mode: LanOutputMode = LanOutputMode.Local,
    val busy: Boolean = false,
    val remote: JSONObject? = null,
    val connections: List<LanDevice> = emptyList(),
)

/** Process-owned connection: dismissing an output panel or changing tracks never closes it. */
object MeloXLanSession {
    private val mutable = MutableStateFlow(LanSessionSnapshot())
    val state = mutable.asStateFlow()
    private val operations = Mutex()
    internal fun connectionStopped() { mutable.update { LanSessionSnapshot(busy = it.busy) } }
    internal fun outputStopped(mode: LanOutputMode) {
        mutable.update { if (it.mode == mode) it.copy(mode = LanOutputMode.Local) else it }
    }
    internal fun refreshConnections() { mutable.update { it.copy(connections = MeloXLanRuntime.connectedDevices) } }

    private suspend fun <T> operation(block: suspend () -> T): T = MeloXOutputArbiter.operations.withLock { operations.withLock {
        mutable.update { it.copy(busy = true) }
        try { withContext(Dispatchers.IO) { block() } }
        finally { mutable.update { it.copy(busy = false) } }
    } }

    suspend fun connect(device: LanDevice, pin: String = ""): JSONObject = operation {
        if (mutable.value.device?.id == device.id && MeloXLanRuntime.isConnected) {
            return@operation JSONObject().put("type", "auth_ok")
        }
        val result = MeloXLanRuntime.connect(device, pin, event = { event ->
            when (event.optString("event")) {
                "state" -> mutable.update { it.copy(remote = event.optJSONObject("state")) }
                "cast.stopped" -> { MeloXLanRuntime.stopCastSender(); mutable.update { it.copy(mode = LanOutputMode.Local) } }
                "together.stopped" -> { MeloXLanRuntime.stopHostTogether(); mutable.update { it.copy(mode = LanOutputMode.Local) } }
            }
        }, onDisconnected = { mutable.value = LanSessionSnapshot(busy = mutable.value.busy, connections = MeloXLanRuntime.connectedDevices) })
        if (result.optString("type") == "auth_ok") {
            val authenticatedDevice = device.copy(id = result.optString("deviceId").ifBlank { device.id })
            mutable.update { it.copy(device = authenticatedDevice,
                connections = MeloXLanRuntime.connectedDevices,
                mode = if (MeloXLanRuntime.selectedClient?.let { c -> MeloXLanTogetherCoordinator.contains(c) } == true) LanOutputMode.Together else LanOutputMode.Local,
                remote = MeloXLanRuntime.remote("state").optJSONObject("state")) }
        }
        result
    }

    private fun endOutput() {
        mutable.update { it.copy(mode = LanOutputMode.Local) }
        MeloXLanRuntime.useLocal()
    }
    suspend fun transfer() = operation {
        melox.dlna.DlnaSession.disconnect(false)
        endOutput(); MeloXLanRuntime.transfer()
    }
    suspend fun cast() = operation {
        melox.dlna.DlnaSession.disconnect(false)
        endOutput()
        mutable.update { it.copy(mode = LanOutputMode.Cast) }
        try { MeloXLanRuntime.cast() }
        catch (error: Exception) { outputStopped(LanOutputMode.Cast); throw error }
    }
    suspend fun together(provideAudio: Boolean) = operation {
        melox.dlna.DlnaSession.disconnect(false)
        if (mutable.value.mode == LanOutputMode.Cast) endOutput()
        mutable.update { it.copy(mode = LanOutputMode.Together) }
        try { MeloXLanRuntime.together(provideAudio) }
        catch (error: Exception) { outputStopped(LanOutputMode.Together); throw error }
    }
    suspend fun useLocal() = operation {
        melox.dlna.DlnaSession.disconnect()
        endOutput()
    }
    suspend fun disconnect() = operation {
        endOutput()
        MeloXLanRuntime.endSession()
        mutable.value = LanSessionSnapshot(busy = true)
    }
    suspend fun control(command: String, fields: JSONObject = JSONObject()) = operation {
        val result = MeloXLanRuntime.remote("control", fields.put("command", command))
        check(result.optBoolean("ok")) { result.optString("error", "control_failed") }
    }
}

/** Android working-tree policy: don't chase a seek that's still being prepared. */
internal fun shouldCorrectTogetherPosition(ready: Boolean, localPositionMs: Long,
    hostPositionMs: Long, elapsedSinceSeekMs: Long): Boolean =
    ready && elapsedSinceSeekMs >= 3_000 && kotlin.math.abs(localPositionMs - hostPositionMs) > 250
