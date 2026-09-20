package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.player.AudioPlayer
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
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        ) + fadeOut(animationSpec = tween(200)),
    ) {
        val track = currentTrack ?: return@AnimatedVisibility
        val sourceColor = remember(track) {
            val key = when (track.id.source) {
                melox.music.model.MusicSource.QQMusic -> "qq"
                melox.music.model.MusicSource.AppleMusic -> "applemusic"
                melox.music.model.MusicSource.YouTubeMusic -> "youtubemusic"
                else -> track.id.source.storageValue
            }
            MeloXColors.sourceColors[key] ?: MeloXColors.Primary
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MeloXColors.MiniPlayerBackground),
        ) {
            Column {
                // ── Progress line at top ──
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MeloXColors.PlayerProgress,
                    trackColor = MeloXColors.PlayerProgressBackground,
                )

                // ── Bar content ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .hoverable(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                        navState?.navigateTo(Route.Player)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Left: artwork placeholder ──
                    Spacer(modifier = Modifier.width(12.dp))
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
                        Text(
                            text = "♫",
                            color = sourceColor.copy(alpha = 0.7f),
                            fontSize = 16.sp,
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
                            color = MeloXColors.TextPrimary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artistText,
                            color = MeloXColors.TextTertiary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // ── Right: controls ──
                    MiniPlayerControlButton(
                        icon = if (playerState.isPlaying) "⏸" else "▶",
                        onClick = {
                            if (playerState.isPlaying) AudioPlayer.pause() else AudioPlayer.resume()
                        },
                    )
                    MiniPlayerControlButton(
                        icon = "⏭",
                        onClick = {
                            AudioPlayer.seekTo(AudioPlayer.getDurationMs())
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerControlButton(
    icon: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .hoverable(interactionSource),
    ) {
        Text(
            text = icon,
            color = if (isHovered) MeloXColors.TextPrimary else MeloXColors.TextSecondary,
            fontSize = 16.sp,
        )
    }
}
