package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import melox.remoteconfig.*
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXIosListRow
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassDialog
import melox.ui.glass.MeloXGlassToggle
import melox.ui.glass.MeloXSettingsDropdown
import java.text.DateFormat
import java.util.Date
import melox.ui.legal.MeloXCloudControlConsentDialog
import melox.ui.legal.MeloXLegalDocumentDialog
import melox.ui.legal.MeloXLegalDocument

@Composable
internal fun RemoteConfigSettings() {
    val status by MeloXRemoteConfigRuntime.status.collectAsState()
    val enabled by MeloXRemoteConfigRuntime.enabled.collectAsState()
    val scope = rememberCoroutineScope()
    val routing = remember { MeloXGitHubRouting() }
    var route by remember { mutableStateOf(routing.selectedSource()) }
    var consentVisible by remember { mutableStateOf(false) }
    var policyVisible by remember { mutableStateOf(false) }
    val source = when {
        !enabled -> "云控已拒绝或关闭"
        status.source == MeloXRemoteConfigSource.VerifiedRemote -> "已验证远程配置"
        status.source == MeloXRemoteConfigSource.VersionInapplicable -> "远程配置不适用于当前版本"
        else -> "内置安全默认值"
    }
    Column(Modifier.fillMaxWidth().testTag("remote-status").semantics {
        stateDescription = "${status.source.name}>${status.config.configVersion}>$enabled"
    }) {
    MeloXIosGroupedList(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
        Text(source, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("签名配置仅控制已披露的音乐源登录、播放和跨平台回退能力。本地选择始终优先；授权后每次应用进入前台检查，并在前台持续运行期间每两小时检查一次。",
            Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp), fontSize = 13.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    MeloXIosGroupedList(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
        RemoteStatusLine("配置版本", status.config.configVersion.toString())
        RemoteStatusLine("兼容性基线", "Android 3f877ea / code ${MeloXRemoteConfigRuntime.PolicyCompatibilityVersionCode}")
        RemoteStatusLine("访问源", routing.effectiveRoute()?.takeIf { route == MeloXGitHubSource.Auto }?.let { "${it.source.label}（${it.latencyMs} ms）" } ?: route.label)
        RemoteStatusLine("签名密钥", status.keyId ?: "内置")
        RemoteStatusLine("最近检查", remoteTime(status.lastCheckedAtEpochMs))
        RemoteStatusLine("最近更新", remoteTime(status.lastUpdatedAtEpochMs))
        RemoteStatusLine("声明熔断", status.config.disabledCapabilities.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无")
        RemoteStatusLine("回退顺序", status.config.fallback.order.joinToString(" → "))
        RemoteStatusLine("回退超时", "${status.config.fallback.timeoutMs} ms")
    }
    status.error?.let { Text("最近检查失败：$it\n已继续使用上一次有效配置或内置默认值。",
        Modifier.padding(horizontal = 36.dp, vertical = 12.dp).testTag("remote-error"), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    MeloXSettingsDropdown("GitHub 访问源", route, MeloXGitHubSource.entries.map { it to it.label },
        onSelected = { route = it; routing.selectSource(it); if (enabled) scope.launch { MeloXRemoteConfigRuntime.refresh(force = true) } },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
    MeloXIosGroupedList(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
        MeloXIosListRow(title = "云控隐私协议与授权", showChevron = false, trailingContent = {
            MeloXGlassToggle(enabled, onCheckedChange = { choice ->
                if (choice) consentVisible = true else scope.launch { MeloXRemoteConfigRuntime.setEnabled(false) }
            }, modifier = Modifier.testTag("remote-enabled"))
        })
        MeloXIosListRow(title = "查看云控隐私协议", onClick = { policyVisible = true }, showTopSeparator = true,
            modifier = Modifier.testTag("remote-open-policy"))
    }
    MeloXGlassButton(onClick = { scope.launch { MeloXRemoteConfigRuntime.refresh(force = true) } },
        enabled = enabled && !status.refreshing, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).testTag("remote-refresh")) {
        Text(if (!enabled) "请先在隐私协议中启用云控" else if (status.refreshing) "正在检查…" else "手动检查配置")
    }
    MeloXGlassButton(onClick = { scope.launch { MeloXRemoteConfigRuntime.clearCache() } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).testTag("remote-clear")) {
        Text("清除缓存并恢复内置配置")
    }
    if (consentVisible) MeloXCloudControlConsentDialog(
        onReject = { consentVisible = false; scope.launch { MeloXRemoteConfigRuntime.setEnabled(false) } },
        onAccept = { scope.launch { MeloXRemoteConfigRuntime.setEnabled(true); consentVisible = false } },
    )
    if (policyVisible) MeloXLegalDocumentDialog(MeloXLegalDocument.CloudControlPrivacy) { policyVisible = false }
    }
}

@Composable
private fun RemoteStatusLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        Text(value, Modifier.weight(1.4f), fontSize = 13.sp)
    }
}

private fun remoteTime(value: Long): String = if (value <= 0L) "尚未" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
