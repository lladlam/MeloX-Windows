package melox.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/** Block moving controls. Only a tap in the actual mini-player can reverse a close. */
internal fun Modifier.meloXPlayerTransitionInput(
    transitioning: Boolean, closing: Boolean, miniRect: Rect?, onReopen: () -> Unit,
): Modifier = composed {
    val moving by rememberUpdatedState(transitioning)
    val exiting by rememberUpdatedState(closing)
    val hit by rememberUpdatedState(miniRect)
    val reopen by rememberUpdatedState(onReopen)
    var origin by remember { mutableStateOf(Offset.Zero) }
    this.onGloballyPositioned { origin = it.positionInWindow() }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!moving) return@awaitEachGesture
            val mayReopen = exiting && hit?.contains(down.position + origin) == true
            down.consume()
            var tap = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop || event.changes.count { it.pressed } > 1) tap = false
                event.changes.forEach { it.consume() }
                if (!change.pressed) {
                    if (tap && mayReopen && exiting) reopen()
                    break
                }
            }
        }
    }
}
