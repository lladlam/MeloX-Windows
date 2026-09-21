package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicSource
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.glassSurface
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

@Composable
fun MiniPlayer(navState: MeloXNavState? = null) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val playerState by AudioPlayer.state.collectAsState()
    val progress by AudioPlayer.progress.collectAsState()

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(MeloXMotion.PageEnterMillis, easing = FastOutSlowInEasing),
        ) + fadeIn(animationSpec = tween(MeloXMotion.IconEnterMillis)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(MeloXMotion.PageExitMillis, easing = FastOutSlowInEasing),
        ) + fadeOut(animationSpec = tween(MeloXMotion.IconExitMillis)),
    ) {
        val track = currentTrack ?: return@AnimatedVisibility
        val sourceColor = remember(track) {
            val key = when (track.id.source) {
                MusicSource.QQMusic -> "qq"
                MusicSource.AppleMusic -> "applemusic"
                MusicSource.YouTubeMusic -> "youtubemusic"
                else -> track.id.source.storageValue
            }
            MeloXColors.sourceColors[key] ?: MeloXColors.Primary
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = MeloXMotion.interactivePress,
            label = "mini_press",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .glassSurface(
                    shape = RoundedCornerShape(28.dp),
                    backgroundColor = MeloXColors.MiniPlayerSurface,
                    borderAlpha = 0.06f,
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                    navState?.navigateTo(Route.Player)
                },
        ) {
            Column {
                // ── Progress line at top ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MeloXColors.PlayerProgressBg),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .background(MeloXColors.PlayerProgressFill),
                    )
                }

                // ── Bar content ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Left: artwork ──
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        sourceColor.copy(alpha = 0.5f),
                                        sourceColor.copy(alpha = 0.2f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.MusicNote,
                            color = sourceColor.copy(alpha = 0.7f),
                            size = 16,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // ── Center: title + artist ──
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    ) {
                        Text(
                            text = track.title,
                            color = MeloXColors.OnSurface,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artistText,
                            color = MeloXColors.OnSurfaceVariant,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // ── Right: controls ──
                    MiniPlayerButton(
                        onClick = {
                            if (playerState.isPlaying) AudioPlayer.pause() else AudioPlayer.resume()
                        },
                    ) {
                        MeloXSymbolIcon(
                            symbol = if (playerState.isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play,
                            color = MeloXColors.OnSurface,
                            size = 18,
                        )
                    }

                    MiniPlayerButton(
                        onClick = {
                            AudioPlayer.seekTo(AudioPlayer.getDurationMs())
                        },
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.NextTrack,
                            color = MeloXColors.OnSurface,
                            size = 18,
                        )
                    }

                    // ── Dancing bars when playing ──
                    if (playerState.isPlaying) {
                        Spacer(modifier = Modifier.width(4.dp))
                        DancingBars()
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 620f,
        ),
        label = "mini_btn_press",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        interactionSource = interactionSource,
    ) {
        content()
    }
}

@Composable
private fun DancingBars() {
    val infiniteTransition = rememberInfiniteTransition(label = "dancing_bars")
    val barSpecs = listOf(0f, 0.7f, 0.3f, 1f)

    Row(
        modifier = Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        barSpecs.forEachIndexed { index, target ->
            val anim by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = target,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + index * 80,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar_$index",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + anim * 14).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MeloXColors.Primary),
            )
        }
    }
}
