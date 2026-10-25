package melox.ui.screens

import androidx.compose.runtime.collectAsState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryCache
import melox.library.NeteaseLibraryClient
import melox.library.NeteaseLibrarySnapshot
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicAccountSummary
import melox.music.model.MusicSource
import melox.music.provider.LocalAggregationCapability
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.MusicProviderSelectionStore
import melox.music.provider.PlaylistCapability
import melox.music.provider.UserLibraryCapability
import melox.playback.PlaybackCommands
import melox.settings.MeloXContentLayout
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.glass.MeloXShapes
import melox.ui.glass.meloXLiquidBottomBar
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.meloXLiquidTabSelection
import melox.ui.glass.rememberMeloXLiquidInteraction
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import kotlin.math.roundToInt

internal val MeloXBottomContentClearanceDp = 156.dp

/**
 * 1:1 port of Android ui/library/LibraryScreen.kt (main structure):
 * login gate -> SharedTransitionLayout -> AnimatedContent(list/detail)
 * -> top bar + liquid segmented picker -> capability-gated pages.
 *
 * MeloXSettingsRuntime gates default to enabled=true on desktop
 * (Android defaults: podcasts/history/cloud/downloads library placement true).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    navState: MeloXNavState? = null,
    requestedPage: String? = null,
) {
    val requestedSource = MusicProviderSelectionStore.selectedSource()
    val openSourceLogin: () -> Unit = {
        melox.ui.navigation.MeloXLoginRequestBus.request(requestedSource)
        navState?.navigateTo(melox.ui.navigation.Route.Login)
    }
    if (requestedSource in setOf(MusicSource.Local, MusicSource.Spotify, MusicSource.YouTubeMusic, MusicSource.Qishui)) {
        MeloXProviderLibraryScreen(requestedSource, requestedPage, openSourceLogin)
        return
    }
    if (MeloXSettingsRuntime.desktopLayoutEnabled && MusicProviderSelectionStore.selectedSource() == MusicSource.Netease) {
        melox.ui.desktop.MeloXDesktopLibrary(requestedPage, openSourceLogin)
        return
    }
    val scope = rememberCoroutineScope()
    val session = melox.account.rememberNeteaseSessionStore()
    val source = MusicProviderSelectionStore.selectedSource()
    val client = remember {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie() })
    }
    val provider = remember(source) {
        if (source == MusicSource.Netease) null else MeloXMusicProviders.create().require(source)
    }
    val providerLibrary = provider as? UserLibraryCapability
    val cache = remember { NeteaseLibraryCache() }
    val jellyfinRevision by melox.provider.jellyfin.JellyfinSessionStore.revision.collectAsState()
    val accountRevision = if (source == MusicSource.Jellyfin) jellyfinRevision else 0L

    val initialPage = MeloXLibraryPage.valueOf(MeloXContentLayout.initialLibraryPage(source))
    var selectedPage by rememberSaveable(source) { mutableStateOf(initialPage) }
    LaunchedEffect(source, requestedPage) {
        MeloXLibraryPage.entries.firstOrNull { it.name == requestedPage && it.isEnabled(source) }?.let { selectedPage = it }
    }
    var selectedPlaylist by remember(source, session.cookie, accountRevision) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var snapshot by remember(source, session.cookie, accountRevision) { mutableStateOf<NeteaseLibrarySnapshot?>(null) }
    var providerAccount by remember(source, accountRevision) { mutableStateOf<MusicAccountSummary?>(null) }
    var loading by remember(source, session.cookie, accountRevision) { mutableStateOf(source != MusicSource.Netease) }
    var errorMessage by remember(source, session.cookie, accountRevision) { mutableStateOf<String?>(null) }
    val playlistListState = rememberLazyListState()

    suspend fun refreshLibrary() {
        loading = true
        errorMessage = null
        if (source == MusicSource.Netease) {
            if (!session.isLoggedIn) {
                loading = false
                return
            }
            if (session.profile == null) session.refreshProfile(force = true)
            val userId = session.profile?.userId
            if (userId == null) {
                loading = false
                return
            }
            val requestedCookie = session.cookie
            val requestedRevision = NeteaseSessionStore.revision.value
            runCatching {
                withContext(Dispatchers.IO) {
                    NeteaseLibraryClient(cookieProvider = { requestedCookie }).snapshot(userId)
                }
            }
                .onSuccess {
                    if (requestedRevision != NeteaseSessionStore.revision.value || session.cookie != requestedCookie) {
                        throw kotlinx.coroutines.CancellationException("NetEase account changed during library refresh")
                    }
                    snapshot = it
                    cache.saveSnapshot(userId, it)
                }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    errorMessage = it.message ?: "音乐库加载失败"
                }
        } else if (source == MusicSource.Local) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val tracks = (provider as? LocalAggregationCapability)
                        ?.aggregationTracks(page = 1, pageSize = 200)
                        ?.items
                        ?: error("本地音乐不可用")
                    providerAccount = MusicAccountSummary(
                        source = MusicSource.Local,
                        id = "local",
                        displayName = "本地音乐",
                    )
                    snapshot = NeteaseLibrarySnapshot(
                        likedSongs = tracks.map(MeloXLegacyUiBridge::track),
                        playlists = emptyList(),
                        recentSongs = emptyList(),
                        likedPlaylistId = null,
                    )
                }
            }.onFailure {
                providerAccount = null
                snapshot = null
                errorMessage = it.message ?: "本地音乐加载失败"
            }
        } else {
            val capability = providerLibrary
            if (capability == null) {
                providerAccount = null
                snapshot = null
                errorMessage = "${source.displayName} 不支持音乐库"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val account = capability.accountSummary()
                    val playlists = if (account != null && provider is PlaylistCapability) {
                        capability.userPlaylists(page = 1, pageSize = 100).items
                    } else {
                        emptyList()
                    }
                    account to playlists
                }
            }.onSuccess { (account, playlists) ->
                if (source == MusicSource.Jellyfin && melox.provider.jellyfin.JellyfinSessionStore.revision.value != accountRevision) {
                    throw kotlinx.coroutines.CancellationException("Jellyfin 登录会话已变更")
                }
                providerAccount = account
                snapshot = if (account == null) null else MeloXLegacyUiBridge.library(playlists)
            }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                providerAccount = null
                snapshot = null
                errorMessage = failure.message ?: "${source.displayName} 音乐库加载失败"
            }
        }
        loading = false
    }

    LaunchedEffect(source, session.cookie, session.profile?.userId, accountRevision) {
        selectedPlaylist = null
        if (source == MusicSource.Netease) {
            val userId = session.profile?.userId ?: return@LaunchedEffect
            cache.loadSnapshot(userId)?.let { snapshot = it }
            if (NeteaseLibraryCache.beginLibraryColdStartRefresh(userId)) {
                refreshLibrary()
            }
        } else {
            refreshLibrary()
        }
    }

    LaunchedEffect(source, session.cookie) {
        if (source != MusicSource.Netease) return@LaunchedEffect
        if (session.isLoggedIn && session.profile == null) {
            session.refreshProfile()
            if (session.profile != null) refreshLibrary()
        }
        if (!session.isLoggedIn) loading = false
    }

    val availablePages = MeloXContentLayout.libraryPages(source)
    LaunchedEffect(source, availablePages) {
        if (!selectedPage.isEnabled(source)) {
            selectedPage = MeloXLibraryPage.valueOf(MeloXContentLayout.initialLibraryPage(source))
        }
    }
    LaunchedEffect(source, selectedPage, melox.settings.MeloXSettingsRuntime.rememberLibraryPage) {
        MeloXContentLayout.rememberLibraryPage(source, selectedPage.name)
    }

    if (source == MusicSource.Netease && !session.isLoggedIn) {
        MeloXLibraryLoginUnavailable(
            onLogin = openSourceLogin,
            sourceName = source.displayName,
        )
        return
    }
    if (!MeloXSettingsRuntime.desktopLayoutEnabled && source != MusicSource.Netease && source != MusicSource.Local && !loading && providerAccount == null && errorMessage == null) {
        MeloXLibraryLoginUnavailable(
            onLogin = openSourceLogin,
            sourceName = source.displayName,
        )
        return
    }

    if (MeloXSettingsRuntime.desktopLayoutEnabled && source != MusicSource.Netease) {
        if (selectedPlaylist != null) {
            PlaylistDetailScreen(requireNotNull(selectedPlaylist), onBack = { selectedPlaylist = null })
        } else if (source == MusicSource.Local) {
            melox.ui.desktop.DesktopLibrarySongs(
                titleKey = "ui.navigation.library",
                songs = snapshot?.likedSongs.orEmpty(),
                loading = loading,
                error = errorMessage,
                onRetry = { scope.launch { refreshLibrary() } },
                onLogin = openSourceLogin.takeIf { providerAccount == null },
                tag = "desktop-provider-library:Local",
            )
        } else {
            melox.ui.desktop.MeloXDesktopProviderLibrary(
                source = source,
                playlists = snapshot?.playlists.orEmpty(),
                loading = loading,
                error = errorMessage,
                accountName = providerAccount?.displayName,
                onLogin = openSourceLogin,
                onRetry = { scope.launch { refreshLibrary() } },
                onOpen = { selectedPlaylist = it },
            )
        }
        return
    }

    val splitPlaylistDetail = !MeloXSettingsRuntime.desktopLayoutEnabled &&
        melox.ui.layout.LocalMeloXListDetail.current && !melox.ui.layout.LocalMeloXInDetailPane.current
    var retainedPlaylist by remember(source) { mutableStateOf(selectedPlaylist) }
    androidx.compose.runtime.SideEffect { if (selectedPlaylist != null) retainedPlaylist = selectedPlaylist }
    melox.ui.navigation.MeloXBackHandler(selectedPlaylist != null) { selectedPlaylist = null }
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedScope = this

        AnimatedContent(
            targetState = selectedPlaylist.takeUnless { splitPlaylistDetail },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val openingDetail = targetState != null
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = MeloXMotion.ContentEnterMillis,
                            delayMillis = 0,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(
                            durationMillis = MeloXMotion.ContentExitMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                ).apply {
                    // The newest target must stay above any retained outgoing
                    // playlist content while an interrupted reverse animation finishes.
                    targetContentZIndex = if (openingDetail) 2f else 0f
                }
            },
            contentKey = { playlist -> playlist?.id ?: Long.MIN_VALUE },
            label = "library-playlist-detail-transition",
        ) { targetPlaylist ->
            val playlistTransitionVisibilityScope = this
            if (targetPlaylist != null) {
                if (MeloXSettingsRuntime.desktopLayoutEnabled) {
                    // Library must use the same Mac collection surface as Home/Search.
                    // The shared-element mobile detail below belongs to the mobile layout only.
                    PlaylistDetailScreen(targetPlaylist, onBack = { selectedPlaylist = null })
                } else {
                MeloXLibraryPlaylistDetailScreen(
                    initialPlaylist = targetPlaylist,
                    client = client,
                    onBack = { selectedPlaylist = null },
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = playlistTransitionVisibilityScope,
                    onSongLikeChanged = { song, liked ->
                        val current = snapshot ?: return@MeloXLibraryPlaylistDetailScreen
                        val updated = current.withSongLiked(song, liked)
                        snapshot = updated
                        session.profile?.userId?.let { userId ->
                            scope.launch { cache.saveSnapshot(userId, updated) }
                        }
                    },
                )
                }
            } else {
                val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MeloXColors.pageBackground())
                        .then(if (desktop) Modifier else Modifier.statusBarsPadding()),
                ) {
                    if (desktop) {
                        Text(
                            text = "音乐库",
                            modifier = Modifier.padding(start = 34.dp, end = 34.dp, top = 28.dp, bottom = 12.dp),
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        MeloXIosTopBar(title = "音乐库")
                    }

                    MeloXLibrarySegmentedPicker(
                        selected = selectedPage,
                        onSelected = { selectedPage = it },
                        source = source,
                        modifier = Modifier
                            .padding(horizontal = if (desktop) 34.dp else 16.dp, vertical = 12.dp)
                            .then(if (desktop) Modifier.widthIn(max = 520.dp) else Modifier),
                    )

                    if (errorMessage != null && snapshot == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MeloXColors.pageOnBackground().copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "重新载入",
                                    modifier = Modifier
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { scope.launch { refreshLibrary() } }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MeloXColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (loading && snapshot == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val data = snapshot ?: NeteaseLibrarySnapshot(emptyList(), emptyList(), emptyList())
                        when (selectedPage) {
                            MeloXLibraryPage.Songs -> MeloXLibrarySongsPage(
                                songs = data.likedSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        songs = data.likedSongs,
                                        selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    data.likedSongs.firstOrNull()?.let { first ->
                                        PlaybackCommands.playQueue(
                                            songs = data.likedSongs,
                                            selectedSongId = first.id,
                                            onFailure = { errorMessage = it.message ?: "播放失败" },
                                        )
                                    }
                                },
                                onHeartMode = {
                                    val seed = data.likedSongs.randomOrNull()
                                    val playlistId = data.likedPlaylistId
                                    if (seed != null && playlistId != null) scope.launch {
                                        runCatching { client.intelligenceModeSongs(seed.id, playlistId) }
                                            .onSuccess { songs ->
                                                songs.firstOrNull()?.let {
                                                    PlaybackCommands.playQueue(
                                                        songs = songs,
                                                        selectedSongId = it.id,
                                                        heartMode = true,
                                                    )
                                                }
                                            }
                                            .onFailure { errorMessage = it.message ?: "无法启动心动模式" }
                                    }
                                },
                            )

                            MeloXLibraryPage.Playlists -> MeloXLibraryPlaylistsPage(
                                playlists = data.playlists,
                                onPlaylistClick = { selectedPlaylist = it },
                                listState = playlistListState,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = playlistTransitionVisibilityScope,
                            )

                            MeloXLibraryPage.Podcasts -> Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                PodcastsScreen(navState = navState ?: remember { MeloXNavState() })
                            }

                            MeloXLibraryPage.Cloud -> Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                CloudScreen(navState = navState ?: remember { MeloXNavState() }, embedded = true)
                            }

                            MeloXLibraryPage.History -> MeloXLibrarySongsPage(
                                songs = data.recentSongs,
                                onPlay = { song ->
                                    PlaybackCommands.playQueue(
                                        songs = data.recentSongs,
                                        selectedSongId = song.id,
                                        onFailure = { errorMessage = it.message ?: "播放失败" },
                                    )
                                },
                                onPlayAll = {
                                    data.recentSongs.firstOrNull()?.let { first ->
                                        PlaybackCommands.playQueue(
                                            songs = data.recentSongs,
                                            selectedSongId = first.id,
                                            onFailure = { errorMessage = it.message ?: "播放失败" },
                                        )
                                    }
                                },
                            )

                            MeloXLibraryPage.Downloads -> MeloXLibraryDownloadsPage()
                        }
                    }
                }
            }
        }
    }
    melox.ui.layout.MeloXAdaptiveDetail(splitPlaylistDetail && selectedPlaylist != null, onDismiss = { selectedPlaylist = null }) { shown ->
        androidx.compose.animation.AnimatedVisibility(visible = shown,
            modifier = Modifier.fillMaxSize().testTag("library-split-playlist-detail"),
            enter = fadeIn(MeloXMotion.contentEnterTween()), exit = fadeOut(MeloXMotion.contentExitTween())) {
            val detailPlaylist = selectedPlaylist ?: retainedPlaylist
            if (detailPlaylist != null) androidx.compose.runtime.key(detailPlaylist.id, detailPlaylist.providerPlaylist?.id) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    val detailSharedScope = this
                    androidx.compose.animation.AnimatedVisibility(visible = true) {
                        MeloXLibraryPlaylistDetailScreen(detailPlaylist, client, onBack = { selectedPlaylist = null },
                            sharedTransitionScope = detailSharedScope, animatedVisibilityScope = this,
                            onSongLikeChanged = { song, liked ->
                                snapshot?.let { current ->
                                    val updated = current.withSongLiked(song, liked)
                                    snapshot = updated
                                    session.profile?.userId?.let { userId -> scope.launch { cache.saveSnapshot(userId, updated) } }
                                }
                            })
                    }
                }
            }
        }
    }
}

internal fun NeteaseLibrarySnapshot.withSongLiked(song: SearchSong, liked: Boolean): NeteaseLibrarySnapshot =
    copy(
        likedSongs = if (liked) {
            listOf(song) + likedSongs.filterNot { it.id == song.id }
        } else {
            likedSongs.filterNot { it.id == song.id }
        },
    )

private enum class MeloXLibraryPage(val title: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    Podcasts("播客"),
    Cloud("云盘"),
    History("最近播放"),
    Downloads("下载"),
}

/**
 * Presentation capability gate only. Provider-specific differences are kept out
 * of the renderer: unsupported product sections are simply absent while the
 * same MeloX transitions/backgrounds remain active.
 */
private fun MeloXLibraryPage.isEnabled(source: MusicSource): Boolean = name in MeloXContentLayout.libraryPages(source)

@Composable
private fun MeloXLibraryLoginUnavailable(
    onLogin: (() -> Unit)?,
    sourceName: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("library-login-required").semantics { stateDescription = sourceName }
            .background(MeloXColors.pageBackground())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        MeloXIosTopBar(
            title = "音乐库",
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要登录", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "登录后可读取收藏歌曲、歌单和播放记录。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MeloXColors.pageOnBackground().copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
                if (onLogin != null) {
                    val interaction = rememberMeloXLiquidInteraction()
                    Box(
                        modifier = Modifier
                            .testTag("library-open-login")
                            .padding(top = 18.dp)
                            .meloXLiquidButton(
                                shape = RoundedCornerShape(18.dp),
                                tint = Color.White,
                                surfaceColor = MeloXColors.Primary.copy(alpha = 0.92f),
                                interaction = interaction,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onLogin,
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "登录$sourceName",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Exact port of Android MeloXLibrarySegmentedPicker: liquid glass panel with a
 * single moving selection lens animated with spring(1f, 460f).
 */
@Composable
private fun MeloXLibrarySegmentedPicker(
    selected: MeloXLibraryPage,
    onSelected: (MeloXLibraryPage) -> Unit,
    source: MusicSource,
    modifier: Modifier = Modifier,
) {
    MeloXProviderLibraryPicker(selected.name, { onSelected(MeloXLibraryPage.valueOf(it)) },
        MeloXLibraryPage.entries.filter { it.isEnabled(source) }.map { it.name to it.title }, modifier)
}

@Composable
internal fun MeloXProviderLibraryPicker(
    selected: String,
    onSelected: (String) -> Unit,
    pages: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val panelShape = MeloXShapes.compact
    val lensShape = RoundedCornerShape(15.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val selectedIndex = pages.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val lensPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 1f,
            stiffness = 460f,
            visibilityThreshold = 0.001f,
        ),
        label = "library-segment-lens-position",
    )
    val panelTint = MeloXColors.Surface.copy(alpha = 0.10f)
    val panelSurface = MeloXColors.pageOnBackground().copy(alpha = 0.055f)
    val selectionTint = if (dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    BoxWithConstraints(
        modifier = modifier
            .testTag("library-pages").semantics { stateDescription = selected }
            .fillMaxWidth()
            .height(30.dp)
            .meloXLiquidBottomBar(
                shape = panelShape,
                tint = panelTint,
                surfaceColor = panelSurface,
            ),
    ) {
        val panelWidthPx = constraints.maxWidth

        // Invisible sizing row (Android renders this row through the backdrop;
        // desktop has no layer sampling so it is purely optical spacing).
        Row(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(page.second, fontSize = 13.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(1f / pages.size)
                .fillMaxHeight()
                .offset {
                    IntOffset(
                        x = (lensPosition * panelWidthPx / pages.size).roundToInt(),
                        y = 0,
                    )
                }
                .padding(horizontal = 1.dp, vertical = 1.dp)
                .meloXLiquidTabSelection(
                    shape = lensShape,
                    selected = true,
                    tint = selectionTint,
                ),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEach { page ->
                val isSelected = page.first == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("library-page:${page.first}")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(page.first) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = page.second,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MeloXColors.pageOnBackground(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXLibrarySongsPage(
    songs: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onPlayAll: () -> Unit,
    onHeartMode: (() -> Unit)? = null,
) {
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌曲",
                color = MeloXColors.pageOnBackground().copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (desktop) 34.dp else 0.dp,
            end = if (desktop) 34.dp else 0.dp,
            bottom = if (desktop) 104.dp else MeloXBottomContentClearanceDp,
        ),
    ) {
        item {
            MeloXPlayAllRow(onPlayAll)
            onHeartMode?.let { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable(onClick = action)
                        .padding(start = 20.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MeloXSymbolIcon(
                        symbol = melox.ui.foundation.MeloXSymbol.HeartFill,
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFFFF3B30),
                    )
                    Text("心动模式", fontSize = 17.sp, color = MeloXColors.pageOnBackground())
                }
            }
            MeloXInsetDivider(leading = 68.dp)
        }
        items(songs, key = { it.id }) { song ->
            MeloXLibraryTrackRow(song = song, onClick = { onPlay(song) })
            MeloXInsetDivider(leading = 68.dp)
        }
    }
}

@Composable
internal fun MeloXPlayAllRow(onClick: () -> Unit) {
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (desktop) 48.dp else 58.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MeloXPlayGlyph(
            modifier = Modifier.size(28.dp),
            color = MeloXRed,
        )
        Text(
            text = "播放全部",
            fontSize = if (desktop) 14.sp else 17.sp,
            fontWeight = FontWeight.Normal,
            color = MeloXColors.pageOnBackground(),
        )
    }
}

@Composable
internal fun MeloXLibraryTrackRow(
    song: SearchSong,
    onClick: () -> Unit,
) {
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (desktop) 54.dp else 66.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeloXArtworkImage(
            url = song.artworkUrl,
            fallbackColor = MeloXColors.Surface,
            modifier = Modifier
                .size(if (desktop) 40.dp else 44.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = song.name,
                fontSize = if (desktop) 14.sp else 17.sp,
                lineHeight = if (desktop) 18.sp else 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MeloXColors.pageOnBackground(),
            )
            Text(
                text = song.artists,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MeloXColors.pageOnBackground().copy(alpha = 0.46f),
            )
        }
        if (song.durationMs > 0L) {
            Text(
                text = meloXFormatDuration(song.durationMs),
                fontSize = 13.sp,
                color = MeloXColors.pageOnBackground().copy(alpha = 0.46f),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXLibraryPlaylistsPage(
    playlists: List<NeteasePlaylistSummary>,
    onPlaylistClick: (NeteasePlaylistSummary) -> Unit,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有收藏歌单",
                color = MeloXColors.pageOnBackground().copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = if (desktop) 34.dp else 0.dp,
            end = if (desktop) 34.dp else 0.dp,
            bottom = if (desktop) 104.dp else MeloXBottomContentClearanceDp,
        ),
    ) {
        item {
            Text(
                text = "歌单",
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
                fontSize = 13.sp,
                color = MeloXColors.pageOnBackground().copy(alpha = 0.50f),
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (desktop) 54.dp else 66.dp)
                    .testTag("library-playlist:${playlist.id}")
                    .clickable { onPlaylistClick(playlist) }
                    .padding(start = 18.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val sharedArtworkModifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(
                            key = meloXPlaylistArtworkSharedKey(playlist.id),
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        // The cover itself must stay in the shared overlay for
                        // the row -> detail flight. The detail screen clips
                        // its settled cover; only the transition uses this
                        // elevated layer.
                        renderInOverlayDuringTransition = true,
                        zIndexInOverlay = 1f,
                    )
                }

                MeloXArtworkImage(
                    url = playlist.coverUrl,
                    fallbackColor = MeloXColors.Surface,
                    modifier = sharedArtworkModifier
                        .size(if (desktop) 40.dp else 44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        playlist.name,
                        fontSize = if (desktop) 14.sp else 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} 首歌曲",
                        fontSize = 12.sp,
                        color = MeloXColors.pageOnBackground().copy(alpha = 0.48f),
                    )
                }
                MeloXSymbolIcon(
                    melox.ui.foundation.MeloXSymbol.ChevronRight,
                    size = 15,
                    color = MeloXColors.pageOnBackground().copy(alpha = 0.40f),
                )
            }
            MeloXInsetDivider(leading = 84.dp)
        }
    }
}

@Composable
private fun MeloXInsetDivider(leading: androidx.compose.ui.unit.Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = leading, end = 18.dp),
        thickness = 0.6.dp,
        color = MeloXColors.pageOnBackground().copy(alpha = 0.12f),
    )
}

internal fun meloXPlaylistArtworkSharedKey(playlistId: Long): String =
    "library-playlist-artwork-$playlistId"

/** Token-mapped action icon, exact port of Android MeloXActionIcon. */
@Composable
internal fun MeloXActionIcon(
    token: String,
    modifier: Modifier = Modifier,
    color: Color,
    enabled: Boolean = true,
) {
    val symbol = melox.ui.foundation.meloXActionIconSymbol(token)
    MeloXSymbolIcon(
        symbol = symbol,
        modifier = modifier,
        color = color.copy(alpha = if (enabled) color.alpha else color.alpha * 0.38f),
    )
}
