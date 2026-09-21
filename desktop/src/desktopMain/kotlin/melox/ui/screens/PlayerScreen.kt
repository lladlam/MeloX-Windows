package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXGlass
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.glassSurface
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import melox.ui.theme.MeloXTypography

// ── Helpers ──

private fun sourceColorFor(source: MusicSource): Color {
    val key = when (source) {
        MusicSource.QQMusic -> "qq"
        MusicSource.AppleMusic -> "applemusic"
        MusicSource.YouTubeMusic -> "youtubemusic"
        else -> source.storageValue
    }
    return MeloXColors.sourceColors[key] ?: MeloXColors.Primary
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ── Mock queue ──

private val mockQueue = mutableStateListOf<String>()

// ── Top-level composable ──

@Composable
fun PlayerScreen(navState: MeloXNavState? = null) {
    val playerState by AudioPlayer.state.collectAsState()
    val progress by AudioPlayer.progress.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }

    val sourceColor = remember(currentTrack) {
        currentTrack?.id?.source?.let { sourceColorFor(it) } ?: MeloXColors.Primary
    }

    val track = currentTrack
    val durationMs = remember(track) {
        track?.durationMs ?: AudioPlayer.getDurationMs()
    }

    // Periodic duration refresh
    LaunchedEffect(track) {
        while (isActive && track != null) {
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.PlayerBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ──
            PlayerTopBar(navState = navState)

            // ── Main content ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = when {
                        showQueue -> PanelState.Queue
                        showLyrics -> PanelState.Lyrics
                        else -> PanelState.Main
                    },
                    transitionSpec = {
                        fadeIn(animationSpec = tween(MeloXMotion.ContentEnterMillis)) +
                            slideInVertically(
                                animationSpec = tween(MeloXMotion.ContentEnterMillis),
                                initialOffsetY = { it / 4 },
                            ) togetherWith fadeOut(animationSpec = tween(MeloXMotion.ContentExitMillis))
                    },
                    label = "panel_transition",
                ) { panel ->
                    when (panel) {
                        PanelState.Main -> PlayerMainContent(
                            track = track,
                            sourceColor = sourceColor,
                            progress = progress,
                            durationMs = durationMs,
                            isPlaying = playerState.isPlaying,
                            isLiked = isLiked,
                            onPlayPause = {
                                if (playerState.isPlaying) AudioPlayer.pause() else AudioPlayer.resume()
                            },
                            onSeek = { fraction ->
                                AudioPlayer.seekTo((fraction * durationMs).toLong())
                            },
                            onPrevious = { AudioPlayer.seekTo(0) },
                            onNext = { AudioPlayer.seekTo(durationMs) },
                            onLikeToggle = { isLiked = !isLiked },
                            onLyricsToggle = {
                                showLyrics = true
                                showQueue = false
                            },
                            onQueueToggle = {
                                showQueue = true
                                showLyrics = false
                            },
                        )
                        PanelState.Lyrics -> LyricsPanel(
                            sourceColor = sourceColor,
                            onDismiss = { showLyrics = false },
                        )
                        PanelState.Queue -> QueuePanel(
                            sourceColor = sourceColor,
                            onDismiss = { showQueue = false },
                        )
                    }
                }
            }
        }
    }
}

private enum class PanelState { Main, Lyrics, Queue }

// ── Top bar ──

@Composable
private fun PlayerTopBar(navState: MeloXNavState?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { navState?.goBack() }) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.ChevronLeft,
                color = MeloXColors.OnSurface,
                size = 24,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "正在播放",
            color = MeloXColors.PlayerTextDim,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(48.dp))
    }
}

// ── Main player content ──

@Composable
private fun PlayerMainContent(
    track: MusicTrack?,
    sourceColor: Color,
    progress: Float,
    durationMs: Long,
    isPlaying: Boolean,
    isLiked: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLikeToggle: () -> Unit,
    onLyricsToggle: () -> Unit,
    onQueueToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Artwork ──
        PlayerArtwork(
            sourceColor = sourceColor,
            isPlaying = isPlaying,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Track info ──
        PlayerTrackInfo(track = track, sourceColor = sourceColor)

        Spacer(modifier = Modifier.height(24.dp))

        // ── Progress bar ──
        PlayerProgress(
            progress = progress,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Playback controls ──
        PlayerTransport(
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Action buttons ──
        PlayerActions(
            isLiked = isLiked,
            onLikeToggle = onLikeToggle,
            onLyricsToggle = onLyricsToggle,
            onQueueToggle = onQueueToggle,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Artwork ──

@Composable
private fun PlayerArtwork(sourceColor: Color, isPlaying: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.74f,
        animationSpec = MeloXMotion.playerPlaybackScale,
        label = "artwork_scale",
    )

    val shadowDp by animateFloatAsState(
        targetValue = if (isPlaying) 26f else 14f,
        animationSpec = MeloXMotion.playerArtworkShadow,
        label = "artwork_shadow",
    )

    Box(
        modifier = Modifier
            .size((300 * scale).dp)
            .shadow(
                elevation = shadowDp.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = sourceColor.copy(alpha = 0.3f),
                spotColor = sourceColor.copy(alpha = 0.3f),
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        sourceColor.copy(alpha = 0.6f),
                        sourceColor.copy(alpha = 0.2f),
                        MeloXColors.SurfaceVariant,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.MusicNote,
            color = sourceColor.copy(alpha = 0.4f),
            size = 72,
        )
    }
}

// ── Track info ──

@Composable
private fun PlayerTrackInfo(track: MusicTrack?, sourceColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = track?.title ?: "未播放",
            color = MeloXColors.OnSurface,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track?.artistText ?: "选择一首歌曲",
            color = MeloXColors.OnSurfaceVariant,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val album = track?.album
        if (album != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.name,
                color = MeloXColors.PlayerTextDim,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Source badge chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(sourceColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = track?.id?.source?.displayName ?: "",
                color = sourceColor,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Progress bar ──

@Composable
private fun PlayerProgress(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekPosition else progress
    val currentPositionMs = (displayProgress * durationMs).toLong()
    val trackHeight = if (isSeeking) 6.dp else 4.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(MeloXColors.PlayerProgressBg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    // Allow tap to seek — simplified
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = displayProgress)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(MeloXColors.PlayerProgressFill),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(currentPositionMs),
                color = MeloXColors.PlayerTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Text(
                text = formatTime(durationMs),
                color = MeloXColors.PlayerTextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

// ── Transport controls ──

@Composable
private fun PlayerTransport(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Previous
        TransportButton(
            size = 64.dp,
            iconSize = 34.dp,
            onClick = onPrevious,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.PreviousTrack,
                color = MeloXColors.OnSurface,
                size = 34,
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Play/Pause (large)
        TransportButton(
            size = 64.dp,
            iconSize = 48.dp,
            onClick = onPlayPause,
            background = MeloXColors.OnSurface,
        ) {
            MeloXSymbolIcon(
                symbol = if (isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play,
                color = MeloXColors.Background,
                size = 48,
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Next
        TransportButton(
            size = 64.dp,
            iconSize = 34.dp,
            onClick = onNext,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.NextTrack,
                color = MeloXColors.OnSurface,
                size = 34,
            )
        }
    }
}

@Composable
private fun TransportButton(
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "press_scale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        interactionSource = interactionSource,
    ) {
        content()
    }
}

// ── Action buttons ──

@Composable
private fun PlayerActions(
    isLiked: Boolean,
    onLikeToggle: () -> Unit,
    onLyricsToggle: () -> Unit,
    onQueueToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PlayerActionButton(
            icon = if (isLiked) MeloXSymbol.HeartFill else MeloXSymbol.Heart,
            tint = if (isLiked) MeloXColors.Primary else MeloXColors.OnSurfaceVariant,
            onClick = onLikeToggle,
        )
        PlayerActionButton(
            icon = MeloXSymbol.Lyrics,
            tint = MeloXColors.OnSurfaceVariant,
            onClick = onLyricsToggle,
        )
        PlayerActionButton(
            icon = MeloXSymbol.Queue,
            tint = MeloXColors.OnSurfaceVariant,
            onClick = onQueueToggle,
        )
        PlayerActionButton(
            icon = MeloXSymbol.Share,
            tint = MeloXColors.OnSurfaceVariant,
            onClick = {},
        )
    }
}

@Composable
private fun PlayerActionButton(
    icon: MeloXSymbol,
    tint: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "action_press_scale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        interactionSource = interactionSource,
    ) {
        MeloXSymbolIcon(symbol = icon, color = tint, size = 22)
    }
}

// ── Lyrics panel ──

@Composable
private fun LyricsPanel(
    sourceColor: Color,
    onDismiss: () -> Unit,
) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val listState = rememberLazyListState()

    val lyricsLines = remember(currentTrack) {
        listOf(
            "暂无歌词",
            "",
            "请在音乐源中查看歌词",
            "或等待歌词加载...",
            "",
        )
    }
    val currentLineIndex = 0

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred artwork background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            sourceColor.copy(alpha = 0.25f),
                            MeloXColors.PlayerBackground,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.XMark,
                        color = MeloXColors.OnSurfaceVariant,
                        size = 18,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "歌词",
                    color = MeloXColors.OnSurfaceVariant,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Lyrics list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentPadding = PaddingValues(vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(lyricsLines) { index, line ->
                    val isCurrent = index == currentLineIndex
                    val isPast = index < currentLineIndex

                    Text(
                        text = line.ifBlank { " " },
                        color = when {
                            isCurrent -> MeloXColors.OnSurface
                            isPast -> MeloXColors.LyricsPast
                            else -> MeloXColors.LyricsFuture
                        },
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = if (isCurrent) 20.sp else 16.sp,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ── Queue panel ──

@Composable
private fun QueuePanel(
    sourceColor: Color,
    onDismiss: () -> Unit,
) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.XMark,
                    color = MeloXColors.OnSurfaceVariant,
                    size = 18,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "播放队列",
                color = MeloXColors.OnSurfaceVariant,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

        // Current track
        currentTrack?.let { track ->
            QueueTrackItem(
                track = track,
                label = "正在播放",
                isCurrent = true,
            )
        }

        // Upcoming
        if (mockQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "队列为空",
                    color = MeloXColors.PlayerTextDim,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(mockQueue) { index, trackTitle ->
                    Text(
                        text = trackTitle,
                        color = MeloXColors.OnSurface,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueTrackItem(
    track: MusicTrack,
    label: String,
    isCurrent: Boolean,
) {
    val sourceColor = sourceColorFor(track.id.source)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(sourceColor.copy(alpha = 0.08f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            sourceColor.copy(alpha = 0.4f),
                            sourceColor.copy(alpha = 0.15f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                color = sourceColor.copy(alpha = 0.6f),
                size = 18,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = sourceColor,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = track.title,
                color = MeloXColors.OnSurface,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistText,
                color = MeloXColors.OnSurfaceVariant,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            NowPlayingIndicator()
        }
    }
}

@Composable
private fun NowPlayingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "now_playing")
    val animValues = listOf(0f, 1f, 0.5f, 0.8f, 0.3f).map { target ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = target,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar_$target",
        )
    }

    Row(
        modifier = Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        animValues.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((8 + anim.value * 8).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MeloXColors.Primary),
            )
        }
    }
}
