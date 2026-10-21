package melox.ui.desktop

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * AWT may promote a Windows touchscreen contact to a mouse press/drag/release.
 * LazyColumn handles Touch itself, but intentionally rejects mouse dragging.
 * Keep native Touch and wheel handling, and support the promoted drag path too.
 */
@Composable
internal fun Modifier.desktopPromotedDragScroll(state: ScrollableState): Modifier {
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    return pointerInput(state, fling) {
        var settling: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            settling?.cancel()
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = Offset.Zero
            var dragging = false
            var released = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) { released = true; break }
                // Text selection, buttons and child drags own their consumed input.
                if (change.isConsumed) break
                val delta = change.positionChange()
                total += delta
                if (!dragging) {
                    if (abs(total.x) > viewConfiguration.touchSlop && abs(total.x) >= abs(total.y)) break
                    if (abs(total.y) <= viewConfiguration.touchSlop) continue
                    dragging = true
                    state.dispatchRawDelta(-total.y + viewConfiguration.touchSlop * if (total.y > 0) 1 else -1)
                } else {
                    state.dispatchRawDelta(-delta.y)
                }
                change.consume()
            }
            if (dragging && released) {
                val velocity = -tracker.calculateVelocity().y
                settling = scope.launch {
                    state.scroll(MutatePriority.UserInput) { with(fling) { performFling(velocity) } }
                }
            }
        }
    }
}
