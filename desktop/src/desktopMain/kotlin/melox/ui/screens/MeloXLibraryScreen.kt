package melox.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryCache
import melox.library.NeteaseLibraryClient
import melox.library.NeteaseLibrarySnapshot
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicSource
import melox.playback.PlaybackCommands
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
) {
    val scope = rememberCoroutineScope()
    val session = remember { NeteaseSessionStore() }
    val client = remember {
        NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie() })
    }
    val cache = remember { NeteaseLibraryCache() }

    var selectedPage by rememberSaveable { mutableStateOf(MeloXLibraryPage.Songs) }
    var selectedPlaylist by remember(session.cookie) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var snapshot by remember(session.cookie) { mutableStateOf<NeteaseLibrarySnapshot?>(null) }
    var loading by remember(session.cookie) { mutableStateOf(false) }
    var errorMessage by remember(session.cookie) { mutableStateOf<String?>(null) }
    val playlistListState = rememberLazyListState()

    suspend fun refreshLibrary() {
        loading = true
        errorMessage = null
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
        runCatching { client.snapshot(userId) }
            .onSuccess {
                snapshot = it
                cache.saveSnapshot(userId, it)
            }
            .onFailure { errorMessage = it.message ?: "音乐库加载失败" }
        loading = false
    }

    LaunchedEffect(session.cookie, session.profile?.userId) {
        val userId = session.profile?.userId ?: return@LaunchedEffect
        cache.loadSnapshot(userId)?.let { snapshot = it }
        if (NeteaseLibraryCache.beginLibraryColdStartRefresh(userId)) {
            refreshLibrary()
        }
    }

    LaunchedEffect(session.cookie) {
        if (session.isLoggedIn && session.profile == null) {
            session.refreshProfile()
            if (session.profile != null) refreshLibrary()
        }
        if (!session.isLoggedIn) loading = false
    }

    if (!session.isLoggedIn) {
        MeloXLibraryLoginUnavailable(
            onLogin = { navState?.navigateTo(melox.ui.navigation.Route.Login) },
            sourceName = "网易云音乐",
        )
        return
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedScope = this

        AnimatedContent(
            targetState = selectedPlaylist,
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
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MeloXColors.Background)
                        .statusBarsPadding(),
                ) {
                    MeloXIosTopBar(
                        title = "音乐库",
                    )

                    MeloXLibrarySegmentedPicker(
                        selected = selectedPage,
                        onSelected = { selectedPage = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )

                    if (errorMessage != null && snapshot == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MeloXColors.OnBackground.copy(alpha = 0.55f),
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
                                CloudScreen(navState = navState ?: remember { MeloXNavState() })
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
private fun MeloXLibraryPage.isEnabled(source: MusicSource): Boolean = when {
    source == MusicSource.Bilibili -> this == MeloXLibraryPage.Playlists || this == MeloXLibraryPage.Downloads
    source == MusicSource.Local -> this == MeloXLibraryPage.Songs
    source != MusicSource.Netease -> this == MeloXLibraryPage.Playlists
    else -> true
}

@Composable
private fun MeloXLibraryLoginUnavailable(
    onLogin: (() -> Unit)?,
    sourceName: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background)
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
                    color = MeloXColors.OnBackground.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
                if (onLogin != null) {
                    val interaction = rememberMeloXLiquidInteraction()
                    Box(
                        modifier = Modifier
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
                            "登录网易云音乐",
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
    modifier: Modifier = Modifier,
) {
    val pages = MeloXLibraryPage.entries.filter { it.isEnabled(MusicSource.Netease) }
    val panelShape = MeloXShapes.compact
    val lensShape = RoundedCornerShape(15.dp)
    val dark = true // MeloX is always dark on desktop
    val selectedIndex = pages.indexOf(selected).coerceAtLeast(0)
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
    val panelSurface = MeloXColors.OnBackground.copy(alpha = 0.055f)
    val selectionTint = if (dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

    BoxWithConstraints(
        modifier = modifier
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
                    Text(page.title, fontSize = 13.sp)
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
                val isSelected = page == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(page) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = page.title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MeloXColors.OnBackground,
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
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无歌曲",
                color = MeloXColors.OnBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearanceDp),
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
                    Text("心动模式", fontSize = 17.sp, color = MeloXColors.OnBackground)
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
private fun MeloXPlayAllRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
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
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = MeloXColors.OnBackground,
        )
    }
}

@Composable
private fun MeloXLibraryTrackRow(
    song: SearchSong,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeloXArtworkImage(
            url = song.artworkUrl,
            fallbackColor = MeloXColors.Surface,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = song.name,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MeloXColors.OnBackground,
            )
            Text(
                text = song.artists,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MeloXColors.OnBackground.copy(alpha = 0.46f),
            )
        }
        if (song.durationMs > 0L) {
            Text(
                text = meloXFormatDuration(song.durationMs),
                fontSize = 13.sp,
                color = MeloXColors.OnBackground.copy(alpha = 0.46f),
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
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有收藏歌单",
                color = MeloXColors.OnBackground.copy(alpha = 0.48f),
                fontSize = 17.sp,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MeloXBottomContentClearanceDp),
    ) {
        item {
            Text(
                text = "歌单",
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
                fontSize = 13.sp,
                color = MeloXColors.OnBackground.copy(alpha = 0.50f),
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        playlist.name,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} 首歌曲",
                        fontSize = 12.sp,
                        color = MeloXColors.OnBackground.copy(alpha = 0.48f),
                    )
                }
                MeloXActionIcon(
                    "›",
                    Modifier.size(18.dp),
                    MeloXColors.OnBackground.copy(alpha = 0.40f),
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
        color = MeloXColors.OnBackground.copy(alpha = 0.12f),
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
    val symbol = when (token) {
        "◷" -> melox.ui.foundation.MeloXSymbol.Clock
        "+", "＋" -> melox.ui.foundation.MeloXSymbol.Plus
        "↓×" -> melox.ui.foundation.MeloXSymbol.Download
        "↗" -> melox.ui.foundation.MeloXSymbol.Share
        "✉" -> melox.ui.foundation.MeloXSymbol.Message
        "◎", "◌" -> melox.ui.foundation.MeloXSymbol.Message
        "i", "#", "ⓘ" -> melox.ui.foundation.MeloXSymbol.Info
        "♡", "♥" -> melox.ui.foundation.MeloXSymbol.Heart
        "▣", "⇥", "♬" -> melox.ui.foundation.MeloXSymbol.ListBullet
        "✓" -> melox.ui.foundation.MeloXSymbol.Checkmark
        "○" -> melox.ui.foundation.MeloXSymbol.Ellipsis
        "♫", "♪" -> melox.ui.foundation.MeloXSymbol.MusicNote
        "✦" -> melox.ui.foundation.MeloXSymbol.Sparkles
        "❞" -> melox.ui.foundation.MeloXSymbol.Quote
        "▱" -> melox.ui.foundation.MeloXSymbol.Rotate
        "▤" -> melox.ui.foundation.MeloXSymbol.Pip
        "☷", "▦", "▥" -> melox.ui.foundation.MeloXSymbol.Grid
        "⌁" -> melox.ui.foundation.MeloXSymbol.Mic
        "▰" -> melox.ui.foundation.MeloXSymbol.Drive
        "⚙" -> melox.ui.foundation.MeloXSymbol.Settings
        "⌘" -> melox.ui.foundation.MeloXSymbol.Bug
        "↑" -> melox.ui.foundation.MeloXSymbol.Upload
        "↓" -> melox.ui.foundation.MeloXSymbol.Download
        "‹" -> melox.ui.foundation.MeloXSymbol.ChevronLeft
        "›" -> melox.ui.foundation.MeloXSymbol.ChevronRight
        "•••", "…" -> melox.ui.foundation.MeloXSymbol.Ellipsis
        "×" -> melox.ui.foundation.MeloXSymbol.XMark
        "↻" -> melox.ui.foundation.MeloXSymbol.Refresh
        else -> melox.ui.foundation.MeloXSymbol.Question
    }
    MeloXSymbolIcon(
        symbol = symbol,
        modifier = modifier,
        color = color.copy(alpha = if (enabled) color.alpha else color.alpha * 0.38f),
    )
}
