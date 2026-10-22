package melox.ui.desktop

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * AWT may promote a Windows touchscreen contact to a mouse press/drag/release.
 * LazyColumn handles Touch itself, but intentionally rejects mouse dragging.
 * Keep native Touch and wheel handling, and support the promoted drag path too.
 */
@Composable
internal fun Modifier.desktopPromotedDragScroll(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
): Modifier {
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    return pointerInput(state, fling, orientation) {
        fun Offset.axis() = if (orientation == Orientation.Vertical) y else x
        fun Offset.crossAxis() = if (orientation == Orientation.Vertical) x else y
        var settling: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            settling?.cancel()
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = Offset.Zero
            var dragging = false
            var released = false
            var deltas: Channel<Float>? = null
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (dragging) change.consume()
                        released = true
                        break
                    }
                    // Text selection, buttons and child drags own their consumed input.
                    if (change.isConsumed) break
                    val delta = change.positionChange()
                    total += delta
                    if (!dragging) {
                        if (abs(total.crossAxis()) > viewConfiguration.touchSlop && abs(total.crossAxis()) >= abs(total.axis())) break
                        if (abs(total.axis()) <= viewConfiguration.touchSlop) continue
                        // Leave an exhausted nested shelf available to its parent.
                        if ((total.axis() < 0 && !state.canScrollForward) || (total.axis() > 0 && !state.canScrollBackward)) break
                        dragging = true
                        val stream = Channel<Float>(Channel.UNLIMITED)
                        deltas = stream
                        settling = scope.launch {
                            // Own the scroll mutation for the entire gesture, so a
                            // running animation cannot keep moving under the finger.
                            state.scroll(MutatePriority.UserInput) {
                                for (distance in stream) scrollBy(distance)
                                if (released) {
                                    val measured = tracker.calculateVelocity()
                                    val velocity = -(if (orientation == Orientation.Vertical) measured.y else measured.x)
                                    with(fling) { performFling(velocity) }
                                }
                            }
                        }
                        stream.trySend(-total.axis() + viewConfiguration.touchSlop * if (total.axis() > 0) 1 else -1)
                    } else {
                        deltas?.trySend(-delta.axis())
                    }
                    change.consume()
                }
            } finally {
                deltas?.close()
            }
        }
    }
}

/** Match wheel/native-touch scrolling and AWT-promoted dragging to one state. */
@Composable
internal fun Modifier.desktopVerticalScroll(state: ScrollState = rememberScrollState()): Modifier =
    desktopPromotedDragScroll(state).verticalScroll(state)
