package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import melox.account.rememberNeteaseSessionStore
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.navigation.AppTab
import melox.ui.screens.MeloXArtworkImage

@Composable
internal fun MeloXDesktopAccountSheet(
    onDismiss: () -> Unit,
    onAccountSettings: () -> Unit,
    onMessages: () -> Unit,
    onLogin: () -> Unit,
    onSelect: (AppTab, String?) -> Unit,
) {
    val session = rememberNeteaseSessionStore()
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .48f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(.72f).widthIn(min = 420.dp, max = 500.dp).heightIn(min = 520.dp, max = 600.dp)
                .clip(RoundedCornerShape(28.dp)).background(colors.surface.copy(alpha = .97f))
                .border(1.dp, colors.outlineVariant.copy(alpha = .5f), RoundedCornerShape(28.dp))
                .clickable {}.padding(24.dp).testTag("desktop-account-sheet")) {
                Text(desktopPlatformString("account_title"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.onSurface.copy(alpha = .055f))
                    .padding(horizontal = 14.dp)) {
                    AccountActionRow("platform_account_private_messages", MeloXSymbol.Messages, onMessages)
                    AccountActionRow("ui.navigation.cloud", MeloXSymbol.ICloud, onClick = { onSelect(AppTab.Cloud, null) })
                    AccountActionRow("ui.navigation.downloads", MeloXSymbol.Download, onClick = { onSelect(AppTab.Downloads, null) })
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.onSurface.copy(alpha = .055f))
                    .padding(horizontal = 14.dp)) {
                    AccountActionRow("platform_account_settings", MeloXSymbol.Settings, onAccountSettings, showDivider = false)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(colors.onSurface.copy(alpha = .055f)).clickable {
                        if (session.isLoggedIn) session.clear() else onLogin()
                    }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopPlatformString(if (session.isLoggedIn) "account_logout" else "account_login"),
                        Modifier.weight(1f), color = Color(0xFFFF454F), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (session.isLoggedIn) {
                        val profile = session.profile
                        if (profile?.avatarUrl != null) MeloXArtworkImage(profile.avatarUrl, colors.primary,
                            Modifier.size(32.dp).clip(CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(profile?.nickname ?: desktopString("ui.desktop.sidebar.not_signed_in"),
                            color = Color(0xFFFF454F).copy(alpha = .78f), fontSize = 14.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.width(110.dp).height(42.dp).clip(CircleShape).background(Color(0xFFFF3B45))
                        .clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Text(desktopPlatformString("account_done"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountActionRow(key: String, icon: MeloXSymbol, onClick: () -> Unit, showDivider: Boolean = true) {
    val color = MaterialTheme.colorScheme.onSurface
    Column {
        Row(Modifier.fillMaxWidth().height(54.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Text(if (key.startsWith("platform_")) desktopPlatformString(key.removePrefix("platform_")) else desktopString(key),
                Modifier.weight(1f), color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            MeloXSymbolIcon(icon, size = 14, color = color.copy(alpha = .48f), nativeGlyph = true)
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(.5.dp).background(color.copy(alpha = .14f)))
    }
}
