package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicSource
import melox.music.provider.MusicProviderSelectionStore
import melox.music.provider.ProviderAccountManager
import melox.ui.navigation.MeloXBackHandler
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXDesktopTextFontFamily

private data class DesktopServiceProvider(val id: String, val name: String, val color: Color)

private val desktopServiceProviders = listOf(
    DesktopServiceProvider("netease", "NetEase Cloud Music", Color(0xFFFF2442)),
    DesktopServiceProvider("qq", "QQ Music", Color(0xFF12B7F5)),
    DesktopServiceProvider("kugou", "Kugou Music", Color(0xFF2CA2F9)),
    DesktopServiceProvider("kuwo", "Kuwo Music", Color(0xFFFF6600)),
    DesktopServiceProvider("bilibili", "Bilibili", Color(0xFFFB7299)),
    DesktopServiceProvider("spotify", "Spotify", Color(0xFF1DB954)),
    DesktopServiceProvider("youtubemusic", "YouTube Music", Color(0xFFFF0000)),
    DesktopServiceProvider("applemusic", "Apple Music", Color(0xFFFC3C44)),
    DesktopServiceProvider("jellyfin", "Jellyfin", Color(0xFF00A4DC)),
    DesktopServiceProvider("local", "Local Music", Color(0xFF888888)),
)

private fun DesktopServiceProvider.source(): MusicSource = when (id) {
    "netease" -> MusicSource.Netease
    "qq" -> MusicSource.QQMusic
    "kugou" -> MusicSource.Kugou
    "kuwo" -> MusicSource.Kuwo
    "bilibili" -> MusicSource.Bilibili
    "spotify" -> MusicSource.Spotify
    "youtubemusic" -> MusicSource.YouTubeMusic
    "applemusic" -> MusicSource.AppleMusic
    "jellyfin" -> MusicSource.Jellyfin
    else -> MusicSource.Local
}

/** Windows multi-provider management, styled as a compact Desktop form. */
@Composable
internal fun MeloXDesktopProviderServices(navState: MeloXNavState) {
    val accounts = remember { ProviderAccountManager() }
    var revision by remember { mutableIntStateOf(0) }
    var unified by remember { mutableStateOf(MusicProviderSelectionStore.unifiedEnabled()) }
    var scope by remember { mutableStateOf(MusicProviderSelectionStore.unifiedSources()) }
    val visible = remember(revision) {
        desktopServiceProviders.filter { it.source() in MusicProviderSelectionStore.visibleSources() }
    }
    MeloXBackHandler(true) { navState.goBack() }
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 34.dp, vertical = 28.dp).testTag("desktop-provider-services")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(desktopPlatformString("services_title"), Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                DesktopSettingsButton(desktopString("ui.common.done"), Modifier.testTag("desktop-services-done")) { navState.goBack() }
            }
            Spacer(Modifier.height(22.dp))
            DesktopSettingsForm {
                Text(desktopPlatformString("services_providers"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                visible.forEachIndexed { index, provider ->
                    val account = accounts.state(provider.source())
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(provider.color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                            Text(provider.name.take(1), color = provider.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(provider.name, fontSize = 13.sp)
                            Text(if (account.loggedIn) account.accountId.orEmpty().ifBlank { desktopPlatformString("services_signed_in") }
                                else desktopPlatformString("services_not_signed_in"),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DesktopSettingsCheckbox(desktopPlatformString("services_in_search"), provider.source() in scope,
                            "desktop-service-scope:${provider.id}", modifier = Modifier.widthIn(max = 150.dp)) {
                            scope = MusicProviderSelectionStore.setUnifiedSourceEnabled(provider.source(), it)
                        }
                        if (account.loggedIn) DesktopSettingsButton(desktopPlatformString("services_sign_out"),
                            Modifier.testTag("desktop-service-logout:${provider.id}")) {
                            accounts.logout(provider.source()); revision++
                        } else DesktopSettingsButton(desktopString("ui.common.login"),
                            Modifier.testTag("desktop-service-login:${provider.id}")) {
                            melox.ui.navigation.MeloXLoginRequestBus.request(provider.id)
                            navState.navigateTo(Route.Login)
                        }
                    }
                    if (index < visible.lastIndex) HorizontalDivider(Modifier.padding(start = 31.dp), thickness = .5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                }
            }
            Spacer(Modifier.height(22.dp))
            DesktopSettingsForm {
                Text(desktopPlatformString("services_search"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                DesktopSettingsCheckbox(desktopPlatformString("services_unified"), unified, "desktop-service-unified") {
                    unified = it
                    MusicProviderSelectionStore.setUnifiedEnabled(it)
                    scope = MusicProviderSelectionStore.unifiedSources()
                }
                Text(desktopPlatformString("services_scope_hint"), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
