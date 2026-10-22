package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.lan.LanDevice
import melox.lan.MeloXLanRuntime
import melox.lan.scanLan
import org.json.JSONObject

/** Desktop entry to the same MELOX-LAN v1 link implemented by Android. */
@Composable
internal fun MeloXLanDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    val runtime = MeloXLanRuntime
    var devices by remember { mutableStateOf<List<LanDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<LanDevice?>(null) }
    var pin by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<JSONObject?>(null) }
    var enabled by remember { mutableStateOf(runtime.identity.enabled) }
    var trusted by remember { mutableStateOf(runtime.identity.trusted()) }
    var recent by remember { mutableStateOf(runtime.identity.recent()) }
    var busy by remember { mutableStateOf(false) }
    fun rescan() {
        if (scanning) return
        scanning = true; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { scanLan(runtime.identity) } }
                .onSuccess { devices = it }
                .onFailure { error = it.message ?: "扫描失败" }
            scanning = false
        }
    }
    fun connect(device: LanDevice) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                runtime.connect(device, pin) { event ->
                    if (event.optString("event") == "state") remote = event.optJSONObject("state")
                }
            } }.onSuccess {
                if (it.optString("type") == "auth_ok") {
                    connected = true; selected = device
                    remote = withContext(Dispatchers.IO) { runCatching { runtime.remote("state").optJSONObject("state") }.getOrNull() }
                    recent = runtime.identity.recent()
                } else error = "配对失败：${it.optString("reason")}"
            }.onFailure { error = it.message ?: "连接失败" }
            busy = false
        }
    }
    fun command(name: String) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { runtime.remote("control", JSONObject().put("command", name)) } }
                .onSuccess { remote = withContext(Dispatchers.IO) { runCatching { runtime.remote("state").optJSONObject("state") }.getOrNull() } }
                .onFailure { error = it.message }
        }
    }
    fun remoteAction(name: String, fields: JSONObject = JSONObject()) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { runtime.remote(name, fields) } }
                .onSuccess { if (!it.optBoolean("ok")) error = it.optString("error") }
                .onFailure { error = it.message }
            busy = false
        }
    }
    val background = MaterialTheme.colorScheme.surface
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(520.dp).heightIn(max = 760.dp).clip(RoundedCornerShape(22.dp))
            .background(background).padding(22.dp).testTag("lan-dialog").verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("局域网播放", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("${runtime.identity.deviceName} · ${runtime.address}:${runtime.port}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("PIN ${runtime.identity.pin}  ·  ${runtime.status.collectAsState().value}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DesktopDialogButton(if (enabled) "关闭局域网" else "开启局域网", "lan-enabled") {
                    enabled = !enabled; runtime.setEnabled(enabled)
                }
                DesktopDialogButton("重新生成PIN", "lan-new-pin") {
                    runtime.identity.regeneratePin()
                }
                DesktopDialogButton("扫描设备", "lan-scan") { rescan() }
            }
            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.testTag("lan-error")) }
            if (connected) {
                val state = remote
                Text("已连接 ${selected?.name.orEmpty()}", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("lan-connected"))
                Text(state?.optString("title").orEmpty().ifBlank { "未在播放" } + " — " + state?.optString("artist").orEmpty(), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopDialogButton("上一首", "lan-previous") { command("previous") }
                    DesktopDialogButton(if (state?.optBoolean("playing") == true) "暂停" else "播放", "lan-toggle") { command("toggle") }
                    DesktopDialogButton("下一首", "lan-next") { command("next") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopDialogButton("接力播放", "lan-transfer") { remoteAction("transfer") }
                    DesktopDialogButton("投放音频", "lan-cast") { busy = true; scope.launch {
                        runCatching { withContext(Dispatchers.IO) { runtime.cast() } }.onFailure { error = it.message }; busy = false } }
                    DesktopDialogButton("同步听歌", "lan-together") { busy = true; scope.launch {
                        runCatching { withContext(Dispatchers.IO) { runtime.together(useRelay = true) } }.onFailure { error = it.message }; busy = false } }
                }
                DesktopDialogButton("断开", "lan-disconnect") {
                    runtime.endSession(); connected = false; selected = null; remote = null
                }
            } else {
                devices.forEach { device ->
                    Row(Modifier.fillMaxWidth().testTag("lan-device:${device.id}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name.ifBlank { device.id }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("${device.platform} · ${device.address}:${device.port}${if (device.trusted) " · 已信任" else ""}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DesktopDialogButton("连接", "lan-connect:${device.id}", prominent = true) { connect(device) }
                    }
                }
                if (devices.isEmpty() && !scanning) Text("点“扫描设备”查找同一局域网内的 MeloX", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(pin, { pin = it.filter(Char::isDigit).take(4) },
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).testTag("lan-pin"),
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface))
            }
            if (trusted.isNotEmpty()) {
                Text("常用设备", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                trusted.forEach { peer ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(peer.optString("name").ifBlank { peer.optString("deviceId") }, Modifier.weight(1f), fontSize = 12.sp)
                        DesktopDialogButton("移除", "lan-untrust:${peer.optString("deviceId")}") {
                            runtime.identity.untrust(peer.optString("deviceId")); trusted = runtime.identity.trusted()
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) Text("最近连接：${recent.joinToString { it.optString("name") }}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            DesktopDialogButton("关闭", "lan-close") { onDismiss() }
        }
    }
}
