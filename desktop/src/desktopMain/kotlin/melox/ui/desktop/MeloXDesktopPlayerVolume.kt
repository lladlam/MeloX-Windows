package melox.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import melox.playback.PlaybackCommands

/** Same player volume endpoint, with the reference's mini white slider skin. */
@Composable
internal fun DesktopPlayerVolume(volume: Float) {
    var transient by remember { mutableStateOf<Float?>(null) }
    fun update(x: Float, width: Float, inset: Float) {
        val value = ((x - inset) / (width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
        transient = value
        PlaybackCommands.playerController()?.setVolume(value)
    }
    val fraction = transient ?: volume
    Canvas(Modifier.width(130.dp).height(22.dp).testTag("desktop-full-volume").semantics {
        contentDescription = desktopString("ui.player.volume")
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        setProgress { PlaybackCommands.playerController()?.setVolume(it.coerceIn(0f, 1f)); true }
    }.pointerInput(Unit) {
        detectTapGestures { update(it.x, size.width.toFloat(), 5.dp.toPx()); transient = null }
    }.pointerInput(Unit) {
        detectDragGestures(onDragStart = { update(it.x, size.width.toFloat(), 5.dp.toPx()) },
            onDragEnd = { transient = null }, onDragCancel = { transient = null }) { change, _ ->
            change.consume(); update(change.position.x, size.width.toFloat(), 5.dp.toPx())
        }
    }) {
        val inset = 5.dp.toPx()
        val track = size.width - inset * 2
        val height = 4.dp.toPx()
        val top = (size.height - height) / 2
        drawRoundRect(Color.White.copy(alpha = .10f), Offset(inset, top), Size(track, height), CornerRadius(height / 2))
        drawRoundRect(Color.White, Offset(inset, top), Size(track * fraction, height), CornerRadius(height / 2))
        val thumbWidth = 16.dp.toPx()
        val thumbHeight = 12.dp.toPx()
        drawRoundRect(Color(0xFFE4E5E8), Offset(inset + track * fraction - thumbWidth / 2, (size.height - thumbHeight) / 2),
            Size(thumbWidth, thumbHeight), CornerRadius(thumbHeight / 2))
    }
}
