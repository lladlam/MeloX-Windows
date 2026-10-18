/* Adapted from capsule 2.1.3 (Apache-2.0, Copyright Kyant).
 * Geometry is unchanged; Android Path calls use equivalent Compose Path operations. */
package com.kyant.capsule.path

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.kyant.capsule.core.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
sealed interface PathSegment {
    val from: Point
    val to: Point
    fun drawTo(path: Path)

    data class Line(override val from: Point, override val to: Point) : PathSegment {
        override fun drawTo(path: Path) { path.lineTo(to.x.toFloat(), to.y.toFloat()) }
    }

    data class Arc(val center: Point, val radius: Double, val startAngle: Double, val sweepAngle: Double) : PathSegment {
        override val from get() = Point(center.x + cos(startAngle) * radius, center.y + sin(startAngle) * radius)
        override val to get() = Point(center.x + cos(startAngle + sweepAngle) * radius, center.y + sin(startAngle + sweepAngle) * radius)
        override fun drawTo(path: Path) {
            path.arcTo(bounds(center, radius), (startAngle * 180.0 / PI).toFloat(), (sweepAngle * 180.0 / PI).toFloat(), false)
        }
    }

    data class Circle(val center: Point, val radius: Double) : PathSegment {
        override val from get() = Point(center.x + radius, center.y)
        override val to get() = from
        override fun drawTo(path: Path) { path.addOval(bounds(center, radius), Path.Direction.Clockwise) }
    }

    data class Cubic(val p0: Point, val p1: Point, val p2: Point, val p3: Point) : PathSegment {
        override val from get() = p0
        override val to get() = p3
        override fun drawTo(path: Path) {
            path.cubicTo(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), p3.x.toFloat(), p3.y.toFloat())
        }
    }
}

private fun bounds(center: Point, radius: Double) = Rect(
    (center.x - radius).toFloat(), (center.y - radius).toFloat(),
    (center.x + radius).toFloat(), (center.y + radius).toFloat(),
)
