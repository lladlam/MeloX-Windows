package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import melox.lan.*
import melox.dlna.*
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.*

/** Working-tree Android output panel, backed by the process-owned LAN session. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun MeloXLanOutputPanel(visible: Boolean, onLocalOptions: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    val session by MeloXLanSession.state.collectAsState()
    val track by AudioPlayer.currentTrack.collectAsState()
    val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val progress by melox.playback.MeloXPlaybackOutput.progress.collectAsState()
    val duration by melox.playback.MeloXPlaybackOutput.durationMs.collectAsState()
    val volume by melox.playback.MeloXPlaybackOutput.volumeLevel.collectAsState()
    val dlna by DlnaSession.state.collectAsState()
    val queue by PlaybackCommands.queue.collectAsState()
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(emptyList<LanDevice>()) }
    var renderers by remember { mutableStateOf(emptyList<DlnaDevice>()) }
    var scanningDlna by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinTarget by remember { mutableStateOf<LanDevice?>(null) }
    var pin by remember { mutableStateOf("") }
    var provideAudio by remember { mutableStateOf(true) }
    var management by remember { mutableStateOf(false) }
    val runtime = MeloXLanRuntime
    val guest by runtime.guestState.collectAsState()
    val player = PlaybackCommands.playerController()
    fun action(dismiss: Boolean = false, block: suspend () -> Unit) {
        if (session.busy || dlna.busy) return
        error = null
        scope.launch {
            try { block(); if (dismiss) onDismiss() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "互联操作失败" }
        }
    }
    fun connect(target: LanDevice, suppliedPin: String = "") = action {
        val result = MeloXLanSession.connect(target, suppliedPin)
        if (result.optString("type") == "auth_ok") { pinTarget = null; pin = "" }
        else { pinTarget = target; pin = ""; if (suppliedPin.isNotEmpty()) error = "配对失败，请检查 PIN" }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                devices = withContext(Dispatchers.IO) {
                    if (runtime.identity.enabled) { runtime.start(); scanLan(runtime.identity) } else emptyList()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "扫描设备失败" }
            delay(2_000)
        }
    }
    LaunchedEffect(Unit) {
        val known = linkedMapOf<String, Pair<DlnaDevice, Long>>()
        while (isActive) {
            scanningDlna = true
            try {
                val found = DlnaDiscovery.scan(); val now = System.nanoTime()
                found.forEach { known[it.id] = it to now }
                known.entries.removeAll { now - it.value.second > 180_000_000_000L }
                renderers = known.values.map { it.first }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "DLNA 扫描失败" }
            finally { scanningDlna = false }
            delay(5000)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss),
            contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = 28.dp).widthIn(max = 360.dp).fillMaxWidth()
                .heightIn(max = (maxHeight - 40.dp).coerceAtLeast(200.dp))
                .meloXGlassSurface(RoundedCornerShape(38.dp), tint = Color.Black.copy(alpha = .10f), surfaceColor = Color.Black.copy(alpha = .28f), dark = true)
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = {})
                .verticalScroll(rememberScrollState()).padding(18.dp).testTag("lan-output-panel")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MeloXArtworkImage(track?.artworkUrl, Color.DarkGray, Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track?.title?.ifBlank { "正在播放" } ?: "正在播放", color = Color.White, fontSize = 19.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track?.artistText.orEmpty(), color = Color.White.copy(alpha = .58f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutputTransport(MeloXSymbol.PreviousTrack, "previous", queue.index > 0) { player?.previous() }
                            OutputTransport(if (playback.isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play, "toggle", track != null) { player?.togglePlay() }
                            OutputTransport(MeloXSymbol.NextTrack, "next", queue.index < queue.songs.lastIndex) { player?.next() }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProgressSection(progress, duration, seekEnabled = track != null) { player?.seekTo((it * duration).toLong()) }
                Spacer(Modifier.height(18.dp))
                Slider(volume, { player?.setVolume(it) }, enabled = dlna.device == null || dlna.device?.rendering != null,
                    modifier = Modifier.fillMaxWidth().height(30.dp).testTag("lan-output-volume"),
                    thumb = { Spacer(Modifier.size(0.dp)) }, track = {
                        Box(Modifier.fillMaxWidth().height(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White.copy(alpha = .20f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(volume).background(Color.White.copy(alpha = .95f)))
                            MeloXSymbolIcon(MeloXSymbol.Volume, size = 18,
                                color = if (volume >= .13f) Color.Black.copy(alpha = .48f) else Color.White.copy(alpha = .78f),
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
                        }
                    })
                Spacer(Modifier.height(16.dp))
                if (session.device != null && dlna.device == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutputAction("接力播放", "transfer", !session.busy, Modifier.weight(1f)) { action(true) { MeloXLanSession.transfer() } }
                        OutputAction("投放音频", "cast", !session.busy, Modifier.weight(1f)) { action(true) { MeloXLanSession.cast() } }
                        OutputAction("同步听歌", "together", !session.busy, Modifier.weight(1f)) { action(true) { MeloXLanSession.together(provideAudio) } }
                    }
                    Text((if (provideAudio) "✓ " else "○ ") + "为对方提供音频", color = Color.White.copy(alpha = .75f), fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().testTag("lan-relay-audio").clickable(enabled = !session.busy) { provideAudio = !provideAudio }.padding(vertical = 12.dp))
                }
                if (session.mode == LanOutputMode.Together || guest) {
                    OutputAction("退出同步听歌", "together-stop", !session.busy, Modifier.fillMaxWidth()) {
                        action { if (runtime.isGuest) withContext(Dispatchers.IO) { runtime.stopGuestTogether() } else MeloXLanSession.useLocal() }
                    }
                }
                OutputDevice("本机播放", "本机音频输出", "local", session.mode == LanOutputMode.Local && dlna.device == null, !session.busy && !dlna.busy) {
                    if (dlna.device != null) action { DlnaSession.disconnect() }
                    else if (session.mode == LanOutputMode.Local) onLocalOptions() else action { MeloXLanSession.useLocal() }
                }
                (session.connections + devices).distinctBy { it.id }.forEach { device ->
                    Spacer(Modifier.height(8.dp))
                    OutputDevice(device.name, "${device.address}:${device.port}" + if (session.device?.id == device.id) " · 已连接" else "",
                        device.id, session.device?.id == device.id && session.mode != LanOutputMode.Local && dlna.device == null, !session.busy && !dlna.busy) {
                        if (session.connections.any { it.id == device.id }) action(true) {
                            MeloXLanSession.connect(device)
                            if (session.mode != LanOutputMode.Cast || session.device?.id != device.id || dlna.device != null) MeloXLanSession.cast()
                        }
                        else connect(device)
                    }
                }
                Text("DLNA 设备", color = Color.White.copy(alpha = .60f), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                (listOfNotNull(dlna.device) + renderers).distinctBy { it.id }.forEach { renderer ->
                    OutputDevice(renderer.name, listOf(renderer.manufacturer, renderer.model).filter(String::isNotBlank).distinct().joinToString(" · "),
                        "dlna:${renderer.id}", dlna.device?.id == renderer.id, !session.busy && !dlna.busy) {
                        if (dlna.device?.id == renderer.id) onDismiss()
                        else action(true) { DlnaSession.cast(renderer) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (renderers.isEmpty() && dlna.device == null) Text(if (scanningDlna) "正在查找 DLNA 设备…" else "未发现 DLNA 设备",
                    color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
                if (dlna.device != null) OutputAction("断开 DLNA", "dlna-disconnect", !dlna.busy, Modifier.fillMaxWidth()) { action { DlnaSession.disconnect() } }
                dlna.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
                Text("配对 PIN：${runtime.identity.pin}", color = Color.White.copy(alpha = .55f), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                if (session.device != null) OutputAction("断开设备", "disconnect", !session.busy, Modifier.fillMaxWidth()) { action { MeloXLanSession.disconnect() } }
                OutputAction("互联设置", "settings", !session.busy, Modifier.fillMaxWidth()) { management = true }
                OutputAction("关闭", "close", true, Modifier.fillMaxWidth(), onDismiss)
                error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.testTag("lan-output-error")) }
            }
        }
    }
    if (management) melox.ui.desktop.MeloXLanDialog(true) { management = false }
    MeloXGlassDialog(pinTarget != null, { pinTarget = null; pin = "" }) {
        Text("输入配对 PIN", fontWeight = FontWeight.Bold)
        Text(pinTarget?.name.orEmpty())
        MeloXGlassTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, Modifier.testTag("lan-output-pin"))
        OutputAction("连接", "pin-connect", !session.busy && pin.length == 4, Modifier.fillMaxWidth()) { pinTarget?.let { connect(it, pin) } }
        error?.let { Text(it, color = Color.Red) }
    }
}

@Composable
private fun OutputTransport(symbol: MeloXSymbol, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).testTag("lan-output-$tag").clickable(enabled = enabled, role = Role.Button, onClick = onClick), Alignment.Center) {
        MeloXSymbolIcon(symbol, size = 26, color = Color.White.copy(alpha = if (enabled) 1f else .35f))
    }
}

@Composable
private fun OutputAction(text: String, tag: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    MeloXGlassButton(onClick, modifier.testTag("lan-output-$tag"), enabled = enabled) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun OutputDevice(title: String, subtitle: String, tag: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (active) Color.Black else Color.White
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .meloXLiquidButton(shape = RoundedCornerShape(22.dp), surfaceColor = if (active) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f))
        .testTag("lan-output-device:$tag").semantics { selected = active }.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        MeloXSymbolIcon(MeloXSymbol.Display, size = 24, color = if (active) Color(0xFF4786FF) else color)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = color.copy(alpha = .55f), fontSize = 10.sp)
        }
    }
}
