package melox.ui.legal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.*
import java.util.Locale
import java.util.Properties

private object PinnedSettingsStrings {
    val values = Properties().apply {
        checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-settings-strings.properties"))
            .reader(Charsets.UTF_8).use(::load)
        checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-lyrics-strings.properties"))
            .reader(Charsets.UTF_8).use(::load)
        checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-skyline-strings.properties"))
            .reader(Charsets.UTF_8).use(::load)
        checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-settings-pages.properties"))
            .reader(Charsets.UTF_8).use(::load)
        checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-v065-lyric-language.properties"))
            .reader(Charsets.UTF_8).use(::load)
        checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-v065-split-layout.properties"))
            .reader(Charsets.UTF_8).use(::load)
    }
    private val localized = java.util.concurrent.ConcurrentHashMap<String, Properties>()
    fun forLocale(locale: Locale): Properties {
        val suffix = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-rTW" else "zh-rCN"
            "ja" -> "ja"
            else -> return values
        }
        return localized.getOrPut(suffix) {
            Properties(values).apply {
                checkNotNull(PinnedSettingsStrings::class.java.classLoader.getResourceAsStream("parity/pinned-settings-pages-$suffix.properties"))
                    .reader(Charsets.UTF_8).use(::load)
            }
        }
    }
}

internal fun pinnedString(key: String, vararg args: Any): String {
    val locale = Locale.getDefault()
    val value = checkNotNull(PinnedSettingsStrings.forLocale(locale).getProperty(key)) { "Missing pinned Android string: $key" }
    return if (args.isEmpty()) value else String.format(locale, value, *args)
}

internal enum class MeloXLegalDocument(val titleKey: String, val asset: String, val tag: String) {
    PrivacyPolicy("legal_privacy", "privacy-policy-zh-CN.md", "privacy"),
    Disclaimer("settings_legal_disclaimer", "disclaimer-zh-CN.md", "disclaimer"),
    CloudControlPrivacy("legal_cloud", "cloud-control-privacy-zh-CN.md", "cloud"),
    ThirdPartyMusicSources("settings_legal_third_party", "third-party-music-sources-zh-CN.md", "third-party"),
}

private enum class LegalBlockKind { Heading, Subheading, Paragraph, Bullet }
private data class LegalBlock(val kind: LegalBlockKind, val text: String)

@Composable
internal fun MeloXLegalDocumentDialog(document: MeloXLegalDocument, onDismiss: () -> Unit) {
    val blocks = remember(document) {
        checkNotNull(MeloXLegalDocument::class.java.classLoader.getResourceAsStream("legal/${document.asset}"))
            .bufferedReader().use { it.readText() }.lineSequence().map(String::trim).filter(String::isNotEmpty).mapNotNull { line ->
                when {
                    line.startsWith("# ") -> LegalBlock(LegalBlockKind.Heading, line.removePrefix("# "))
                    line.startsWith("## ") -> LegalBlock(LegalBlockKind.Subheading, line.removePrefix("## "))
                    line.startsWith("### ") -> LegalBlock(LegalBlockKind.Subheading, line.removePrefix("### "))
                    line.startsWith("- ") -> LegalBlock(LegalBlockKind.Bullet, line.removePrefix("- "))
                    line == "---" -> null
                    else -> LegalBlock(LegalBlockKind.Paragraph, line)
                }
            }.toList()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("legal-document").semantics { stateDescription = document.tag },
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).testTag("legal-document-close").clickable(role = Role.Button, onClick = onDismiss),
                        contentAlignment = Alignment.Center) {
                        MeloXSymbolIcon(MeloXSymbol.XMark, color = MaterialTheme.colorScheme.onSurface, size = 16, iconSize = 15)
                    }
                    Text(pinnedString(document.titleKey), Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(36.dp))
                }
                LazyColumn(Modifier.fillMaxSize().testTag("legal-document-content"),
                    contentPadding = PaddingValues(start = 22.dp, top = 8.dp, end = 22.dp, bottom = 32.dp)) {
                    itemsIndexed(blocks) { index, block ->
                        when (block.kind) {
                            LegalBlockKind.Heading -> Text(block.text, Modifier.padding(top = if (index == 0) 0.dp else 24.dp, bottom = 4.dp),
                                color = MaterialTheme.colorScheme.onBackground, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                            LegalBlockKind.Subheading -> Text(block.text, Modifier.padding(top = 20.dp, bottom = 2.dp),
                                color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
                            LegalBlockKind.Paragraph -> Text(block.text, Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f), fontSize = 14.sp, lineHeight = 21.sp)
                            LegalBlockKind.Bullet -> Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.Top) {
                                Text("•", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f), fontSize = 14.sp, lineHeight = 21.sp)
                                Text(block.text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f), fontSize = 14.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MeloXLegalLinks(modifier: Modifier = Modifier, tint: Color = MeloXSystemColors.Blue) {
    var document by remember { mutableStateOf<MeloXLegalDocument?>(null) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LegalLink(pinnedString("legal_privacy"), tint, "onboarding-privacy") { document = MeloXLegalDocument.PrivacyPolicy }
            Text(pinnedString("legal_and"), Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            LegalLink(pinnedString("legal_disclaimer"), tint, "onboarding-disclaimer") { document = MeloXLegalDocument.Disclaimer }
        }
        LegalLink(pinnedString("legal_cloud"), tint, "onboarding-cloud-policy") { document = MeloXLegalDocument.CloudControlPrivacy }
    }
    document?.let { MeloXLegalDocumentDialog(it) { document = null } }
}

@Composable
private fun LegalLink(text: String, tint: Color, tag: String, onClick: () -> Unit) {
    Text(text, Modifier.clip(MaterialTheme.shapes.small).testTag(tag).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 5.dp), color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun MeloXFirstLaunchLegalConsent(onAgree: () -> Unit, onDecline: () -> Unit, onOpenProject: () -> Unit) {
    MeloXGlassDialog(true, onDismiss = {}, modifier = Modifier.testTag("onboarding-legal")) {
        Text(pinnedString("legal_welcome"), style = MaterialTheme.typography.titleLarge)
        Text(pinnedString("legal_welcome_body"), Modifier.padding(top = 9.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f), fontSize = 14.sp, lineHeight = 20.sp)
        MeloXLegalLinks(Modifier.padding(top = 10.dp))
        Text(pinnedString("legal_agree_note"), Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f), fontSize = 12.sp, lineHeight = 17.sp)
        Text(pinnedString("legal_project"), Modifier.align(Alignment.CenterHorizontally).clip(MaterialTheme.shapes.small)
            .testTag("onboarding-project").clickable(role = Role.Button, onClick = onOpenProject).padding(horizontal = 8.dp, vertical = 8.dp),
            color = MeloXSystemColors.Blue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(onDecline, Modifier.weight(1f).testTag("onboarding-decline"), style = MeloXGlassButtonStyle.Plain) { Text(pinnedString("legal_decline_exit")) }
            MeloXGlassButton(onAgree, Modifier.weight(1f).testTag("onboarding-agree"), style = MeloXGlassButtonStyle.BorderedProminent) { Text(pinnedString("legal_agree_continue")) }
        }
    }
}

internal const val MELOX_CLOUD_CONTROL_MESSAGE =
    "我希望你能了解最新的功能：云控。关于云控，你可以在“云控隐私协议”里面查看详细内容。云控只用于控制音乐源及其下属兼容功能，不用于其他目的。启用后，MeloX 会在每次应用进入前台时检查一次签名配置，并在应用保持前台运行期间每两小时检查一次。你可以选择拒绝或确定；确定代表你同意云控隐私协议，拒绝不会影响未依赖云控的功能，之后也可以随时在设置中修改选择。"

@Composable
internal fun MeloXConnectServiceDialog(onLater: () -> Unit, onLogin: () -> Unit) {
    MeloXGlassDialog(true, onDismiss = onLater, modifier = Modifier.testTag("onboarding-connect")) {
        Text(pinnedString("app_connect_title"), style = MaterialTheme.typography.titleLarge)
        Text(pinnedString("app_connect_body"), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(onLater, Modifier.weight(1f).testTag("onboarding-later"), style = MeloXGlassButtonStyle.Plain) { Text(pinnedString("app_later")) }
            MeloXGlassButton(onLogin, Modifier.weight(1f).testTag("onboarding-login"), style = MeloXGlassButtonStyle.BorderedProminent) { Text(pinnedString("app_login_netease")) }
        }
    }
}

@Composable
internal fun MeloXCloudControlConsentDialog(onReject: () -> Unit, onAccept: () -> Unit) {
    var showPolicy by remember { mutableStateOf(false) }
    MeloXGlassDialog(true, onDismiss = {}, modifier = Modifier.testTag("remote-consent")) {
        Text("欢迎使用MeloX！", style = MaterialTheme.typography.titleLarge)
        Text(MELOX_CLOUD_CONTROL_MESSAGE, Modifier.padding(top = 9.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f), fontSize = 14.sp, lineHeight = 21.sp)
        Text("查看云控隐私协议", Modifier.padding(top = 8.dp).clip(MaterialTheme.shapes.small)
            .testTag("remote-consent-policy").clickable(role = Role.Button) { showPolicy = true }.padding(horizontal = 6.dp, vertical = 7.dp),
            color = MeloXSystemColors.Blue, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(onReject, Modifier.weight(1f).testTag("remote-reject"), style = MeloXGlassButtonStyle.Plain) { Text("拒绝") }
            MeloXGlassButton(onAccept, Modifier.weight(1f).testTag("remote-accept"), style = MeloXGlassButtonStyle.BorderedProminent) { Text("确定") }
        }
    }
    if (showPolicy) MeloXLegalDocumentDialog(MeloXLegalDocument.CloudControlPrivacy) { showPolicy = false }
}
