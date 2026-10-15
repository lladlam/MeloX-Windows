package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import melox.remoteconfig.*
import melox.ui.legal.pinnedString
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RemoteConfigSettings() {
    val status by MeloXRemoteConfigRuntime.status.collectAsState()
    val enabled by MeloXRemoteConfigRuntime.enabled.collectAsState()
    val scope = rememberCoroutineScope()
    val routing = remember { MeloXGitHubRouting() }
    val source = pinnedString(when {
        !enabled -> "settings_remote_declined"
        status.source == MeloXRemoteConfigSource.VerifiedRemote -> "settings_remote_verified"
        status.source == MeloXRemoteConfigSource.VersionInapplicable -> "settings_remote_inapplicable"
        else -> "settings_remote_builtin"
    })
    Column(Modifier.fillMaxWidth().testTag("remote-status").semantics {
        stateDescription = "${status.source.name}>${status.config.configVersion}>$enabled"
    }) {
        SettingsGlassGroup {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(source, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(pinnedString("settings_remote_summary"), Modifier.padding(top = 7.dp), fontSize = 13.sp, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsGlassGroup {
            RemoteStatusLine(pinnedString("settings_remote_version"), status.config.configVersion.toString())
            RemoteStatusLine(pinnedString("settings_remote_access"), routing.effectiveRoute()?.takeIf { routing.selectedSource() == MeloXGitHubSource.Auto }?.let {
                pinnedString("settings_remote_route", settingsGithubSource(it.source), it.latencyMs)
            } ?: settingsGithubSource(routing.selectedSource()))
            RemoteStatusLine(pinnedString("settings_remote_key"), status.keyId ?: pinnedString("settings_remote_builtin_key"))
            RemoteStatusLine(pinnedString("settings_remote_last_check"), remoteTime(status.lastCheckedAtEpochMs))
            RemoteStatusLine(pinnedString("settings_remote_last_update"), remoteTime(status.lastUpdatedAtEpochMs))
            RemoteStatusLine(pinnedString("settings_remote_breakers"), status.config.disabledCapabilities.takeIf { it.isNotEmpty() }?.joinToString("、") ?: pinnedString("settings_remote_none"))
            RemoteStatusLine(pinnedString("settings_remote_fallback_order"), status.config.fallback.order.joinToString(" → "))
            RemoteStatusLine(pinnedString("settings_remote_timeout"), pinnedString("settings_remote_timeout_value", status.config.fallback.timeoutMs))
        }
        status.error?.let {
            Spacer(Modifier.height(12.dp))
            SettingsInfoCard(pinnedString("settings_remote_check_failed", it), Modifier.testTag("remote-error"))
        }
        Spacer(Modifier.height(12.dp))
        SettingsActionButton(pinnedString(when {
            !enabled -> "settings_remote_enable_first"; status.refreshing -> "settings_remote_checking"; else -> "settings_remote_check"
        }), Modifier.testTag("remote-refresh"), enabled = enabled && !status.refreshing) {
            scope.launch { MeloXRemoteConfigRuntime.refresh(force = true) }
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton(pinnedString("settings_remote_clear"), Modifier.testTag("remote-clear")) {
            scope.launch { MeloXRemoteConfigRuntime.clearCache() }
        }
    }
}

@Composable
private fun RemoteStatusLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        Text(value, Modifier.weight(1.4f), fontSize = 13.sp, textAlign = TextAlign.End)
    }
}

private fun remoteTime(value: Long): String = if (value <= 0L) pinnedString("settings_remote_not_yet") else
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
