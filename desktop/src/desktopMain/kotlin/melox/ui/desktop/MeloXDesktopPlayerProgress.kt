package melox.ui.desktop

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsRuntime

/** DesktopNowPlayingProgress: full-width capsule, rectangular fill, 8→18pt scrubbing. */
@Composable
internal fun DesktopPlayerProgress(scale: Float) {
    val position by AudioPlayer.positionMs.collectAsState()
    val duration by AudioPlayer.durationMs.collectAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    val latestDuration by rememberUpdatedState(duration)
    val fraction = scrub ?: (position.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
    val expanded = scrub != null
    val height by animateFloatAsState(if (expanded) 18f else 8f,
        tween(if (MeloXSettingsRuntime.reduceMotion) 0 else if (expanded) 320 else 400,
            easing = if (expanded) CubicBezierEasing(.25f, .10f, .25f, 1f) else CubicBezierEasing(.20f, .80f, .20f, 1f)),
        label = "desktop-progress-height")
    fun seek(value: Float) {
        PlaybackCommands.playerController()?.seekTo((latestDuration * value.coerceIn(0f, 1f)).toLong())
    }
    Column(Modifier.fillMaxWidth().testTag("desktop-player-progress")) {
        Canvas(Modifier.fillMaxWidth().height((18 * scale).dp).testTag("desktop-seek").semantics {
            contentDescription = desktopString("ui.desktop.player.playback_progress")
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            if (duration <= 0) disabled()
            setProgress { if (latestDuration > 0) { seek(it); true } else false }
        }.pointerInput(duration > 0) {
            if (latestDuration <= 0) return@pointerInput
            detectTapGestures { seek(it.x / size.width.coerceAtLeast(1)) }
        }.pointerInput(duration > 0) {
            if (latestDuration <= 0) return@pointerInput
            detectDragGestures(
                onDragStart = { scrub = (it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) },
                onDragEnd = { scrub?.let(::seek); scrub = null },
                onDragCancel = { scrub = null },
            ) { change, _ ->
                change.consume()
                scrub = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                scrub?.let(::seek)
            }
        }) {
            val h = (height * scale).dp.toPx()
            val y = (size.height - h) / 2
            val capsule = Path().apply { addRoundRect(RoundRect(0f, y, size.width, y + h, CornerRadius(h / 2))) }
            clipPath(capsule) {
                drawRect(Color.White.copy(alpha = .22f), Offset(0f, y), Size(size.width, h))
                drawRect(Color.White, Offset(0f, y), Size(size.width * fraction, h))
            }
        }
        val displayed = scrub?.let { (duration * it).toLong() } ?: position
        Row(Modifier.fillMaxWidth().padding(top = (3 * scale).dp)) {
            Text(desktopPlayerTime(displayed), color = Color.White.copy(alpha = .82f), fontSize = (10 * scale).sp,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.weight(1f))
            Text("−" + desktopPlayerTime((duration - displayed).coerceAtLeast(0)), color = Color.White.copy(alpha = .82f),
                fontSize = (10 * scale).sp, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
        }
    }
}

private fun desktopPlayerTime(ms: Long) = "${ms.coerceAtLeast(0) / 60000}:${(ms.coerceAtLeast(0) / 1000 % 60).toString().padStart(2, '0')}"
