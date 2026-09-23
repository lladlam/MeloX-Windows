package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicSource
import melox.music.provider.MusicProviderSelectionStore
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import java.awt.Cursor

// ── Provider definitions ──

private data class ServiceProvider(
    val id: String,
    val name: String,
    val color: Color,
)

private fun ServiceProvider.musicSource(): MusicSource = when (id) {
    "netease" -> MusicSource.Netease
    "qq" -> MusicSource.QQMusic
    "kugou" -> MusicSource.Kugou
    "kuwo" -> MusicSource.Kuwo
    "bilibili" -> MusicSource.Bilibili
    "spotify" -> MusicSource.Spotify
    "youtubemusic" -> MusicSource.YouTubeMusic
    "applemusic" -> MusicSource.AppleMusic
    "jellyfin" -> MusicSource.Jellyfin
    "local" -> MusicSource.Local
    else -> MusicSource.fromStorageValue(id)
}

private val allProviders = listOf(
    ServiceProvider("netease", "网易云音乐", Color(0xFFFF2442)),
    ServiceProvider("qq", "QQ音乐", Color(0xFF12B7F5)),
    ServiceProvider("kugou", "酷狗音乐", Color(0xFF2CA2F9)),
    ServiceProvider("kuwo", "酷我音乐", Color(0xFFFF6600)),
    ServiceProvider("bilibili", "哔哩哔哩", Color(0xFFFB7299)),
    ServiceProvider("spotify", "Spotify", Color(0xFF1DB954)),
    ServiceProvider("youtubemusic", "YouTube Music", Color(0xFFFF0000)),
    ServiceProvider("applemusic", "Apple Music", Color(0xFFFC3C44)),
    ServiceProvider("jellyfin", "Jellyfin", Color(0xFF00A4DC)),
    ServiceProvider("local", "本地音乐", Color(0xFF888888)),
)

// ── State holder ──

@Stable
private class ServiceState {
    val enabledProviders = mutableStateMapOf(
        "netease" to true,
        "qq" to true,
        "kugou" to true,
        "kuwo" to true,
        "bilibili" to false,
        "spotify" to false,
        "youtubemusic" to false,
        "applemusic" to false,
        "jellyfin" to false,
        "local" to true,
    )
    val loggedInProviders = mutableStateMapOf<String, Boolean>()
    val usernames = mutableStateMapOf<String, String>()
    var aggregatedSearch by mutableStateOf(true)
    val searchScope = mutableStateMapOf(
        "netease" to true,
        "qq" to true,
        "kugou" to true,
        "kuwo" to true,
        "bilibili" to false,
        "spotify" to false,
        "youtubemusic" to false,
        "applemusic" to false,
        "jellyfin" to false,
        "local" to false,
    )

    fun isLoggedIn(id: String): Boolean = loggedInProviders[id] == true
    fun username(id: String): String = usernames[id] ?: ""
}

// ── Top bar ──

@Composable
private fun ServiceTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MeloXColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "音乐服务",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .hoverable(remember { MutableInteractionSource() })
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        ) {
            Text(
                text = "‹",
                color = MeloXColors.TextPrimary,
                fontSize = 22.sp,
            )
        }
    }
}

// ── Section header ──

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = MeloXColors.TextSecondary,
        fontFamily = MeloXLanTingProFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp),
    )
}

// ── Provider row ──

@Composable
private fun ProviderRow(
    provider: ServiceProvider,
    isEnabled: Boolean,
    isLoggedIn: Boolean,
    username: String,
    onToggle: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        shape = RoundedCornerShape(0.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(provider.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.name.first().toString(),
                    color = Color.White,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + status
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        color = MeloXColors.TextPrimary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isLoggedIn) MeloXColors.Success else MeloXColors.TextTertiary),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLoggedIn) {
                        if (username.isNotEmpty()) username else "已登录"
                    } else {
                        "未登录"
                    },
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Toggle
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MeloXColors.OnPrimary,
                    checkedTrackColor = provider.color,
                    uncheckedThumbColor = MeloXColors.TextSecondary,
                    uncheckedTrackColor = MeloXColors.SurfaceHigh,
                ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Login/Logout button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (isLoggedIn) onLogoutClick() else onLoginClick()
                    }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shape = RoundedCornerShape(16.dp),
                color = if (isLoggedIn) MeloXColors.SurfaceVariant else provider.color.copy(alpha = 0.12f),
            ) {
                Text(
                    text = if (isLoggedIn) "退出" else "登录",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = if (isLoggedIn) MeloXColors.TextSecondary else provider.color,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Search scope checkbox ──

@Composable
private fun SearchScopeItem(
    provider: ServiceProvider,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = provider.color,
                uncheckedColor = MeloXColors.TextTertiary,
            ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(provider.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = provider.name.first().toString(),
                color = provider.color,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = provider.name,
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 14.sp,
        )
    }
}

// ── Main ProviderServicesScreen composable ──

@Composable
fun ProviderServicesScreen(navState: MeloXNavState) {
    val state = remember { ServiceState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        ServiceTopBar(onBack = { navState.goBack() })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── Provider list section ──
            item {
                SectionLabel("音乐服务")
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    allProviders.forEachIndexed { index, provider ->
                        Column {
                            ProviderRow(
                                provider = provider,
                                isEnabled = state.enabledProviders[provider.id] == true,
                                isLoggedIn = state.isLoggedIn(provider.id),
                                username = state.username(provider.id),
                                onToggle = { state.enabledProviders[provider.id] = it },
                                onLoginClick = { /* Navigate to login */ },
                                onLogoutClick = {
                                    state.loggedInProviders[provider.id] = false
                                    state.usernames.remove(provider.id)
                                },
                            )
                            if (index < allProviders.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    color = MeloXColors.Outline.copy(alpha = 0.2f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }
            }

            // ── Aggregated search section ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("搜索设置")
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    // Aggregated search toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "聚合搜索",
                            color = MeloXColors.TextPrimary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.aggregatedSearch,
                            onCheckedChange = { state.aggregatedSearch = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeloXColors.OnPrimary,
                                checkedTrackColor = MeloXColors.Primary,
                                uncheckedThumbColor = MeloXColors.TextSecondary,
                                uncheckedTrackColor = MeloXColors.SurfaceHigh,
                            ),
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 20.dp),
                        color = MeloXColors.Outline.copy(alpha = 0.2f),
                        thickness = 0.5.dp,
                    )

                    // Search scope label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "搜索范围",
                            color = MeloXColors.TextSecondary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 13.sp,
                        )
                    }

                    // Search scope checkboxes
                    allProviders.forEach { provider ->
                        SearchScopeItem(
                            provider = provider,
                            checked = state.searchScope[provider.id] == true,
                            onCheckedChange = { state.searchScope[provider.id] = it },
                        )
                    }
                }
            }

            // ── Account switching section ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionLabel("账号管理")
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    val loggedInProviders = allProviders.filter { state.isLoggedIn(it.id) }
                    if (loggedInProviders.isEmpty()) {
                        Text(
                            text = "暂无已登录的账号",
                            color = MeloXColors.TextTertiary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    } else {
                        loggedInProviders.forEachIndexed { index, provider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(provider.color),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = provider.name.first().toString(),
                                        color = Color.White,
                                        fontFamily = MeloXLanTingProFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.name,
                                        color = MeloXColors.TextPrimary,
                                        fontFamily = MeloXLanTingProFontFamily,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = state.username(provider.id),
                                        color = MeloXColors.TextTertiary,
                                        fontFamily = MeloXLanTingProFontFamily,
                                        fontSize = 12.sp,
                                    )
                                }
                                Text(
                                    text = "切换 ›",
                                    color = MeloXColors.Primary,
                                    fontFamily = MeloXLanTingProFontFamily,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable {
                                        MusicProviderSelectionStore.setSelectedSource(provider.musicSource())
                                    }
                                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                )
                            }
                            if (index < loggedInProviders.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 62.dp),
                                    color = MeloXColors.Outline.copy(alpha = 0.2f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
