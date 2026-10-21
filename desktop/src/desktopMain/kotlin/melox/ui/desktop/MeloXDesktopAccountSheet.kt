package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.capsule.ContinuousRoundedRectangle
import melox.account.rememberNeteaseSessionStore
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.navigation.AppTab
import melox.ui.theme.MeloXDesktopDisplayFontFamily
import melox.ui.theme.MeloXDesktopTextFontFamily

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun MeloXDesktopAccountSheet(onDismiss: () -> Unit, onAccountSettings: () -> Unit,
    onMessages: () -> Unit, onLogin: () -> Unit, onSelect: (AppTab, String?) -> Unit) {
    val session = rememberNeteaseSessionStore()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val foreground = if (dark) Color(0xFFDDDDDD) else Color(0xFF202020)
    val surface = if (dark) Color(0xFF312E2E) else Color(0xFFF8F7F7)
    val accent = Color(0xFFFF3B45)
    var confirmLogout by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color.Transparent)) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = MeloXDesktopTextFontFamily, color = foreground)) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent {
            if (it.key == Key.Escape) { if (it.type == KeyEventType.KeyUp) onDismiss(); true } else false
        }.focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .10f))
                .testTag("desktop-account-scrim").clickable(interactionSource = null, indication = null, onClick = onDismiss))
            val width = minOf(500.dp, (maxWidth - 32.dp).coerceAtLeast(1.dp))
            val height = minOf(600.dp, (maxHeight - 32.dp).coerceAtLeast(1.dp))
            val shape = ContinuousRoundedRectangle(28.dp)
            Column(Modifier.size(width, height).clip(shape).background(surface)
                .border(.75.dp, if (dark) Color.White.copy(alpha = .26f) else Color.Black.copy(alpha = .14f), shape)
                .testTag("desktop-account-sheet").pointerInput(Unit) {
                    // Own the panel hit region without exposing a fictitious clickable action.
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                }) {
                Text(desktopString("ui.desktop.settings.account"), Modifier.padding(start = 28.dp, top = 28.dp, bottom = 20.dp)
                    .height(34.dp).wrapContentHeight(unbounded = true), fontFamily = MeloXDesktopDisplayFontFamily,
                    fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                if (session.isLoggedIn) {
                    Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccountGroup(foreground) {
                            AccountActionRow("ui.messages.private.title", "desktop-account-messages", foreground, onMessages)
                            if (MeloXSettingsRuntime.cloudMusicEnabled) {
                                AccountSeparator(foreground)
                                AccountActionRow("ui.navigation.cloud", "desktop-account-cloud", foreground) { onSelect(AppTab.Cloud, null) }
                            }
                            if (MeloXSettingsRuntime.downloadsEnabled) {
                                AccountSeparator(foreground)
                                AccountActionRow("ui.navigation.downloads", "desktop-account-downloads", foreground) { onSelect(AppTab.Downloads, null) }
                            }
                        }
                        AccountGroup(foreground) {
                            AccountActionRow("ui.desktop.account.settings", "desktop-account-settings", foreground, onAccountSettings)
                        }
                        AccountGroup(foreground) {
                            Row(Modifier.fillMaxWidth().height(42.dp).testTag("desktop-account-logout")
                                .clickable(interactionSource = null, indication = null, role = Role.Button) { confirmLogout = true }
                                .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(desktopString("ui.settings.account.logout"), Modifier.weight(1f), color = accent, fontSize = 13.sp)
                                Text(session.profile?.nickname.orEmpty(), color = accent.copy(alpha = .55f), fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                } else Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp).testTag("desktop-account-signed-out"),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    MeloXSymbolIcon(MeloXSymbol.Person, size = 44, color = foreground.copy(alpha = .45f))
                    Text(desktopString("ui.account.login_netease"), Modifier.padding(top = 12.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(desktopString("ui.desktop.account.login_message"), Modifier.padding(top = 8.dp, bottom = 16.dp), fontSize = 13.sp,
                        color = foreground.copy(alpha = .6f))
                    Box(Modifier.clip(CircleShape).background(accent).testTag("desktop-account-login")
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onLogin)
                        .padding(horizontal = 22.dp, vertical = 8.dp)) { Text(desktopString("ui.common.login"), color = Color.White, fontSize = 13.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.size(80.dp, 30.dp).clip(CircleShape).background(accent).testTag("desktop-account-done")
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Text(desktopString("ui.common.done"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false },
            title = { Text(desktopString("ui.settings.account.logout.confirmation")) }, confirmButton = {
                TextButton(onClick = { session.clear(); confirmLogout = false; onDismiss() }, modifier = Modifier.testTag("desktop-account-confirm-logout")) {
                    Text(desktopString("ui.settings.account.logout"), color = accent)
                }
            }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(desktopString("ui.common.cancel")) } })
        }
    }
}

@Composable
private fun AccountGroup(foreground: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(ContinuousRoundedRectangle(12.dp)).background(foreground.copy(alpha = .045f)), content = content)
}

@Composable
private fun AccountSeparator(foreground: Color) {
    Box(Modifier.fillMaxWidth().padding(start = 12.dp).height(.5.dp).background(foreground.copy(alpha = .14f)))
}

@Composable
private fun AccountActionRow(key: String, tag: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp).testTag(tag).clickable(interactionSource = null, indication = null, role = Role.Button,
        onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(desktopString(key), Modifier.weight(1f), color = color, fontSize = 13.sp)
        MeloXSymbolIcon(MeloXSymbol.ChevronRight, size = 11, weight = FontWeight.SemiBold, color = color.copy(alpha = .3f))
    }
}
