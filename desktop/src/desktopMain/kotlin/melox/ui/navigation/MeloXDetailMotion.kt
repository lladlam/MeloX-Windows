package melox.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import melox.settings.MeloXSettingsRuntime
import kotlin.math.abs

/** Runtime input modality only; a touch never changes the saved layout preference. */
internal object MeloXPointerMode {
    var touch by mutableStateOf(false)
}

internal fun Modifier.meloXTrackPointerMode() = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        MeloXPointerMode.touch = down.type == PointerType.Touch
    }
}

/** Retain the real parent page below the destination, including its LazyListState. */
@Composable
internal fun <T> MeloXDetailOverlay(target: T?, onBack: () -> Unit, content: @Composable (T) -> Unit) {
    val reduceMotion = MeloXSettingsRuntime.reduceMotion
    AnimatedContent(target, modifier = Modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val incoming = slideInHorizontally(tween(if (reduceMotion) 0 else 300, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(if (reduceMotion) 0 else 180))
            val outgoing = slideOutHorizontally(tween(if (reduceMotion) 0 else 260, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(if (reduceMotion) 0 else 200))
            if (targetState == null) EnterTransition.None togetherWith outgoing
            else incoming togetherWith ExitTransition.None
        }, label = "collection-route") { destination ->
        if (destination != null) Box(Modifier.fillMaxSize().meloXEdgeSwipeBack(onBack)) { content(destination) }
    }
}

/** Edge-only horizontal dismissal leaves ordinary vertical scrolling to the list. */
internal fun Modifier.meloXEdgeSwipeBack(onBack: () -> Unit): Modifier = meloXDirectionalDismiss(
    horizontal = true, onDismiss = onBack)

/** Left artwork column can pull down; right lyric/queue columns keep vertical scroll. */
internal fun Modifier.meloXPlayerPullDown(artworkOnly: Boolean, onDismiss: () -> Unit): Modifier = meloXDirectionalDismiss(
    horizontal = false, artworkOnly = artworkOnly, onDismiss = onDismiss)

private fun Modifier.meloXDirectionalDismiss(horizontal: Boolean, artworkOnly: Boolean = false, onDismiss: () -> Unit): Modifier = composed {
    val dismiss by rememberUpdatedState(onDismiss)
    var distance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val displayed by animateFloatAsState(distance,
        tween(if (dragging || MeloXSettingsRuntime.reduceMotion) 0 else 220, easing = FastOutSlowInEasing), label = "detail-drag-settle")
    this.pointerInput(horizontal, artworkOnly) {
        val edge = 28.dp.toPx()
        val minimum = 96.dp.toPx()
        val velocityThreshold = 900.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (horizontal && down.position.x > edge) return@awaitEachGesture
            if (!horizontal && (down.position.y < 48.dp.toPx() || down.position.y > size.height * .65f ||
                    (!artworkOnly && down.position.x > size.width * .5f))) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = androidx.compose.ui.geometry.Offset.Zero
            var accepted = false
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    // A second finger cancels this navigation gesture, leaving the page in place.
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) { completed = true; break }
                    if (change.isConsumed) break
                    total += change.positionChange()
                    val primary = if (horizontal) total.x else total.y
                    val cross = if (horizontal) total.y else total.x
                    if (!accepted) {
                        if (abs(cross) > viewConfiguration.touchSlop && abs(cross) >= primary) break
                        if (primary < -viewConfiguration.touchSlop) break
                        if (primary <= viewConfiguration.touchSlop || primary < abs(cross) * 1.25f) continue
                        accepted = true
                        dragging = true
                    }
                    change.consume()
                    distance = primary.coerceAtLeast(0f)
                }
                if (accepted && completed) {
                    val velocity = tracker.calculateVelocity().let { if (horizontal) it.x else it.y }
                    val extent = if (horizontal) size.width else size.height
                    if (distance >= minOf(minimum, extent * .25f) || (distance > viewConfiguration.touchSlop * 2 && velocity >= velocityThreshold)) dismiss()
                }
            } finally {
                dragging = false
                distance = 0f
            }
        }
    }.graphicsLayer {
        if (horizontal) translationX = displayed else translationY = displayed
    }
}
