package melox.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule

/**
 * 1:1 port of Android MeloXGlassTokens.kt (ui/glass/MeloXGlassTokens.kt).
 */

/**
 * Same pinned continuous/capsule geometry; source algorithms are compiled for desktop.
 */
object MeloXShapes {
    val capsule: Shape = Capsule()
    val compact: Shape = ContinuousRoundedRectangle(16.dp)
    val card: Shape = ContinuousRoundedRectangle(22.dp)
    val largeCard: Shape = ContinuousRoundedRectangle(28.dp)
    val sheet: Shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val circle: Shape = CircleShape
}

/** The two Liquid Glass variants Apple exposes for custom components. */
enum class MeloXGlassMaterial {
    Clear,
    Regular,
}

/** Semantic colors used by the iOS Native Components reference. */
object MeloXSystemColors {
    val Blue = Color(0xFF0A84FF)
    val Red = Color(0xFFFF3B30)
    val SecondaryFill = Color(0x26787880)
    val TertiaryFill = Color(0x1F767680)
    val Separator = Color(0x4A3C3C43)
}

enum class MeloXGlassButtonStyle {
    Bordered,
    BorderedProminent,
    Plain,
    Destructive,
}

data class MeloXGlassSpec(
    val blurRadiusDp: Float,
    val lensRadiusDp: Float,
    val refractionHeightDp: Float,
    /** Regular is the opaque-enough control material; Clear is reserved for rich media. */
    val useLens: Boolean,
) {
    companion object {
        fun forMaterial(material: MeloXGlassMaterial): MeloXGlassSpec = when (material) {
            // Keep the same optical envelope as Mei: low blur, visible lens,
            // and a shallow refraction depth. The distinction between Clear
            // and Regular is carried by the tint, not by disabling refraction.
            MeloXGlassMaterial.Clear -> MeloXGlassSpec(2f, 24f, 12f, useLens = true)
            MeloXGlassMaterial.Regular -> MeloXGlassSpec(2f, 24f, 12f, useLens = true)
        }
    }
}
