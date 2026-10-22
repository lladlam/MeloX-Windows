package melox.ui.navigation

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import melox.settings.MeloXSettingsRuntime
import kotlin.math.roundToInt

/** Both pages read one declarative spring: reversing never restarts an animateTo coroutine. */
@Stable
internal class MeloXPageMotion(
    private val transition: Transition<Boolean>,
    private val fraction: State<Float>,
) {
    val progress: Float get() = fraction.value.coerceIn(0f, 1f)
    // Reversing an unfinished push can have current == target == false while
    // the spring still has distance/velocity. Keep its outgoing content alive.
    val active: Boolean get() = transition.currentState || transition.targetState || transition.isRunning || fraction.value > 0f
}

@Composable
internal fun rememberMeloXPageMotion(visible: Boolean): MeloXPageMotion {
    val transition = updateTransition(visible, label = "page-stack")
    val progress = transition.animateFloat(transitionSpec = {
        if (MeloXSettingsRuntime.reduceMotion) tween(0)
        else spring(dampingRatio = 1f, stiffness = 800f, visibilityThreshold = .0001f)
    }, label = "page-stack-progress") { if (it) 1f else 0f }
    return remember(transition) { MeloXPageMotion(transition, progress) }
}

/** Pinned PageTransitions:218–237: placement, absolute left, no scale/tint/clip. */
internal fun Modifier.meloXPageRecede(motion: MeloXPageMotion, enabled: Boolean = true): Modifier =
    if (!enabled) this else layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        layout(child.width, child.height) {
            child.place((-child.width * motion.progress / 3f).roundToInt(), 0)
        }
    }

/** Keep the outgoing page and its BackHandler until the shared spring actually finishes. */
@Composable
internal fun MeloXInterruptiblePage(
    visible: Boolean,
    motion: MeloXPageMotion,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = motion.active
    val focus = remember { FocusRequester() }
    MeloXBackHandler(active) { if (visible) onBack() }
    LaunchedEffect(active, visible) {
        if (active && visible) focus.requestFocus()
        else if (!active) runCatching { MeloXRootFocus.requester?.requestFocus() }
    }
    if (active) Box(modifier.fillMaxSize().layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        layout(child.width, child.height) {
            child.place((child.width * (1f - motion.progress)).roundToInt(), 0)
        }
    }.testTag("page-stack-detail").semantics {
        stateDescription = "progress=${motion.progress};visible=$visible"
    }.focusRequester(focus).focusable()) { content() }
}
