package melox.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/** A recorded page, excluding overlays that sample it (avoids recursive capture). */
class MeloXLayerBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

val LocalMeloXLayerBackdrop = staticCompositionLocalOf<MeloXLayerBackdrop?> { null }

@Composable
fun rememberMeloXLayerBackdrop(): MeloXLayerBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { MeloXLayerBackdrop(layer) }
}

/** Record once per content draw; invisible captures still provide the same pixels. */
fun Modifier.meloXRecordBackdrop(backdrop: MeloXLayerBackdrop, visible: Boolean = true): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            if (visible) drawLayer(backdrop.layer)
        }

/** Sample the real page at this control's location, with a padded GPU blur crop. */
fun Modifier.meloXSampleBackdrop(
    blurRadius: Dp,
    backdropOverride: MeloXLayerBackdrop? = null,
): Modifier = composed {
    val backdrop = backdropOverride ?: LocalMeloXLayerBackdrop.current
    if (backdrop == null) return@composed Modifier
    val sampleLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    Modifier.onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            if (backdrop.layer.size.width > 0 && backdrop.layer.size.height > 0) {
                val blurPx = blurRadius.toPx().coerceAtLeast(0f)
                val padding = ceil(blurPx * 3f).toInt()
                val sampleSize = IntSize(
                    ceil(size.width).toInt() + padding * 2,
                    ceil(size.height).toInt() + padding * 2,
                )
                sampleLayer.renderEffect = if (blurPx > 0f) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null
                sampleLayer.record(size = sampleSize) {
                    translate(
                        left = backdrop.origin.x - origin.x + padding,
                        top = backdrop.origin.y - origin.y + padding,
                    ) { drawLayer(backdrop.layer) }
                }
                translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(sampleLayer) }
            }
            drawContent()
        }
}
