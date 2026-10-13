package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import melox.recommendation.LocalRecommendationStore
import melox.recommendation.LocalRecommendationEngine
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXIosListRow
import melox.ui.glass.MeloXGlassDialog
import melox.ui.glass.MeloXGlassToggle
import melox.ui.glass.meloXLiquidButton
import melox.ui.legal.pinnedString

@Composable
internal fun SettingsActionButton(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().height(50.dp).meloXLiquidButton(
        shape = RoundedCornerShape(25.dp), enabled = enabled, surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f),
    ).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Text(title, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun SettingsDangerButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().height(54.dp).meloXLiquidButton(
        shape = RoundedCornerShape(27.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = .25f),
        surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = .20f),
    ).clickable(onClick = onClick).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
        Text(title, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PrivacySettings() {
    var algorithmEnabled by remember { mutableStateOf(LocalRecommendationStore.isAlgorithmEnabled()) }
    var consent by remember { mutableStateOf(LocalRecommendationStore.hasPersonalizationConsent()) }
    var showPolicy by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(10) }
    var clearing by remember { mutableStateOf(false) }
    val progress by LocalRecommendationStore.progress
    val scope = rememberCoroutineScope()
    val revoke: () -> Unit = {
        consent = false
        LocalRecommendationStore.setPersonalizationConsent(false)
        clearing = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                LocalRecommendationEngine.revokePersonalizationAndClear()
                showClear = false
            } finally { clearing = false }
        }
    }
    LaunchedEffect(showPolicy) {
        if (showPolicy) {
            secondsLeft = 10
            while (secondsLeft > 0) { delay(1_000); secondsLeft-- }
        }
    }
    Column(Modifier.testTag("privacy-content").semantics { stateDescription = if (consent) "consented" else "not-consented" }) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(pinnedString("settings_privacy_data_title"), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(pinnedString("settings_privacy_data_body"), Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(title = pinnedString("settings_privacy_algorithm"), showChevron = false,
                trailingContent = { MeloXGlassToggle(algorithmEnabled, onCheckedChange = {
                    algorithmEnabled = it
                    LocalRecommendationStore.setAlgorithmEnabled(it)
                    if (it && consent) LocalRecommendationEngine.start() else if (!it) LocalRecommendationEngine.stop()
                }, modifier = Modifier.testTag("privacy-algorithm")) })
        }
        Spacer(Modifier.height(10.dp))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            Text(pinnedString("settings_privacy_personalization"), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(pinnedString(if (consent) "settings_privacy_consented" else "settings_privacy_not_consented"),
                Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            SettingsActionButton(pinnedString(if (consent) "settings_privacy_withdraw" else "settings_privacy_read_policy"), Modifier.testTag("privacy-consent-action")) {
                if (!clearing) { if (consent) revoke() else { secondsLeft = 10; showPolicy = true } }
            }
            SettingsActionButton(pinnedString("settings_privacy_clear_all"), Modifier.testTag("privacy-clear")) { if (!clearing) showClear = true }
        }
        Spacer(Modifier.height(10.dp))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(pinnedString("settings_privacy_status"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (progress.isFullAnalysis) pinnedString("settings_privacy_full_analysis", progress.stage, progress.processed, progress.total)
                    else pinnedString("settings_privacy_quick_update", progress.stage), Modifier.padding(top = 8.dp),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                Text(pinnedString("settings_privacy_backend", progress.modelBackend), Modifier.padding(top = 4.dp),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                progress.message?.let { Text(it, Modifier.padding(top = 5.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f)) }
            }
        }
        SettingsActionButton(pinnedString("settings_privacy_start_full"), Modifier.testTag("privacy-start-analysis"),
            enabled = !clearing && algorithmEnabled && consent) {
            if (!clearing && algorithmEnabled && consent) LocalRecommendationEngine.startFullAnalysis()
        }
    }
    if (showPolicy) MeloXGlassDialog(true, onDismiss = { if (secondsLeft == 0) showPolicy = false }, modifier = Modifier.testTag("privacy-policy")) {
        Text(pinnedString("settings_privacy_policy_title"), style = MaterialTheme.typography.titleMedium)
        Text(pinnedString("settings_privacy_policy_body"), Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        SettingsActionButton(if (secondsLeft > 0) pinnedString("settings_privacy_wait", secondsLeft) else pinnedString("legal_agree_enable"),
            Modifier.testTag("privacy-policy-agree").semantics { stateDescription = if (secondsLeft == 0) "ready" else "waiting" }) {
            if (secondsLeft == 0 && !clearing) {
                LocalRecommendationStore.setPersonalizationConsent(true)
                LocalRecommendationStore.setConsentAt(System.currentTimeMillis())
                consent = true
                showPolicy = false
                if (algorithmEnabled) LocalRecommendationEngine.start()
            }
        }
    }
    if (showClear) MeloXGlassDialog(true, onDismiss = { if (!clearing) showClear = false }, modifier = Modifier.testTag("privacy-clear-dialog")) {
        Text(pinnedString("settings_privacy_clear_title"), style = MaterialTheme.typography.titleMedium)
        Text(pinnedString("settings_privacy_clear_body"), Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 19.sp)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton(pinnedString("action_cancel"), Modifier.weight(1f).testTag("privacy-clear-cancel")) { if (!clearing) showClear = false }
            SettingsDangerButton(pinnedString("settings_privacy_clear_confirm"), Modifier.weight(1f).testTag("privacy-clear-confirm")) { if (!clearing) revoke() }
        }
    }
}
