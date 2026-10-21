package melox.ui.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import melox.account.rememberNeteaseSessionStore
import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXThemeMode
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopTextFontFamily

/** DesktopOnboardingDialog's three pages; phone onboarding retains its own state/UI. */
@Composable
internal fun MeloXDesktopOnboarding(onFinish: (login: Boolean) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val session = rememberNeteaseSessionStore()
    var quality by remember { mutableStateOf(MusicQualityPreferences.read()) }
    var duration by remember { mutableIntStateOf(MeloXSettingsPreferences.string("recognition_duration", "6").toIntOrNull() ?: 6) }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(Modifier.size((760.dp).coerceAtMost(maxWidth), (650.dp).coerceAtMost(maxHeight))
                .testTag("desktop-onboarding"), shape = RoundedCornerShape(10.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 30.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(desktopString(when (page) { 0 -> "ui.desktop.onboarding.welcome"; 1 -> "ui.desktop.onboarding.configure"; else -> "ui.settings.account.netease_account" }), Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(if (it == page) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = .22f))) } }
                    }
                    HorizontalDivider(thickness = .5.dp)
                    Crossfade(page, Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 220)) { selected ->
                        when (selected) {
                            0 -> Column(Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                                Spacer(Modifier.weight(1f))
                                Image(painterResource("parity/desktop-logo.png"), null, Modifier.size(132.dp).clip(RoundedCornerShape(28.dp)))
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("MeloX", fontSize = 38.sp, fontWeight = FontWeight.Bold)
                                    Text(desktopString("ui.desktop.onboarding.subtitle"), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                                    listOf("library" to MeloXSymbol.Library, "lyrics" to MeloXSymbol.Quote, "downloads_cloud" to MeloXSymbol.ICloud).forEach { (name, symbol) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { MeloXSymbolIcon(symbol, size = 24, color = Color.Red); Text(desktopString("ui.desktop.onboarding.feature.$name")) }
                                    }
                                }
                                Text(desktopString("ui.legal.unofficial_disclaimer"), Modifier.widthIn(max = 520.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                            }
                            1 -> Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                DesktopSettingsForm {
                                    DesktopSettingsPicker(desktopString("ui.desktop.settings.appearance"), MeloXSettingsRuntime.themeMode, MeloXThemeMode.entries.map { it to desktopString("ui.settings.appearance.option.${it.name.lowercase()}") }, "desktop-onboarding-theme") { MeloXSettingsPreferences.setString("theme_mode", it.name) }
                                }
                                DesktopSettingsForm {
                                    Text(desktopString("ui.settings.playback.section.playback"), fontWeight = FontWeight.SemiBold)
                                    DesktopSettingsPicker(desktopString("ui.player.playback_quality"), quality, MusicQuality.entries.map { it to desktopQualityTitle(it) }, "desktop-onboarding-quality") { quality = it; PlaybackCommands.changeQuality(it) }
                                    DesktopSettingsCheckbox(desktopString("ui.settings.player_appearance.shrink_paused_artwork"), MeloXSettingsRuntime.artworkMotionEnabled, "desktop-onboarding-artwork") { MeloXSettingsPreferences.setBoolean("player_artwork_motion", it) }
                                }
                                DesktopSettingsForm {
                                    Text(desktopString("ui.recognition.title"), fontWeight = FontWeight.SemiBold)
                                    DesktopSettingsPicker(desktopString("ui.desktop.onboarding.recognition.default_duration"), duration, listOf(3, 6, 9, 0).map { it to if (it == 0) desktopString("ui.settings.recognition.duration.continuous") else desktopFormat("ui.common.seconds", it) }, "desktop-onboarding-duration") { duration = it; MeloXSettingsPreferences.setString("recognition_duration", it.toString()) }
                                    Text(desktopString("ui.desktop.onboarding.recognition.privacy"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            else -> Column(Modifier.fillMaxSize().padding(horizontal = 42.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                                Spacer(Modifier.weight(1f))
                                val profile = session.profile
                                if (profile != null) {
                                    MeloXArtworkImage(profile.avatarUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(112.dp).clip(CircleShape))
                                    Text(profile.nickname, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text(desktopString("ui.account.logged_in"), color = Color(0xFF228833))
                                } else {
                                    MeloXSymbolIcon(MeloXSymbol.Person, size = 82, color = Color.Red)
                                    Text(desktopString("ui.onboarding.account.connect"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text(desktopString("ui.desktop.onboarding.account.message"), Modifier.widthIn(max = 500.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
                                    Text(desktopString("ui.desktop.onboarding.benefit.direct_api"))
                                    Text(desktopString("ui.onboarding.benefit.local_cookie"))
                                    Text(desktopString("ui.desktop.onboarding.benefit.independent_interfaces"))
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    HorizontalDivider(thickness = .5.dp)
                    Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (page > 0) DesktopDialogButton("ui.common.back", tag = "desktop-onboarding-back") { page-- }
                        Spacer(Modifier.weight(1f))
                        if (page < 2) DesktopDialogButton("ui.common.continue", tag = "desktop-onboarding-continue", prominent = true) { page++ }
                        else if (session.isLoggedIn) DesktopDialogButton("ui.onboarding.start", prominent = true) { onFinish(false) }
                        else {
                            DesktopDialogButton("ui.desktop.onboarding.sign_in_later", tag = "desktop-onboarding-later") { onFinish(false) }
                            DesktopDialogButton("ui.account.login_netease", prominent = true) { onFinish(true) }
                        }
                    }
                }
            }
            }
        }
    }
}
