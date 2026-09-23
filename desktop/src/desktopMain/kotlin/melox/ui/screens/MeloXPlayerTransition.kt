package melox.ui.screens

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import melox.ui.foundation.MeloXMotion

internal data object MeloXPlayerShellKey
internal data object MeloXPlayerArtworkKey

@OptIn(ExperimentalSharedTransitionApi::class)
internal val MeloXArtworkBoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = 300, easing = FastOutSlowInEasing)
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal val MeloXPlayerShellBoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = MeloXMotion.PlayerTransitionDurationMillis, easing = FastOutSlowInEasing)
}
