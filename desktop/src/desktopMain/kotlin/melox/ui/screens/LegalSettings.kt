package melox.ui.screens

import androidx.compose.foundation.layout.*
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
import melox.legal.MELOX_LEGAL_VERSION
import melox.remoteconfig.MeloXRemoteConfigRuntime
import melox.settings.MeloXSettingsPreferences
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.MeloXGlassToggle
import melox.ui.glass.MeloXSystemColors
import melox.ui.legal.*

@Composable
internal fun LegalSettings() {
    var document by remember { mutableStateOf<MeloXLegalDocument?>(null) }
    var consentVisible by remember { mutableStateOf(false) }
    val enabled by MeloXRemoteConfigRuntime.enabled.collectAsState()
    val scope = rememberCoroutineScope()
    val consentVersion = remember { MeloXSettingsPreferences.string("legal_consent_version") }
    Column {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).testTag("legal-version").semantics { stateDescription = consentVersion }) {
                Text(pinnedString("settings_legal_documents"), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(pinnedString("settings_legal_version", MELOX_LEGAL_VERSION), Modifier.padding(top = 7.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp, lineHeight = 19.sp)
                Text(if (consentVersion.isBlank()) pinnedString("settings_legal_no_consent") else pinnedString("settings_legal_consented", consentVersion),
                    Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXLegalDocument.entries.forEachIndexed { index, item ->
                MeloXIosListRow(title = pinnedString(item.titleKey), modifier = Modifier.testTag("legal-open-${item.tag}"),
                    leading = { MeloXSymbolIcon(if (item == MeloXLegalDocument.Disclaimer || item == MeloXLegalDocument.ThirdPartyMusicSources) MeloXSymbol.Book else MeloXSymbol.Info,
                        color = MeloXSystemColors.Blue, size = 24) }, showTopSeparator = index > 0, onClick = { document = item })
            }
        }
        Spacer(Modifier.height(10.dp))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(title = pinnedString("settings_legal_remote_toggle"),
                showChevron = false, trailingContent = {
                    MeloXGlassToggle(enabled, onCheckedChange = { requested ->
                        if (requested) consentVisible = true else scope.launch { MeloXRemoteConfigRuntime.setEnabled(false) }
                    }, modifier = Modifier.testTag("legal-remote-enabled"))
                })
        }
        Text(pinnedString("settings_legal_remote_footer"), Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
    document?.let { MeloXLegalDocumentDialog(it) { document = null } }
    if (consentVisible) MeloXCloudControlConsentDialog(
        onReject = { consentVisible = false },
        onAccept = { scope.launch { MeloXRemoteConfigRuntime.setEnabled(true); consentVisible = false } },
    )
}
