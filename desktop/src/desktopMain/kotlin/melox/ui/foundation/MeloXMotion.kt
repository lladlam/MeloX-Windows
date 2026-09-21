package melox.ui.foundation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MeloXMotion {
    // Page transitions
    const val PageEnterMillis = 280
    const val PageExitMillis = 220
    const val ContentEnterMillis = 320
    const val ContentExitMillis = 240
    const val IconEnterMillis = 180
    const val IconExitMillis = 120

    // Spring parameters
    const val PanelEnterStiffness = 420f
    const val PanelExitStiffness = 500f
    const val DropdownStiffness = 320f

    // Panel enter animation (glass sheets, dialogs)
    val panelEnterSpec = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = PanelEnterStiffness,
    )
    val panelExitSpec = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = PanelExitStiffness,
    )

    // Page enter/exit
    val pageEnterTween = tween<Float>(PageEnterMillis, easing = FastOutSlowInEasing)
    val pageExitTween = tween<Float>(PageExitMillis, easing = FastOutSlowInEasing)

    // Content transitions
    val contentEnterTween = tween<Float>(ContentEnterMillis, easing = FastOutSlowInEasing)
    val contentExitTween = tween<Float>(ContentExitMillis, easing = FastOutSlowInEasing)

    // Mini Player
    val miniSwipeDismiss = spring<Float>(dampingRatio = 0.68f, stiffness = 360f)
    val miniSwipeBounceBack = spring<Float>(dampingRatio = 0.72f, stiffness = 430f)
    val miniReveal = spring<Float>(dampingRatio = 0.9f, stiffness = 360f)

    // Player
    val playerArtworkSpec = tween<Float>(300, easing = FastOutSlowInEasing)
    val playerShellSpec = tween<Float>(360, easing = FastOutSlowInEasing)
    val playerPageCrossFade = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)
    val playerArtworkShadow = spring<Float>(dampingRatio = 0.92f, stiffness = 320f)
    val playerPlaybackScale = spring<Float>(dampingRatio = 0.70f, stiffness = 280f)

    // Liquid drag
    val liquidDragValue = spring<Float>(dampingRatio = 1.0f, stiffness = 1000f)
    val liquidDragVelocity = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
    val liquidPressProgress = spring<Float>(dampingRatio = 1.0f, stiffness = 1000f)
    val liquidScaleX = spring<Float>(dampingRatio = 0.6f, stiffness = 250f)
    val liquidScaleY = spring<Float>(dampingRatio = 0.7f, stiffness = 250f)

    // Tab selection
    val tabDropdownSpec = spring<Float>(dampingRatio = 0.82f, stiffness = DropdownStiffness)

    // Interactive highlight
    val interactivePress = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
    val interactivePosition = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
}
