package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import melox.music.model.MusicSource
import melox.player.AudioPlayer
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

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
            .background(MeloXColors.Background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ──
            PlayerTopBar(
                navState = navState,
                sourceColor = sourceColor,
            )

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
                        fadeIn(animationSpec = tween(200)) + slideInVertically(
                            animationSpec = tween(250),
                            initialOffsetY = { it / 4 },
                        ) togetherWith fadeOut(animationSpec = tween(150))
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
                            onDismiss = { showLyrics = false },
                        )
                        PanelState.Queue -> QueuePanel(
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
private fun PlayerTopBar(
    navState: MeloXNavState?,
    sourceColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { navState?.goBack() }) {
            Text(
                text = "‹",
                color = MeloXColors.TextPrimary,
                fontSize = 24.sp,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "正在播放",
            color = MeloXColors.TextSecondary,
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
    track: melox.music.model.MusicTrack?,
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
        ArtworkPlaceholder(
            sourceColor = sourceColor,
            isPlaying = isPlaying,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Track info ──
        TrackInfoSection(track, sourceColor)

        Spacer(modifier = Modifier.height(24.dp))

        // ── Progress bar ──
        ProgressSection(
            progress = progress,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Playback controls ──
        PlaybackControls(
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Action buttons ──
        ActionButtons(
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
private fun ArtworkPlaceholder(
    sourceColor: Color,
    isPlaying: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "artwork_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "artwork_scale",
    )

    Box(
        modifier = Modifier
            .size((300 * scale).dp)
            .clip(RoundedCornerShape(20.dp))
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
        Text(
            text = "♫",
            fontSize = 72.sp,
            color = sourceColor.copy(alpha = 0.4f),
        )
    }
}

// ── Track info ──

@Composable
private fun TrackInfoSection(
    track: melox.music.model.MusicTrack?,
    sourceColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = track?.title ?: "未播放",
            color = MeloXColors.TextPrimary,
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
            color = MeloXColors.TextSecondary,
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
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
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

// ── Progress section ──

@Composable
private fun ProgressSection(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isSeeking) seekPosition else progress
    val currentPositionMs = (displayProgress * durationMs).toLong()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    ) {
        Slider(
            value = displayProgress,
            onValueChange = { value ->
                isSeeking = true
                seekPosition = value
            },
            onValueChangeFinished = {
                onSeek(seekPosition)
                isSeeking = false
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MeloXColors.Primary,
                activeTrackColor = MeloXColors.Primary,
                inactiveTrackColor = MeloXColors.PlayerProgressBackground,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(currentPositionMs),
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
            )
            Text(
                text = formatTime(durationMs),
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
            )
        }
    }
}

// ── Playback controls ──

@Composable
private fun PlaybackControls(
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
        // Shuffle
        ControlButton(
            icon = "🔀",
            size = 18.sp,
            onClick = {},
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Previous
        ControlButton(
            icon = "⏮",
            size = 20.sp,
            onClick = onPrevious,
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Play/Pause (large)
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MeloXColors.Primary),
        ) {
            Text(
                text = if (isPlaying) "⏸" else "▶",
                color = MeloXColors.OnPrimary,
                fontSize = 28.sp,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Next
        ControlButton(
            icon = "⏭",
            size = 20.sp,
            onClick = onNext,
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Repeat
        ControlButton(
            icon = "🔁",
            size = 18.sp,
            onClick = {},
        )
    }
}

@Composable
private fun ControlButton(
    icon: String,
    size: androidx.compose.ui.unit.TextUnit = 20.sp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .hoverable(interactionSource),
    ) {
        Text(
            text = icon,
            color = if (isHovered) MeloXColors.TextPrimary else MeloXColors.TextSecondary,
            fontSize = size,
        )
    }
}

// ── Action buttons row ──

@Composable
private fun ActionButtons(
    isLiked: Boolean,
    onLikeToggle: () -> Unit,
    onLyricsToggle: () -> Unit,
    onQueueToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionButton(
            icon = if (isLiked) "♥" else "♡",
            label = "喜欢",
            tint = if (isLiked) MeloXColors.Primary else MeloXColors.TextSecondary,
            onClick = onLikeToggle,
        )
        ActionButton(
            icon = "📝",
            label = "歌词",
            tint = MeloXColors.TextSecondary,
            onClick = onLyricsToggle,
        )
        ActionButton(
            icon = "📋",
            label = "队列",
            tint = MeloXColors.TextSecondary,
            onClick = onQueueToggle,
        )
        ActionButton(
            icon = "↗",
            label = "分享",
            tint = MeloXColors.TextSecondary,
            onClick = {},
        )
    }
}

@Composable
private fun ActionButton(
    icon: String,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = icon,
            color = if (isHovered) tint.copy(alpha = 0.8f) else tint,
            fontSize = 20.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = MeloXColors.TextTertiary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 11.sp,
        )
    }
}

// ── Lyrics panel ──

@Composable
private fun LyricsPanel(onDismiss: () -> Unit) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val listState = rememberLazyListState()

    // Mock lyrics lines
    val lyricsLines = remember(currentTrack) {
        listOf(
            "暂无歌词",
            "",
            "请在音乐源中查看歌词",
            "或等待歌词加载...",
            "",
            "♪ ♫ ♬",
        )
    }
    val currentLineIndex = 0

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Text("✕", color = MeloXColors.TextSecondary, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "歌词",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp))
        }

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
                        isCurrent -> MeloXColors.LyricsCurrent
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

// ── Queue panel ──

@Composable
private fun QueuePanel(onDismiss: () -> Unit) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Text("✕", color = MeloXColors.TextSecondary, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "播放队列",
                color = MeloXColors.TextSecondary,
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
                isPlaying = true,
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
                    color = MeloXColors.TextTertiary,
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
                        color = MeloXColors.TextPrimary,
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
    track: melox.music.model.MusicTrack,
    label: String,
    isPlaying: Boolean,
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
                        colors = listOf(sourceColor.copy(alpha = 0.4f), sourceColor.copy(alpha = 0.15f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("♫", color = sourceColor.copy(alpha = 0.6f), fontSize = 18.sp)
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
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistText,
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPlaying) {
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
