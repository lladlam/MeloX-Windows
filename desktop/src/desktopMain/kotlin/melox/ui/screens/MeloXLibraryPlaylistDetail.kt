package melox.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryCache
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistDetail
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.PlaylistCapability
import melox.music.provider.loadAllPlaylistTracks
import melox.network.NeteaseMusicOperationsClient
import melox.network.NeteaseSearchClient
import melox.playback.PlaybackCommands
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.rememberMeloXLiquidInteraction
import melox.ui.theme.MeloXColors
import java.io.IOException

private enum class MeloXPlaylistSortMode { Original, Title, Artist, Album }

/**
 * Canonical playlist detail used by Library, Home, Explore, Search and account
 * entry points — exact port of Android MeloXPlaylistDetailScreen
 * (LibraryScreen.kt lines 1536-2012, provider-playlist branch only: desktop
 * has no NetEase album/NetEase-native detail entry from Library).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXLibraryPlaylistDetailScreen(
    initialPlaylist: NeteasePlaylistSummary,
    client: NeteaseLibraryClient,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSongLikeChanged: (SearchSong, Boolean) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val cache = remember { NeteaseLibraryCache() }
    val accountClient = remember {
        NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() })
    }
    val operationsClient = remember {
        NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() })
    }
    val providerPlaylist = initialPlaylist.providerPlaylist
    val providerPlaylistCapability = remember(providerPlaylist?.id?.source) {
        providerPlaylist?.let { backing ->
            MeloXMusicProviders.create().require(backing.id.source) as? PlaylistCapability
        }
    }
    val isProviderPlaylist = providerPlaylist != null
    var detail by remember(initialPlaylist.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }
    var loading by remember(initialPlaylist.id) { mutableStateOf(true) }
    var errorMessage by remember(initialPlaylist.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(initialPlaylist.id) { mutableStateOf("") }
    var isSaved by remember(initialPlaylist.id) { mutableStateOf<Boolean?>(null) }
    var currentUserId by remember(initialPlaylist.id) { mutableStateOf<Long?>(null) }
    var savingPlaylist by remember(initialPlaylist.id) { mutableStateOf(false) }
    var palette by remember(initialPlaylist.coverUrl) {
        mutableStateOf(MeloXDetailPalette.LightFallback)
    }
    var sortMode by remember(initialPlaylist.id) { mutableStateOf(MeloXPlaylistSortMode.Original) }

    suspend fun refreshSavedState() {
        if (isProviderPlaylist) {
            isSaved = null
            return
        }
        val cookie = NeteaseSessionStore.readCookie()
        if (!NeteaseSessionStore.containsMusicU(cookie)) {
            isSaved = null
            return
        }
        runCatching {
            val profile = accountClient.accountProfile(cookie)
            currentUserId = profile.userId
            withContext(Dispatchers.IO) {
                client.userPlaylistsBlocking(profile.userId)
            }.any { it.id == initialPlaylist.id }
        }.onSuccess { isSaved = it }
    }

    suspend fun refreshPlaylist() {
        loading = true
        errorMessage = null
        if (providerPlaylist != null) {
            val capability = providerPlaylistCapability
            if (capability == null) {
                errorMessage = "${providerPlaylist.id.source.displayName} 当前不提供歌单详情能力"
                loading = false
                return
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    capability.loadAllPlaylistTracks(providerPlaylist, pageSize = 200)
                }
            }.onSuccess { providerDetail ->
                detail = MeloXLegacyUiBridge.playlistDetail(providerDetail)
            }.onFailure { errorMessage = it.message ?: "歌单加载失败" }
        } else {
            runCatching { client.playlistDetail(initialPlaylist.id) }
                .onSuccess {
                    detail = it
                    cache.savePlaylistDetail(initialPlaylist.id, it)
                }
                .onFailure { failure ->
                    errorMessage = if (failure is IOException) {
                        failure.message ?: "网络连接失败，请检查网络后重试"
                    } else {
                        failure.message ?: "歌单加载失败"
                    }
                }
        }
        loading = false
    }

    LaunchedEffect(initialPlaylist.id, providerPlaylist?.id) {
        if (providerPlaylist != null) {
            refreshPlaylist()
        } else {
            cache.loadPlaylistDetail(initialPlaylist.id)?.let { detail = it }
            loading = detail == null
            if (NeteaseLibraryCache.beginPlaylistColdStartRefresh(initialPlaylist.id)) {
                refreshPlaylist()
            }
        }
    }

    LaunchedEffect(initialPlaylist.id, isProviderPlaylist) {
        refreshSavedState()
    }

    val displayed = detail?.summary ?: initialPlaylist
    val songs = detail?.songs.orEmpty()
    val ownedPlaylistId = displayed.id.takeIf {
        !isProviderPlaylist &&
            displayed.creatorUserId != null && displayed.creatorUserId == currentUserId
    }

    LaunchedEffect(displayed.coverUrl) {
        palette = MeloXDetailPaletteProvider.paletteFor(displayed.coverUrl)
    }

    val foreground = if (palette.prefersDarkAppearance) Color.White else Color.Black
    val secondary = foreground.copy(alpha = 0.48f)
    val orderedSongs = remember(songs, sortMode) {
        when (sortMode) {
            MeloXPlaylistSortMode.Original -> songs
            MeloXPlaylistSortMode.Title -> songs.sortedBy { it.name.lowercase() }
            MeloXPlaylistSortMode.Artist -> songs.sortedWith(compareBy({ it.artists.lowercase() }, { it.name.lowercase() }))
            MeloXPlaylistSortMode.Album -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.name.lowercase() }))
        }
    }
    val filteredSongs = remember(orderedSongs, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) orderedSongs else orderedSongs.filter { song ->
            song.name.lowercase().contains(query) ||
                song.artists.lowercase().contains(query) ||
                song.album.lowercase().contains(query)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            MeloXLibraryPlaylistToolbar(
                foreground = foreground,
                onBack = onBack,
                onShare = {},
                showMore = false,
                onMore = {},
            )
            MeloXLibraryPlaylistSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                foreground = foreground,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = MeloXBottomContentClearanceDp),
            ) {
                item {
                    MeloXStandardPlaylistHero(
                        playlist = displayed,
                        tracks = filteredSongs,
                        foreground = foreground,
                        secondary = secondary,
                        sourceLabel = displayed.providerPlaylist?.id?.source?.displayName ?: "网易云音乐",
                        onPlay = {
                            filteredSongs.firstOrNull()?.let { first ->
                                PlaybackCommands.playQueue(
                                    songs = filteredSongs,
                                    selectedSongId = first.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            }
                        },
                        onShuffle = {
                            val shuffled = filteredSongs.shuffled()
                            shuffled.firstOrNull()?.let { first ->
                                PlaybackCommands.playQueue(
                                    songs = shuffled,
                                    selectedSongId = first.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            }
                        },
                        isSaved = isSaved == true,
                        showSaveAction = !isProviderPlaylist,
                        onToggleSaved = {
                            if (!isProviderPlaylist && !savingPlaylist) {
                                val desired = isSaved != true
                                savingPlaylist = true
                                scope.launch {
                                    runCatching {
                                        operationsClient.setPlaylistSubscribed(displayed.id, desired)
                                    }.onSuccess {
                                        isSaved = desired
                                    }.onFailure {
                                        errorMessage = it.message ?: "歌单收藏操作失败"
                                    }
                                    savingPlaylist = false
                                }
                            }
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                when {
                    loading && songs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = foreground)
                        }
                    }
                    errorMessage != null && songs.isEmpty() -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                errorMessage.orEmpty(),
                                color = secondary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "重试",
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .clickable {
                                        scope.launch {
                                            refreshPlaylist()
                                        }
                                    }
                                    .padding(8.dp),
                                color = foreground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    filteredSongs.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("暂无歌曲", color = secondary)
                        }
                    }
                    else -> itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.id },
                    ) { index, song ->
                        MeloXPlaylistTrackRow(
                            song = song,
                            index = index,
                            foreground = foreground,
                            onClick = {
                                PlaybackCommands.playQueue(
                                    songs = filteredSongs,
                                    selectedSongId = song.id,
                                    onFailure = { errorMessage = it.message ?: "播放失败" },
                                )
                            },
                            onPlayNext = { PlaybackCommands.playNext(song) },
                            onPlayLast = { PlaybackCommands.addToQueue(song) },
                            endAction = if (ownedPlaylistId != null) {
                                MeloXSwipeAction("从歌单移除", melox.ui.foundation.MeloXSymbol.Trash, Color(0xFFFF3B30)) {
                                    scope.launch {
                                        runCatching { operationsClient.removeSongFromPlaylist(song.id, ownedPlaylistId) }
                                            .onSuccess { refreshPlaylist() }
                                            .onFailure { errorMessage = it.message ?: "移除歌曲失败" }
                                    }
                                }
                            } else {
                                MeloXSwipeAction("添加到资料库", melox.ui.foundation.MeloXSymbol.Heart, Color(0xFFFF3B30)) {
                                    scope.launch {
                                        runCatching { operationsClient.setSongLiked(song.id, true) }
                                            .onSuccess {
                                                currentUserId?.let { userId ->
                                                    scope.launch {
                                                        cache.loadSnapshot(userId)?.let { cached ->
                                                            cache.saveSnapshot(userId, cached.withSongLiked(song, true))
                                                        }
                                                    }
                                                }
                                                onSongLikeChanged(song, true)
                                            }
                                            .onFailure { errorMessage = it.message ?: "添加到资料库失败" }
                                    }
                                }
                            },
                        )
                        if (song.id != filteredSongs.lastOrNull()?.id) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 66.dp, end = 20.dp),
                                thickness = 0.6.dp,
                                color = foreground.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXLibraryPlaylistToolbar(
    foreground: Color,
    onBack: () -> Unit,
    onShare: () -> Unit,
    showMore: Boolean = true,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MeloXLibraryGlassCircleButton(
            foreground = foreground,
            size = 44.dp,
            onClick = onBack,
        ) {
            MeloXBackGlyph(Modifier.size(22.dp), foreground)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeloXLibraryGlassCircleButton(
                foreground = foreground,
                size = 44.dp,
                onClick = onShare,
            ) {
                MeloXShareGlyph(Modifier.size(22.dp), MeloXRed)
            }
            if (showMore) {
                MeloXLibraryGlassCircleButton(
                    foreground = foreground,
                    size = 44.dp,
                    onClick = onMore,
                ) {
                    Text(
                        "•••",
                        color = MeloXRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeloXLibraryPlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(meloXGlassColor(foreground)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSearchGlyph(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(20.dp),
            color = foreground,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    "在歌单中搜索",
                    color = foreground.copy(alpha = 0.46f),
                    fontSize = 17.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = foreground,
                    fontSize = 17.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeloXStandardPlaylistHero(
    playlist: NeteasePlaylistSummary,
    tracks: List<SearchSong>,
    foreground: Color,
    secondary: Color,
    sourceLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isSaved: Boolean,
    showSaveAction: Boolean,
    onToggleSaved: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * 0.68f, 300.dp)
        var descriptionExpanded by remember(playlist.id) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sharedArtworkModifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(
                        key = meloXPlaylistArtworkSharedKey(playlist.id),
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    renderInOverlayDuringTransition = true,
                    zIndexInOverlay = 1f,
                )
            }

            MeloXArtworkImage(
                url = playlist.coverUrl,
                fallbackColor = MeloXColors.Surface,
                modifier = sharedArtworkModifier
                    .size(artworkSize)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    )
                    .clip(RoundedCornerShape(12.dp)),
            )

            Text(
                text = playlist.name,
                modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp),
                color = foreground,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = playlist.creatorName.ifBlank { sourceLabel },
                modifier = Modifier.padding(top = 8.dp),
                color = foreground,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = buildString {
                    append("${if (playlist.trackCount > 0) playlist.trackCount else tracks.size} 首歌曲")
                    if (playlist.playCount > 0) append(" · ${meloXCompactPlayCount(playlist.playCount)} 次播放")
                },
                modifier = Modifier.padding(top = 7.dp),
                color = secondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )

            Row(
                modifier = Modifier.padding(top = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MeloXLibraryGlassCircleButton(
                    foreground = foreground,
                    size = 54.dp,
                    enabled = tracks.isNotEmpty(),
                    onClick = onShuffle,
                ) {
                    MeloXShuffleGlyph(Modifier.size(26.dp), foreground)
                }

                val playInteraction = rememberMeloXLiquidInteraction()
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(50.dp)
                        .meloXLiquidButton(
                            shape = RoundedCornerShape(25.dp),
                            enabled = tracks.isNotEmpty(),
                            tint = if (foreground == Color.White) Color.White else Color.Black,
                            surfaceColor = if (foreground == Color.White) {
                                Color.White.copy(alpha = 0.82f)
                            } else {
                                Color.Black.copy(alpha = 0.82f)
                            },
                            lensRadius = 12.dp,
                            refractionHeight = 20.dp,
                            interaction = playInteraction,
                        )
                        .clickable(enabled = tracks.isNotEmpty(), onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        MeloXPlayGlyph(
                            Modifier.size(19.dp),
                            if (foreground == Color.White) Color.Black else Color.White,
                        )
                        Text(
                            "播放",
                            color = if (foreground == Color.White) Color.Black else Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (showSaveAction) {
                    MeloXLibraryGlassCircleButton(
                        foreground = foreground,
                        size = 54.dp,
                        onClick = onToggleSaved,
                    ) {
                        Text(
                            if (isSaved) "✓" else "+",
                            color = foreground,
                            fontSize = if (isSaved) 24.sp else 34.sp,
                            lineHeight = 34.sp,
                            fontWeight = if (isSaved) FontWeight.SemiBold else FontWeight.Light,
                        )
                    }
                }
            }

            playlist.description
                ?.takeUnless { description ->
                    description.isBlank() || description.equals("null", ignoreCase = true)
                }
                ?.let { description ->
                    Text(
                        text = description,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
                            .clickable { descriptionExpanded = !descriptionExpanded },
                        color = secondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (descriptionExpanded) 12 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun MeloXPlaylistTrackRow(
    song: SearchSong,
    index: Int,
    foreground: Color,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayLast: () -> Unit,
    endAction: MeloXSwipeAction?,
) {
    MeloXSwipeActionRow(
        startActions = listOf(
            MeloXSwipeAction("下一首播放", melox.ui.foundation.MeloXSymbol.NextTrack, Color(0xFF8E5AF7), onPlayNext),
            MeloXSwipeAction("稍后播放", melox.ui.foundation.MeloXSymbol.Queue, Color(0xFFFF9F0A), onPlayLast),
        ),
        endActions = listOfNotNull(endAction),
        onClick = onClick,
        onLongClick = null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.width(40.dp),
                    color = foreground.copy(alpha = 0.48f),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(
                    text = song.name,
                    modifier = Modifier.weight(1f),
                    color = foreground,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MeloXLibraryGlassCircleButton(
    foreground: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = rememberMeloXLiquidInteraction()
    Box(
        modifier = Modifier
            .size(size)
            .meloXLiquidButton(
                shape = CircleShape,
                enabled = enabled,
                surfaceColor = meloXGlassColor(foreground).copy(alpha = 0.48f),
                lensRadius = 11.dp,
                refractionHeight = 18.dp,
                interaction = interaction,
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
