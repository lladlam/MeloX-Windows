package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.playback.CrossProviderPlaybackPreferences
import melox.ui.foundation.MeloXIosListRow
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassDialog
import melox.ui.glass.MeloXGlassToggle

@Composable
internal fun CrossProviderFallbackSetting() {
    var enabled by remember { mutableStateOf(CrossProviderPlaybackPreferences.enabled()) }
    var showNotice by remember { mutableStateOf(false) }
    MeloXIosListRow(title = "不可用资源从其他平台获取", showChevron = false, trailingContent = {
        MeloXGlassToggle(enabled, onCheckedChange = {
            if (it) showNotice = true else {
                CrossProviderPlaybackPreferences.setEnabled(false)
                enabled = false
            }
        }, modifier = Modifier.testTag("setting-cross_provider_unavailable_fallback"))
    })
    if (enabled) Text("仅当网易云明确未返回可播放音频时，才会严格匹配 QQ音乐、酷狗音乐或 Bilibili 的完整音源。",
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    MeloXGlassDialog(showNotice, onDismiss = { showNotice = false }, modifier = Modifier.testTag("cross-provider-notice")) {
        Text("启用跨平台资源匹配？", style = MaterialTheme.typography.titleMedium)
        Text("仅当网易云明确无法返回完整可播放音频时，MeloX 才会将当前曲目的标题、歌手和时长发送给 QQ音乐、酷狗音乐和 Bilibili 的搜索接口。",
            Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
        Text("只有标题与主艺人一致、且时长在严格范围内的完整音源才会播放。MeloX 不使用试听片段，也不会绕过付费、地区、版权、DRM 或账号权限限制。实际平台、歌曲版本和音质可能与原条目不同；收藏、歌单身份和歌词仍保留原网易云条目。",
            Modifier.padding(top = 8.dp), fontSize = 13.sp, lineHeight = 19.sp)
        Text("播放行为同时受实际音源平台的服务条款和隐私政策约束，你可以随时关闭此功能。",
            Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 18.sp)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(onClick = { showNotice = false }, modifier = Modifier.weight(1f).testTag("cross-provider-cancel"), style = MeloXGlassButtonStyle.Plain) { Text("取消") }
            MeloXGlassButton(onClick = {
                CrossProviderPlaybackPreferences.setEnabled(true)
                enabled = true
                showNotice = false
            }, modifier = Modifier.weight(1f).testTag("cross-provider-enable"), style = MeloXGlassButtonStyle.BorderedProminent) { Text("了解并启用") }
        }
    }
}
