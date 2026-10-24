package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import melox.lan.LanDevice
import melox.lan.MeloXLanRuntime
import melox.lan.MeloXLanSession
import melox.lan.LanOutputMode
import melox.lan.scanLan
import melox.dlna.*
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
    val session by MeloXLanSession.state.collectAsState()
    val dlna by DlnaSession.state.collectAsState()
    var renderers by remember { mutableStateOf(emptyList<DlnaDevice>()) }
    var scanningDlna by remember { mutableStateOf(false) }
    val selected = session.device
    var pin by remember { mutableStateOf("") }
    val connected = selected != null
    val remote = session.remote
    var enabled by remember { mutableStateOf(runtime.identity.enabled) }
    var trusted by remember { mutableStateOf(runtime.identity.trusted()) }
    var recent by remember { mutableStateOf(runtime.identity.recent()) }
    val busy = session.busy || dlna.busy
    val guest by runtime.guestState.collectAsState()
    var localPin by remember { mutableStateOf(runtime.identity.pin) }
    var manualAddress by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf(runtime.port.toString()) }
    var provideAudio by remember { mutableStateOf(true) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        error = null
        scope.launch {
            try { block() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "操作失败" }
        }
    }
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
        error = null
        scope.launch {
            runCatching { MeloXLanSession.connect(device, pin) }.onSuccess {
                if (it.optString("type") == "auth_ok") {
                    recent = runtime.identity.recent()
                } else error = "配对失败：${it.optString("reason")}"
            }.onFailure { error = it.message ?: "连接失败" }
        }
    }
    fun command(name: String) {
        action { MeloXLanSession.control(name) }
    }
    LaunchedEffect(Unit) {
        val known = linkedMapOf<String, Pair<DlnaDevice, Long>>()
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            scanningDlna = true
            try {
                val found = DlnaDiscovery.scan(); val now = System.nanoTime()
                found.forEach { known[it.id] = it to now }
                known.entries.removeAll { now - it.value.second > 180_000_000_000L }
                renderers = known.values.map { it.first }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message }
            finally { scanningDlna = false }
            kotlinx.coroutines.delay(5000)
        }
    }
    val background = MaterialTheme.colorScheme.surface
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(.9f).heightIn(max = (maxHeight - 48.dp).coerceAtLeast(200.dp)).clip(RoundedCornerShape(22.dp))
            .background(background).padding(22.dp).testTag("lan-dialog").verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("局域网播放", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("${runtime.identity.deviceName} · ${runtime.address}:${runtime.port}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("PIN $localPin  ·  ${runtime.status.collectAsState().value}", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LanButton(if (enabled) "关闭局域网" else "开启局域网", "lan-enabled") {
                    action { withContext(Dispatchers.IO) { runtime.setEnabled(!enabled) }; enabled = runtime.identity.enabled }
                }
                LanButton("重新生成PIN", "lan-new-pin") {
                    localPin = runtime.identity.regeneratePin()
                }
                LanButton("扫描设备", "lan-scan") { rescan() }
            }
            if (scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.testTag("lan-error")) }
            if (connected) {
                val state = remote
                Text("已连接 ${selected?.name.orEmpty()}", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("lan-connected"))
                Text(state?.optString("title").orEmpty().ifBlank { "未在播放" } + " — " + state?.optString("artist").orEmpty(), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanButton("上一首", "lan-previous") { command("previous") }
                    LanButton(if (state?.optBoolean("playing") == true) "暂停" else "播放", "lan-toggle") { command("toggle") }
                    LanButton("下一首", "lan-next") { command("next") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanButton("接力播放", "lan-transfer", enabled = !busy) { action { MeloXLanSession.transfer() } }
                    LanButton("投放音频", "lan-cast", prominent = session.mode == LanOutputMode.Cast, enabled = !busy) { action { MeloXLanSession.cast() } }
                    LanButton("同步听歌", "lan-together", prominent = session.mode == LanOutputMode.Together, enabled = !busy) { action { MeloXLanSession.together(provideAudio) } }
                }
                LanButton(if (provideAudio) "✓ 为对方提供音频" else "使用对方音乐账号", "lan-relay") { provideAudio = !provideAudio }
                LanButton("本机播放", "lan-local", enabled = !busy) { action { MeloXLanSession.useLocal() } }
                LanButton("断开", "lan-disconnect") {
                    action { MeloXLanSession.disconnect() }
                }
            }
            session.connections.filter { it.id != selected?.id }.forEach { peer ->
                LanButton("选择 ${peer.name}", "lan-select:${peer.id}", enabled = !busy) { connect(peer) }
            }
            run {
                devices.forEach { device ->
                    Row(Modifier.fillMaxWidth().testTag("lan-device:${device.id}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name.ifBlank { device.id }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("${device.platform} · ${device.address}:${device.port}${if (device.trusted) " · 已信任" else ""}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LanButton("连接", "lan-connect:${device.id}", prominent = true) { connect(device) }
                    }
                }
                if (devices.isEmpty() && !scanning) Text("点“扫描设备”查找同一局域网内的 MeloX", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(pin, { pin = it.filter(Char::isDigit).take(4) },
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp).testTag("lan-pin"),
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface))
                Text("手动连接（地址与控制端口）", fontSize = 12.sp)
                BasicTextField(manualAddress, { manualAddress = it }, Modifier.fillMaxWidth().testTag("lan-manual-address")
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface))
                BasicTextField(manualPort, { manualPort = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth().testTag("lan-manual-port")
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface))
                LanButton("连接地址", "lan-manual-connect", enabled = !busy && manualAddress.isNotBlank() && manualPort.toIntOrNull() in 1024..65535) {
                    connect(LanDevice("manual:$manualAddress:$manualPort", manualAddress, manualAddress.trim(), manualPort.toInt()))
                }
            }
            if (guest) LanButton("退出同步听歌", "lan-together-stop") { action { withContext(Dispatchers.IO) { runtime.stopGuestTogether() } } }
            if (trusted.isNotEmpty()) {
                Text("常用设备", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                trusted.forEach { peer ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(peer.optString("name").ifBlank { peer.optString("deviceId") }, Modifier.weight(1f), fontSize = 12.sp)
                        LanButton("移除", "lan-untrust:${peer.optString("deviceId")}") {
                            runtime.identity.untrust(peer.optString("deviceId")); trusted = runtime.identity.trusted()
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Text("最近连接", fontSize = 12.sp)
                recent.forEach { peer ->
                    val id = peer.optString("deviceId")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(peer.optString("name"), Modifier.weight(1f), fontSize = 12.sp)
                        if (!runtime.identity.isTrusted(id)) LanButton("添加常用", "lan-trust:$id") {
                            runtime.identity.trust(id); trusted = runtime.identity.trusted()
                        }
                    }
                }
            }
            Text("DLNA 设备", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            (listOfNotNull(dlna.device) + renderers).distinctBy { it.id }.forEach { renderer ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(renderer.name, Modifier.weight(1f), fontSize = 13.sp)
                    if (dlna.device?.id == renderer.id) LanButton("本机播放", "lan-dlna-disconnect", enabled = !busy) { action { DlnaSession.disconnect() } }
                    else LanButton("投放", "lan-dlna-cast:${renderer.id}", enabled = !busy) { action { DlnaSession.cast(renderer) } }
                }
            }
            if (dlna.device == null && renderers.isEmpty()) Text(if (scanningDlna) "正在查找 DLNA 设备…" else "未发现 DLNA 设备", fontSize = 12.sp)
            dlna.error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
            LanButton("关闭", "lan-close") { onDismiss() }
        }
        }
    }
}

@Composable
private fun LanButton(label: String, tag: String, prominent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.height(28.dp).widthIn(min = 72.dp).clip(shape)
        .background(if (prominent) Color(0xFFDC0011) else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
        .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .14f), shape)
        .testTag(tag).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.5.sp, color = if (prominent) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}
