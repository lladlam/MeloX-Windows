package melox.ui.input

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent

internal object MeloXTouchInput {
    private val promotedField by lazy {
        runCatching { MouseEvent::class.java.getDeclaredField("causedByTouchEvent").apply { isAccessible = true } }.getOrNull()
    }
    fun promoted(event: Any?): Boolean = event is MouseEvent && runCatching { promotedField?.getBoolean(event) == true }.getOrDefault(false)
}

/** A touch tap in an editable field, including a tap after dismissing the keyboard. */
internal fun Modifier.meloXTouchKeyboard(enabled: Boolean): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    this.onFocusChanged { focused = it.isFocused }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val touch = down.type == PointerType.Touch || MeloXTouchInput.promoted(currentEvent.nativeEvent)
            if (!touch) return@awaitEachGesture
            var tap = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop || event.changes.count { it.pressed } > 1) tap = false
                if (!change.pressed) {
                    if (tap) scope.launch {
                        delay(80) // Let the text field's own focus/IME transaction finish.
                        if (focused) melox.platform.MeloXWindowsTouchKeyboard.show()
                    }
                    break
                }
            }
        }
    }
}
