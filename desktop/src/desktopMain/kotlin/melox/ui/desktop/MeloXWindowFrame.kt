package melox.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle

internal class MeloXWindowChrome(
    val scope: FrameWindowScope,
    val state: WindowState,
    val onClose: () -> Unit,
) {
    fun toggleMaximized() {
        val next = if (state.placement == WindowPlacement.Floating) WindowPlacement.Maximized else WindowPlacement.Floating
        // Clear Skiko fullscreen before applying an ordinary window placement.
        if (scope.window.placement == WindowPlacement.Fullscreen) scope.window.placement = WindowPlacement.Floating
        state.placement = next
        scope.window.placement = next
    }
}

internal val LocalMeloXWindowChrome = staticCompositionLocalOf<MeloXWindowChrome?> { null }

/** App-owned caption. The six page surfaces remain inside the client area. */
@Composable
internal fun MeloXWindowFrame(content: @Composable () -> Unit) {
    val owner = LocalMeloXWindowChrome.current
    if (owner == null) { content(); return }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(34.dp).testTag("window-caption")) {
                with(owner.scope) {
                    WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()
                        .testTag("window-drag-area").pointerInput(owner) {
                            detectTapGestures(onDoubleTap = { owner.toggleMaximized() })
                        }) {
                        Box(Modifier.fillMaxSize().padding(start = 14.dp), contentAlignment = Alignment.CenterStart) {
                            Text("MeloX", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f))
                        }
                    }
                }
                CaptionButton("minimize") { owner.state.isMinimized = true }
                CaptionButton(if (owner.state.placement == WindowPlacement.Floating) "maximize" else "restore") { owner.toggleMaximized() }
                CaptionButton("close", owner.onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        if (owner.state.placement == WindowPlacement.Floating) {
            ResizeEdge(owner, -1, 0, Modifier.align(Alignment.CenterStart).width(5.dp).fillMaxHeight())
            ResizeEdge(owner, 1, 0, Modifier.align(Alignment.CenterEnd).width(5.dp).fillMaxHeight())
            ResizeEdge(owner, 0, -1, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(5.dp))
            ResizeEdge(owner, 0, 1, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp))
            ResizeEdge(owner, -1, -1, Modifier.align(Alignment.TopStart).size(10.dp))
            ResizeEdge(owner, 1, -1, Modifier.align(Alignment.TopEnd).size(10.dp))
            ResizeEdge(owner, -1, 1, Modifier.align(Alignment.BottomStart).size(10.dp))
            ResizeEdge(owner, 1, 1, Modifier.align(Alignment.BottomEnd).size(10.dp))
        }
    }
}

@Composable
private fun CaptionButton(action: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val foreground = if (action == "close" && hovered) Color.White else MaterialTheme.colorScheme.onBackground
    Box(Modifier.width(46.dp).fillMaxHeight().testTag("window-$action")
        .background(if (!hovered) Color.Transparent else if (action == "close") Color(0xFFC42B1C) else foreground.copy(alpha = .1f))
        .semantics { contentDescription = desktopPlatformString("window_$action") }
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 1.dp.toPx()
            val inset = stroke / 2
            when (action) {
                "minimize" -> drawLine(foreground, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke)
                "close" -> {
                    drawLine(foreground, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke)
                    drawLine(foreground, Offset(size.width - inset, inset), Offset(inset, size.height - inset), stroke)
                }
                "restore" -> {
                    val shift = 3.dp.toPx()
                    drawRect(foreground, Offset(shift, inset), Size(size.width - shift - inset, size.height - shift - inset), style = Stroke(stroke))
                    drawRect(foreground, Offset(inset, shift), Size(size.width - shift - inset, size.height - shift - inset), style = Stroke(stroke))
                }
                else -> drawRect(foreground, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            }
        }
    }
}

@Composable
private fun ResizeEdge(owner: MeloXWindowChrome, horizontal: Int, vertical: Int, modifier: Modifier) {
    val cursor = when {
        horizontal == 0 -> Cursor.N_RESIZE_CURSOR
        vertical == 0 -> Cursor.E_RESIZE_CURSOR
        horizontal == vertical -> Cursor.NW_RESIZE_CURSOR
        else -> Cursor.NE_RESIZE_CURSOR
    }
    Box(modifier.pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursor)))
        .pointerInput(owner, horizontal, vertical) {
            var bounds = Rectangle()
            var start = Point()
            detectDragGestures(onDragStart = {
                bounds = owner.scope.window.bounds
                start = MouseInfo.getPointerInfo().location
            }) { change, _ ->
                change.consume()
                val pointer = MouseInfo.getPointerInfo().location
                val dx = pointer.x - start.x
                val dy = pointer.y - start.y
                val minimum = owner.scope.window.minimumSize
                val width = if (horizontal == 0) bounds.width else (bounds.width + dx * horizontal).coerceAtLeast(minimum.width.coerceAtLeast(320))
                val height = if (vertical == 0) bounds.height else (bounds.height + dy * vertical).coerceAtLeast(minimum.height.coerceAtLeast(240))
                owner.scope.window.setBounds(if (horizontal < 0) bounds.x + bounds.width - width else bounds.x,
                    if (vertical < 0) bounds.y + bounds.height - height else bounds.y, width, height)
            }
        })
}
