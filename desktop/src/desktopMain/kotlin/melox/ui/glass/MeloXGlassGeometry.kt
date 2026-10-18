package melox.ui.glass

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.shapes.RoundedRectangularShape

/** Match Backdrop Lens.kt's shape radii even when continuous corners return Generic outlines. */
internal fun Shape?.glassCornerRadii(size: Size, direction: LayoutDirection, density: Density, outline: Outline?): List<Float> {
    val maximum = (size.minDimension / 2f).coerceAtLeast(0f)
    val corners = when (this) {
        is RoundedRectangularShape -> corners(size, direction, density).let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }
        is CornerBasedShape -> {
            val ltr = direction == LayoutDirection.Ltr || this is AbsoluteRoundedCornerShape
            val start = topStart.toPx(size, density); val end = topEnd.toPx(size, density)
            val bottomStart = bottomStart.toPx(size, density); val bottomEnd = bottomEnd.toPx(size, density)
            if (ltr) listOf(start, end, bottomEnd, bottomStart) else listOf(end, start, bottomStart, bottomEnd)
        }
        else -> when (outline) {
            is Outline.Rounded -> outline.roundRect.let { listOf(it.topLeftCornerRadius.x, it.topRightCornerRadius.x, it.bottomRightCornerRadius.x, it.bottomLeftCornerRadius.x) }
            is Outline.Rectangle -> List(4) { 0f }
            else -> emptyList()
        }
    }
    return corners.map { it.glassFinite().coerceIn(0f, maximum) }
}
