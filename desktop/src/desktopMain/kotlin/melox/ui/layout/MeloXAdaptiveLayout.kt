package melox.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MeloXWindowWidthClass { Compact, Medium, Expanded }

data class MeloXWindowInfo(val widthClass: MeloXWindowWidthClass, val isLandscape: Boolean,
    val gutter: Dp, val maxContentWidth: Dp, val gridColumns: Int) {
    val supportsTwoPane get() = widthClass != MeloXWindowWidthClass.Compact
}

/** The pinned shared window policy, including compact landscape and medium portrait. */
@Composable
fun rememberMeloXWindowInfo(): MeloXWindowInfo {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val width = with(density) { containerSize.width.toDp().value.toInt() }
    val height = with(density) { containerSize.height.toDp().value.toInt() }
    val widthClass = when { width >= 840 -> MeloXWindowWidthClass.Expanded; width >= 600 -> MeloXWindowWidthClass.Medium; else -> MeloXWindowWidthClass.Compact }
    return when (widthClass) {
        MeloXWindowWidthClass.Compact -> MeloXWindowInfo(widthClass, width > height, 20.dp, 600.dp, 2)
        MeloXWindowWidthClass.Medium -> MeloXWindowInfo(widthClass, width > height, 28.dp, 920.dp, 3)
        MeloXWindowWidthClass.Expanded -> MeloXWindowInfo(widthClass, width > height, 36.dp, 1180.dp, 4)
    }
}
