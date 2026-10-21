package melox.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import melox.account.NeteaseSessionStore
import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.download.MeloXDownloadStore
import melox.music.model.MusicSource
import melox.network.MeloXHttpClient
import melox.platform.meloXCacheDir
import melox.platform.meloXDataDir
import melox.playback.MeloXMediaCache
import melox.playback.PlaybackCommands
import melox.settings.MeloXPlayerBackgroundMode
import melox.settings.MeloXPlayerShell
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXThemeMode
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.meloXPageEnter
import melox.ui.foundation.meloXPageExit
import melox.ui.navigation.MeloXBackHandler
import melox.ui.glass.MeloXGlassSegmentedControl
import melox.ui.glass.MeloXGlassTextField
import melox.ui.glass.MeloXSettingsDropdown
import melox.ui.glass.LocalSettingsGroupRowIndex
import melox.ui.theme.MeloXColors
import melox.ui.legal.pinnedString
import melox.account.rememberNeteaseSessionStore
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator

private enum class SettingsRoute(val resourceKey: String, val symbol: String) {
    General("general", "⚙"),
    Appearance("appearance", "✦"),
    Content("content", "▦"),
    TabLayout("tabs", "▥"),
    Playback("playback", "♫"),
    Lyrics("lyrics", "❞"),
    SkylineLyrics("skyline", "▱"),
    SystemPlayback("system_playback", "▣"),
    FloatingLyrics("floating", "▤"),
    Recognition("recognition", "⌁"),
    ListenTogether("listen_together", "◎"),
    Storage("storage", "▰"),
    Features("features", "☷"),
    Messages("messages", "✉"),
    RemoteConfig("remote_config", "⌁"),
    About("about", "ⓘ"),
    Legal("legal", "▤"),
    Privacy("privacy", "⌘"),
    Developer("developer", "⌘"),
    Experimental("experimental", "✦"),
    ;
    val title: String get() = pinnedString("settings_route_$resourceKey")
    val subtitle: String get() = pinnedString(if (this == Privacy) "settings_privacy_personalization" else "settings_sub_$resourceKey")
    val keywords: String get() = pinnedString(if (this == Privacy) "settings_privacy_algorithm" else "settings_kw_$resourceKey")
}

private val SettingsSections = listOf(
    "settings_section_app" to listOf(SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content, SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.Storage),
    "settings_section_more" to listOf(SettingsRoute.Features, SettingsRoute.Recognition, SettingsRoute.Messages, SettingsRoute.ListenTogether,
        SettingsRoute.TabLayout, SettingsRoute.SystemPlayback, SettingsRoute.SkylineLyrics, SettingsRoute.FloatingLyrics),
    "settings_section_about" to listOf(SettingsRoute.RemoteConfig, SettingsRoute.About, SettingsRoute.Legal, SettingsRoute.Developer, SettingsRoute.Experimental),
)

@Composable
fun SettingsScreen(
    routeRequest: String? = null,
    onRouteRequestConsumed: () -> Unit = {},
    currentSource: MusicSource = MusicSource.Netease,
    onSourceSelected: (MusicSource) -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenServices: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onDesktopDismiss: () -> Unit = {},
) {
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    val detailFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val equalizer by melox.playback.MeloXEqualizerSettings.current.collectAsState()
    LaunchedEffect(route, equalizer.enabled, MeloXSettingsRuntime.lyricsStyle) { detailFocus.requestFocus() }
    val session = rememberNeteaseSessionStore()
    LaunchedEffect(session.cookie) { if (session.isLoggedIn) session.refreshProfile() }
    LaunchedEffect(routeRequest) {
        if (routeRequest == null) return@LaunchedEffect
        route = SettingsRoute.entries.firstOrNull { it.name == when (routeRequest) {
            "PlayerAppearance" -> "Appearance"; "ContentFeatures" -> "Features"; else -> routeRequest
        } }
        onRouteRequestConsumed()
    }
    var search by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    var detailRoute by remember { mutableStateOf<SettingsRoute?>(null) }
    if (route != null) detailRoute = route
    MeloXBackHandler(enabled = route != null) { route = null }
    if (MeloXSettingsRuntime.desktopLayoutEnabled) {
        // The focus effect above also runs for persisted Desktop startup. Keep
        // its requester attached before that effect, just as in the legacy branch.
        CompositionLocalProvider(melox.ui.desktop.LocalDesktopSettingsPresentation provides true) {
        Box(Modifier.fillMaxSize().focusRequester(detailFocus).focusable()) {
        DesktopSettingsLayout(
            selected = route ?: SettingsRoute.General,
            onSelected = { route = it },
            currentSource = currentSource,
            onSourceSelected = onSourceSelected,
            onOpenMessages = onOpenMessages,
            session = session,
            onOpenLogin = onOpenLogin,
            onOpenServices = onOpenServices,
            onDismiss = onDesktopDismiss,
        )
        }
        }
        return
    }
    Box(Modifier.fillMaxSize().focusRequester(detailFocus).focusable()) {
    val normalized = search.trim().lowercase()
    val visibleSections = SettingsSections.mapNotNull { (title, items) ->
        items.filter { normalized.isBlank() || "${it.title} ${it.subtitle} ${it.keywords}".lowercase().contains(normalized) }
            .takeIf { it.isNotEmpty() }?.let { title to it }
    }
    Column(
        Modifier.fillMaxSize().testTag("settings-root").background(MeloXColors.pageBackground()).verticalScroll(scroll)
             .padding(horizontal = 20.dp).padding(top = 18.dp, bottom = MeloXBottomContentClearanceDp)
             .then(if (route != null && !melox.ui.layout.LocalMeloXListDetail.current) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        MeloXIosTopBar(title = pinnedString("tab_settings"), contentPadding = PaddingValues(0.dp))
        Spacer(Modifier.height(14.dp))
        MeloXGlassTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().testTag("settings-search"),
            leadingContent = { MeloXSymbolIcon(MeloXSymbol.Search, size = 20, iconSize = 24, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) },
             placeholder = { Text(pinnedString("settings_search_placeholder"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 21.sp),
        )
        Spacer(Modifier.height(20.dp))
        if (normalized.isBlank() || pinnedString("settings_account_keywords").lowercase().contains(normalized)) {
            SettingsAccountCard(session, onOpenLogin = onOpenLogin, onOpenServices = onOpenServices)
            Spacer(Modifier.height(24.dp))
        }
        visibleSections.forEach { (title, items) ->
            Text(pinnedString(title), Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            items.forEachIndexed { index, item ->
                MeloXIosListRow(
                    modifier = Modifier.testTag("settings-open-${item.name.lowercase()}"),
                    title = item.title,
                    leading = { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        MeloXActionIcon(item.symbol, Modifier.size(22.dp), melox.ui.glass.MeloXSystemColors.Red)
                    } },
                    showTopSeparator = index > 0,
                    onClick = {
                        when (item) {
                            SettingsRoute.Messages -> onOpenMessages()
                            else -> route = item
                        }
                    },
                )
            }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (visibleSections.isEmpty() && normalized.isNotBlank()) {
            Text(pinnedString("settings_search_empty"), Modifier.padding(vertical = 36.dp).testTag("settings-search-empty"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
        }
        if (normalized.isBlank()) {
            SettingsAccountActions(session)
        }
    }
    melox.ui.layout.MeloXAdaptiveDetail(route != null, onDismiss = { route = null }) { shown ->
    AnimatedVisibility(
        visible = shown,
        enter = meloXPageEnter(fromRight = true),
        exit = meloXPageExit(toRight = true),
        modifier = Modifier.fillMaxSize().zIndex(1f),
    ) {
        detailRoute?.let { SettingsDetail(it, currentSource, onSourceSelected, onOpenMessages, onBack = { route = null }) }
    }
    }
    }
}

private val DesktopSettingsRoutes = listOf(
    SettingsRoute.General,
    SettingsRoute.Features,
    SettingsRoute.Playback,
    SettingsRoute.Storage,
    SettingsRoute.Developer,
)

private fun desktopSettingsGroup(route: SettingsRoute): List<SettingsRoute> = when (route) {
    SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content ->
        listOf(SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content)
    SettingsRoute.Features, SettingsRoute.TabLayout, SettingsRoute.Recognition, SettingsRoute.Messages, SettingsRoute.ListenTogether ->
        listOf(SettingsRoute.Features, SettingsRoute.TabLayout, SettingsRoute.Recognition, SettingsRoute.Messages, SettingsRoute.ListenTogether)
    SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.SystemPlayback, SettingsRoute.SkylineLyrics, SettingsRoute.FloatingLyrics ->
        listOf(SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.SystemPlayback, SettingsRoute.SkylineLyrics, SettingsRoute.FloatingLyrics)
    SettingsRoute.Storage -> listOf(SettingsRoute.Storage)
    else -> listOf(SettingsRoute.RemoteConfig, SettingsRoute.About, SettingsRoute.Legal, SettingsRoute.Privacy, SettingsRoute.Developer, SettingsRoute.Experimental)
}

/** Windows counterpart of the fixed 650pt macOS settings window. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun DesktopSettingsLayout(
    selected: SettingsRoute,
    onSelected: (SettingsRoute) -> Unit,
    currentSource: MusicSource,
    onSourceSelected: (MusicSource) -> Unit,
    onOpenMessages: () -> Unit,
    session: NeteaseSessionStore,
    onOpenLogin: () -> Unit,
    onOpenServices: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var search by remember { mutableStateOf("") }
    var resetConfirmation by remember { mutableStateOf(false) }
    val normalized = search.trim().lowercase()
    val matchingRoutes = SettingsSections.flatMap { it.second }.filter {
        "${it.title} ${it.subtitle} ${it.keywords}".lowercase().contains(normalized)
    }
    Box(Modifier.fillMaxSize().testTag("settings-root")) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MeloXColors.pageBackground()).testTag("desktop-settings-window"),
            contentAlignment = Alignment.TopCenter,
        ) {
            val preferredHeight = when (selected) {
                SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content -> 570.dp
                SettingsRoute.Features, SettingsRoute.TabLayout, SettingsRoute.Recognition, SettingsRoute.Messages, SettingsRoute.ListenTogether -> 480.dp
                SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.Storage, SettingsRoute.SystemPlayback, SettingsRoute.SkylineLyrics, SettingsRoute.FloatingLyrics -> 620.dp
                else -> 600.dp
            }
            Column(
                Modifier.padding(top = 24.dp, bottom = 94.dp)
                    .width(650.dp).height(preferredHeight.coerceAtMost((maxHeight - 118.dp).coerceAtLeast(240.dp)))
                    .shadow(18.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(.65.dp, colors.outlineVariant.copy(alpha = .55f), RoundedCornerShape(14.dp)).testTag("desktop-settings-panel"),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    DesktopSettingsRoutes.forEach { destination ->
                        val active = when (destination) {
                            SettingsRoute.General -> selected in setOf(SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content)
                            SettingsRoute.Features -> selected in setOf(SettingsRoute.Features, SettingsRoute.TabLayout, SettingsRoute.Recognition, SettingsRoute.Messages, SettingsRoute.ListenTogether)
                            SettingsRoute.Playback -> selected in setOf(SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.SystemPlayback, SettingsRoute.SkylineLyrics, SettingsRoute.FloatingLyrics)
                            SettingsRoute.Storage -> selected == SettingsRoute.Storage
                            SettingsRoute.Developer -> selected in setOf(SettingsRoute.Developer, SettingsRoute.Experimental, SettingsRoute.RemoteConfig, SettingsRoute.About, SettingsRoute.Legal, SettingsRoute.Privacy)
                            else -> selected == destination
                        }
                        Column(
                            Modifier.width(96.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (active) colors.onSurface.copy(alpha = .09f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onSelected(destination) }.padding(vertical = 8.dp)
                                .testTag("desktop-settings-tab:${destination.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MeloXSymbolIcon(when (destination) { SettingsRoute.General -> MeloXSymbol.Settings; SettingsRoute.Features -> MeloXSymbol.Switch; SettingsRoute.Playback -> MeloXSymbol.Play; SettingsRoute.Storage -> MeloXSymbol.Drive; else -> MeloXSymbol.Settings }, size = 20, color = if (active) androidx.compose.ui.graphics.Color.Red else colors.onSurface.copy(alpha = .72f))
                            Text(melox.ui.desktop.desktopString(when (destination) {
                                SettingsRoute.General -> "ui.desktop.settings.tab.general"; SettingsRoute.Features -> "ui.desktop.settings.tab.features"
                                SettingsRoute.Playback -> "ui.settings.playback.section.playback"; SettingsRoute.Storage -> "ui.desktop.settings.tab.files"
                                else -> "ui.desktop.settings.tab.advanced"
                            }),
                                modifier = if (destination == SettingsRoute.General) Modifier.testTag("settings-open-general").clickable { onSelected(destination) } else Modifier,
                                fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(.65.dp).background(colors.outlineVariant.copy(alpha = .7f)))
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .verticalScroll(rememberScrollState()).padding(16.dp)
                        .then(if (selected == SettingsRoute.General) Modifier.testTag("settings-general") else Modifier),
                ) {
                    androidx.compose.foundation.text.BasicTextField(search, { search = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = melox.ui.theme.MeloXDesktopTextFontFamily, color = colors.onSurface, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(5.dp)).background(colors.onSurface.copy(alpha = .045f))
                            .border(.5.dp, colors.onSurface.copy(alpha = .15f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 4.dp).testTag("settings-search"),
                        decorationBox = { inner -> Box { if (search.isEmpty()) Text(pinnedString("settings_search_placeholder"), fontSize = 13.sp, color = colors.onSurface.copy(alpha = .4f)); inner() } })
                    Spacer(Modifier.height(18.dp))
                    if (normalized.isNotBlank()) {
                        if (pinnedString("settings_account_keywords").lowercase().contains(normalized)) {
                            SettingsAccountCard(session, onOpenLogin, onOpenServices)
                            Spacer(Modifier.height(18.dp))
                        }
                        matchingRoutes.forEach { destination ->
                            MeloXIosListRow(
                                title = destination.title,
                                modifier = Modifier.testTag("settings-open-${destination.name.lowercase()}"),
                                onClick = {
                                    if (destination == SettingsRoute.Messages) onOpenMessages()
                                    else { onSelected(destination); search = "" }
                                },
                            )
                        }
                        if (matchingRoutes.isEmpty()) Text(pinnedString("settings_search_empty"),
                            Modifier.padding(vertical = 36.dp).testTag("settings-search-empty"),
                            color = colors.onBackground.copy(alpha = .48f))
                    } else {
                    Text(selected.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val relatedRoutes = desktopSettingsGroup(selected)
                    if (relatedRoutes.size > 1) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
                        ) {
                            relatedRoutes.forEach { route ->
                                val active = route == selected
                                Text(
                                    route.title,
                                    modifier = Modifier.clip(RoundedCornerShape(7.dp))
                                         .background(if (active) colors.onSurface.copy(alpha = .14f) else colors.onSurface.copy(alpha = .04f))
                                        .clickable { onSelected(route) }
                                         .padding(horizontal = 9.dp, vertical = 4.dp)
                                        .testTag("desktop-settings-subtab:${route.name}"),
                                     color = colors.onSurface.copy(alpha = if (active) 1f else .76f),
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (selected == SettingsRoute.General) {
                            SettingsAccountCard(session, onOpenLogin, onOpenServices)
                            Spacer(Modifier.height(20.dp))
                    }
                    Column(Modifier.fillMaxWidth().testTag("settings-page:${selected.name}")) {
                        SettingsRouteContent(selected, currentSource, onOpenMessages)
                    }
                    if (selected == SettingsRoute.General) {
                        Spacer(Modifier.height(18.dp))
                         if (session.isLoggedIn) melox.ui.desktop.DesktopDialogButton("ui.settings.account.logout", tag = "settings-sign-out") { session.clear() }
                    }
                    Spacer(Modifier.height(18.dp))
                    }
                }
                HorizontalDivider(thickness = .5.dp)
                Row(Modifier.fillMaxWidth().height(58.dp).testTag("desktop-settings-footer").padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp).testTag("desktop-settings-about").clickable { melox.ui.desktop.MeloXDesktopToolWindows.show(melox.ui.desktop.DesktopTool.About) }, contentAlignment = Alignment.Center) { MeloXSymbolIcon(MeloXSymbol.Question, size = 20) }
                    melox.ui.desktop.DesktopDialogButton("ui.desktop.settings.restore_defaults_ellipsis", tag = "settings-reset") { resetConfirmation = true }
                    Spacer(Modifier.weight(1f))
                    melox.ui.desktop.DesktopDialogButton("ui.common.cancel", onClick = onDismiss)
                    melox.ui.desktop.DesktopDialogButton("ui.common.ok", prominent = true, onClick = onDismiss)
                }
            }
        }
    }
    if (resetConfirmation) melox.ui.desktop.DesktopMessageDialog("ui.desktop.settings.restore_defaults.confirmation",
        melox.ui.desktop.desktopString("ui.desktop.settings.restore_defaults.message"), { resetConfirmation = false },
        confirmKey = "ui.desktop.settings.restore_defaults", onConfirm = { MeloXSettingsPreferences.resetRecommendedPlayerSettings(); resetConfirmation = false })
}

/** Both layouts use the same production actions and settings pages. */
@Composable
private fun SettingsAccountActions(session: NeteaseSessionStore) {
    SettingsDangerButton(pinnedString("settings_reset_player"), Modifier.testTag("settings-reset")) {
        MeloXSettingsPreferences.reset()
        melox.playback.MeloXPlaybackModePreferences.reset()
        PlaybackCommands.changeQuality(MusicQuality.Standard)
    }
    Spacer(Modifier.height(18.dp))
    if (session.isLoggedIn) SettingsDangerButton(pinnedString("settings_sign_out"), Modifier.testTag("settings-sign-out")) { session.clear() }
}

@Composable
private fun SettingsAccountCard(session: NeteaseSessionStore, onOpenLogin: () -> Unit, onOpenServices: () -> Unit) {
    val accent = melox.ui.glass.MeloXSystemColors.Red
    Text(pinnedString("accessibility_account"), Modifier.padding(start = 8.dp, bottom = 8.dp), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f))
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
    MeloXIosListRow(
        title = session.profile?.nickname ?: if (session.isLoggedIn && session.isRefreshing) pinnedString("settings_account_loading") else pinnedString("app_login_netease"),
        modifier = Modifier.testTag("settings-open-login"),
        detail = if (session.isLoggedIn && session.profile != null) pinnedString("provider_logged_in") else null,
        leading = {
            when {
                session.profile != null -> MeloXArtworkImage(session.profile?.avatarUrl, accent, Modifier.size(30.dp).clip(CircleShape))
                session.isLoggedIn && session.isRefreshing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = accent)
                else -> MeloXSymbolIcon(MeloXSymbol.PersonFill, size = 30, iconSize = 24, weight = FontWeight.Medium, color = accent)
            }
        },
        onClick = onOpenLogin,
    )
    MeloXIosListRow(
        title = pinnedString("tab_services"),
        subtitle = pinnedString("settings_services_subtitle"),
        modifier = Modifier.testTag("settings-open-services"),
        leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, size = 30, iconSize = 24, color = accent) },
        showTopSeparator = true,
        onClick = onOpenServices,
    )
    }
}

@Composable
private fun SettingsDetail(
    route: SettingsRoute,
    currentSource: MusicSource,
    onSourceSelected: (MusicSource) -> Unit,
    onOpenMessages: () -> Unit,
    onBack: () -> Unit,
) {
    melox.ui.glass.MeloXPinnedListPage(route.title, onNavigateBack = onBack,
        bottomPadding = MeloXBottomContentClearanceDp,
        modifier = Modifier.testTag("settings-${route.name.lowercase()}")) {
        item(key = "settings-detail:${route.name}") {
          Column {
            SettingsRouteContent(route, currentSource, onOpenMessages)
          }
        }
    }
}

@Composable
private fun SettingsRouteContent(route: SettingsRoute, currentSource: MusicSource, onOpenMessages: () -> Unit) {
    when (route) {
        SettingsRoute.General -> GeneralSettings()
        SettingsRoute.Appearance -> AppearanceSettings()
        SettingsRoute.Content -> ContentSettings()
        SettingsRoute.TabLayout -> TabLayoutSettings(currentSource)
        SettingsRoute.Playback -> PinnedPlaybackSettings()
        SettingsRoute.Lyrics -> LyricsSettings()
        SettingsRoute.SkylineLyrics -> SkylineLyricsSettings()
        SettingsRoute.SystemPlayback -> SystemPlaybackSettings()
        SettingsRoute.FloatingLyrics -> FloatingLyricsSettings()
        SettingsRoute.Recognition -> RecognitionSettings()
        SettingsRoute.ListenTogether -> ListenTogetherSettings()
        SettingsRoute.Storage -> PinnedStorageSettings()
        SettingsRoute.Features -> FeatureSettings()
        SettingsRoute.Messages -> MessagesSettings(onOpenMessages)
        SettingsRoute.RemoteConfig -> RemoteConfigSettings()
        SettingsRoute.Legal -> LegalSettings()
        SettingsRoute.Privacy -> PrivacySettings()
        SettingsRoute.About -> PinnedAboutSettings()
        SettingsRoute.Developer -> DeveloperSettings()
        SettingsRoute.Experimental -> ExperimentalSettings(currentSource)
    }
}

@Composable
private fun GeneralSettings() {
    SettingsGlassGroup {
        SettingsExternalToggleRow(melox.ui.desktop.desktopPlatformString("layout_title"),
            MeloXSettingsRuntime.desktopLayoutEnabled, "setting-general_desktop_layout",
            subtitle = melox.ui.desktop.desktopPlatformString("layout_hint")) {
            MeloXSettingsPreferences.setBoolean("general_desktop_layout", it)
        }
        SettingsChoice("settings_general_theme", "theme_mode", MeloXSettingsRuntime.themeMode,
            listOf(MeloXThemeMode.System to pinnedString("settings_general_theme_system"), MeloXThemeMode.Light to pinnedString("settings_general_theme_light"),
                MeloXThemeMode.Dark to pinnedString("settings_general_theme_dark"))) { MeloXSettingsPreferences.setString("theme_mode", it.name) }
        SettingsToggleRow("settings_split_screen", "general_split_screen", true, subtitle = pinnedString("settings_split_screen_hint"))
        if (MeloXSettingsRuntime.splitScreenEnabled) {
            val positions = melox.settings.MeloXSplitChromePosition.entries.map { position ->
                position to pinnedString("settings_position_${when (position) {
                    melox.settings.MeloXSplitChromePosition.BottomLeft -> "bottom_left"
                    melox.settings.MeloXSplitChromePosition.BottomRight -> "bottom_right"
                    melox.settings.MeloXSplitChromePosition.TopLeft -> "top_left"
                    melox.settings.MeloXSplitChromePosition.TopRight -> "top_right"
                    melox.settings.MeloXSplitChromePosition.Left -> "left"
                    melox.settings.MeloXSplitChromePosition.Right -> "right"
                }}")
            }
            SettingsChoice("settings_split_navigation_position", "general_split_navigation_position", MeloXSettingsRuntime.splitNavigationPosition, positions) {
                MeloXSettingsPreferences.setString("general_split_navigation_position", it.name)
            }
            SettingsChoice("settings_split_mini_player_position", "general_split_mini_player_position", MeloXSettingsRuntime.splitMiniPlayerPosition, positions) {
                MeloXSettingsPreferences.setString("general_split_mini_player_position", it.name)
            }
        }
        SettingsChoice("settings_general_launch", "general_default_tab", MeloXSettingsRuntime.defaultTab,
            listOf("Home", "Explore", "Library", "Settings").map { it to pinnedString("settings_tabs_${it.lowercase()}") }) {
            MeloXSettingsPreferences.setString("general_default_tab", it)
        }
        SettingsToggleRow("settings_general_remember_tab", "general_remember_tab", true)
        SettingsChoice("settings_general_swipe", "general_swipe_full_action", MeloXSettingsRuntime.swipeFullAction,
            listOf(melox.settings.MeloXSwipeFullAction.PlayNext to pinnedString("settings_general_play_next"),
                melox.settings.MeloXSwipeFullAction.AddToQueue to pinnedString("settings_general_add_queue"))) {
            MeloXSettingsPreferences.setString("general_swipe_full_action", it.name)
        }
        SettingsToggleRow("settings_general_clipboard", "general_clipboard_links", true)
        SettingsToggleRow("settings_general_haptics", "general_haptic_feedback", true, enabled = false)
        SettingsToggleRow("settings_general_system_font", "system_font", false)
        SettingsToggleRow("settings_general_tabbar", "general_disable_auto_tabbar_shrink", false)
    }
    SettingsPlatformNote(pinnedString("settings_general_haptics"))
}

@Composable
private fun AppearanceSettings() {
    val r = MeloXSettingsRuntime
    SettingsGlassGroup {
        SettingsChoice("settings_player_shell", "player_shell", r.playerShell,
            listOf(MeloXPlayerShell.AppleMusic to "Apple Music", MeloXPlayerShell.Classic to pinnedString("settings_player_shell_classic"))) {
            MeloXSettingsPreferences.setString("player_shell", it.name)
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup { SettingsToggleRow("settings_frosted_glass", "player_frosted_glass", false) }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsChoice("settings_player_background", "player_background_mode", r.playerBackgroundMode,
            listOf(MeloXPlayerBackgroundMode.FlowingLight to pinnedString("settings_bg_flowing_light"),
                MeloXPlayerBackgroundMode.AppleLyrics to pinnedString("settings_bg_apple_lyrics"),
                MeloXPlayerBackgroundMode.BlurredArtwork to pinnedString("settings_bg_blurred_artwork"))) {
            MeloXSettingsPreferences.setString("player_background_mode", it.name)
        }
        if (r.playerBackgroundMode == MeloXPlayerBackgroundMode.FlowingLight) {
            LyricsFloatSlider("settings_flowing_speed", "player_flowing_speed", r.flowingLightSpeed, .25f..2f, 6) { "%.2fx".format(it) }
            LyricsFloatSlider("settings_flowing_saturation", "player_flowing_saturation", r.flowingLightSaturation, 0f..2f, 19)
            LyricsFloatSlider("settings_flowing_brightness", "player_flowing_brightness", r.flowingLightBrightness, .4f..1.6f, 11)
        }
        SettingsToggleRow("settings_flowing_backdrop", "player_flowing_backdrop", true)
        SettingsToggleRow("settings_background_isolation", "player_background_isolation", true)
        LyricsIntChoice("settings_background_fps", "lyrics_background_frame_rate", r.lyricBackgroundFrameRate, listOf(15, 24, 30, 45, 60)) {
            pinnedString(if (it <= 24) "settings_fps_battery" else if (it == 30) "settings_fps_balanced" else "settings_fps_recommended", it)
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsToggleRow("settings_reduce_motion", "reduce_motion", false)
        SettingsToggleRow("settings_artwork_motion", "player_artwork_motion", true)
        SettingsChoice("settings_screen_awake_scope", "player_screen_awake_mode", r.screenAwakeMode,
            listOf(melox.settings.MeloXScreenAwakeMode.Disabled to pinnedString("settings_skyline_off"),
                melox.settings.MeloXScreenAwakeMode.Player to pinnedString("settings_awake_player"),
                melox.settings.MeloXScreenAwakeMode.Lyrics to pinnedString("settings_awake_lyrics"),
                melox.settings.MeloXScreenAwakeMode.HiddenLyricsInterface to pinnedString("settings_awake_hidden_lyrics")),
            ) { MeloXSettingsPreferences.setString("player_screen_awake_mode", it.name) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup { SettingsToggleRow("settings_immersive_playback", "immersive_playback", false) }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        // These two source labels are literal Chinese even in the default Android locale.
        SettingsExternalToggleRow("隐藏歌单搜索栏", r.playlistSearchBarHidden, "setting-library_playlist_search_hidden") {
            MeloXSettingsPreferences.setBoolean("library_playlist_search_hidden", it)
        }
        SettingsExternalToggleRow("歌单展示封面", r.playlistShowArtwork, "setting-library_playlist_show_artwork") {
            MeloXSettingsPreferences.setBoolean("library_playlist_show_artwork", it)
        }
    }
}

@Composable
private fun ContentSettings() {
    SettingsGlassGroup {
        SettingsChoice("settings_content_area", "music_area", MeloXSettingsRuntime.musicArea,
            listOf("全部" to "all", "华语" to "chinese", "欧美" to "western", "日本" to "japan", "韩国" to "korea")
                .map { (key, label) -> key to pinnedString("settings_content_area_$label") }) { MeloXSettingsPreferences.setString("music_area", it) }
        SettingsToggleRow("settings_content_hq_playlists", "content_high_quality_playlist", true)
        SettingsToggleRow("settings_content_play_count", "content_playlist_play_count", true)
        CrossProviderFallbackSetting()
    }
}

@Composable
internal fun PreferenceToggle(title: String, checked: Boolean, key: String, subtitle: String? = null, showTopSeparator: Boolean = false) {
    if (MeloXSettingsRuntime.desktopLayoutEnabled) {
        melox.ui.desktop.DesktopSettingsCheckbox(title, checked, key, subtitle = subtitle) { MeloXSettingsPreferences.setBoolean(key, it) }
        return
    }
    MeloXIosListRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        showTopSeparator = showTopSeparator,
        trailingContent = {
            melox.ui.glass.MeloXGlassToggle(
                checked = checked,
                modifier = Modifier.testTag("setting-$key"),
                onCheckedChange = { MeloXSettingsPreferences.setBoolean(key, it) },
            )
        },
    )
}

@Composable
private fun LyricsSettings() {
    AdvancedLyricsSettings()
}

@Composable
private fun FeatureSettings() {
    SettingsGlassGroup {
        SettingsToggleRow("settings_feature_podcasts", "feature_podcasts", true)
        SettingsToggleRow("settings_feature_history", "feature_history", true)
        SettingsToggleRow("settings_feature_downloads", "feature_downloads", true)
        SettingsToggleRow("settings_feature_cloud", "feature_cloud_music", true)
    }
}

@Composable
private fun MessagesSettings(onOpenMessages: () -> Unit) {
    MeloXIosListRow(
        modifier = Modifier.testTag("settings-open-messages"),
        title = "私信",
        subtitle = "查看联系人、会话详情并发送文字私信",
        leadingIcon = MeloXSymbol.Message,
        onClick = onOpenMessages,
    )
}
