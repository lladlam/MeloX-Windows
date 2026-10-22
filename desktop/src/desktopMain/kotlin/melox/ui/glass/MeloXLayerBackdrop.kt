package melox.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/** A recorded page, excluding overlays that sample it (avoids recursive capture). */
class MeloXLayerBackdrop(val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
    var origin by mutableStateOf(Offset.Zero)
        internal set
}

val LocalMeloXLayerBackdrop = staticCompositionLocalOf<MeloXLayerBackdrop?> { null }

/** Adjacent page projected into a narrow side face, rather than a readable mirrored page. */
enum class MeloXBackdropEdge { Left, Right }

/** The legacy visual-context flag is not evidence that an acyclic sampler exists. */
@Composable
internal fun meloXGlassAvailable(): Boolean = LocalMeloXLayerBackdrop.current != null

@Composable
fun rememberMeloXLayerBackdrop(): MeloXLayerBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { MeloXLayerBackdrop(layer) }
}

/** Record once per content draw; invisible captures still provide the same pixels. */
fun Modifier.meloXRecordBackdrop(backdrop: MeloXLayerBackdrop, visible: Boolean = true): Modifier =
    onGloballyPositioned { backdrop.coordinates = it; backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            if (visible) drawLayer(backdrop.layer)
        }

/** Sample the real page at this control's location, with a padded GPU blur crop. */
fun Modifier.meloXSampleBackdrop(
    blurRadius: Dp,
    backdropOverride: MeloXLayerBackdrop? = null,
    shape: Shape? = null,
    lensHeight: Dp = androidx.compose.ui.unit.Dp(0f),
    lensAmount: Dp = androidx.compose.ui.unit.Dp(0f),
    depth: Boolean = false,
    dispersion: Boolean = false,
    vibrant: Boolean = false,
    underlay: MeloXLayerBackdrop? = null,
    sampleScale: Offset = Offset(1f, 1f),
    reflectionEdge: MeloXBackdropEdge? = null,
): Modifier = composed {
    val backdrop = backdropOverride ?: LocalMeloXLayerBackdrop.current
    if (backdrop == null) return@composed Modifier
    val sampleLayer = rememberGraphicsLayer()
    val filters = remember { MeloXGlassFilterOwner() }
    DisposableEffect(filters, sampleLayer) { onDispose { sampleLayer.renderEffect = null; filters.close() } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Modifier.onGloballyPositioned { coordinates = it; origin = it.positionInRoot() }
        .drawWithCache {
                val blurPx = blurRadius.toPx().glassFinite().coerceAtLeast(0f)
                val height = lensHeight.toPx().glassFinite().coerceAtLeast(0f)
                val amount = lensAmount.toPx().glassFinite().coerceAtLeast(0f)
                val padding = ceil(blurPx * 3f + amount * (if (dispersion) 2f else 1f)).toInt()
                val outline = shape?.createOutline(size, layoutDirection, this)
                val radii = shape.glassCornerRadii(size, layoutDirection, this, outline)
                val sampleSize = IntSize(
                    ceil(size.width).toInt() + padding * 2,
                    ceil(size.height).toInt() + padding * 2,
                )
                sampleLayer.renderEffect = filters.obtain(MeloXGlassFilterKey(size.width, size.height, padding.toFloat(), blurPx,
                    radii, MeloXGlassOptics(height, amount, depth, dispersion, vibrant)))
                val matrix = Matrix()
                val panelWidth = size.width
                val edgeWidth = minOf(28.dp.toPx(), panelWidth / 2f)
                val compression = edgeWidth / 180.dp.toPx()
                val edgeStart = if (reflectionEdge == MeloXBackdropEdge.Right) panelWidth - edgeWidth else 0f
                val edgeEnd = edgeStart + edgeWidth
                val edgePaint = Paint()
                val edgeMask = Brush.horizontalGradient(
                    if (reflectionEdge == MeloXBackdropEdge.Right) listOf(Color.Transparent, Color.White)
                    else listOf(Color.White, Color.Transparent), startX = edgeStart, endX = edgeEnd)
                val sampleCenter = Offset(padding + size.width / 2f, padding + size.height / 2f)
                fun DrawScope.drawSource(source: MeloXLayerBackdrop) {
                    val target = coordinates
                    val input = source.coordinates
                    val points = if (target?.isAttached == true && input?.isAttached == true) {
                        try {
                            listOf(target.localPositionOf(input, Offset.Zero),
                                target.localPositionOf(input, Offset(256f, 0f)),
                                target.localPositionOf(input, Offset(0f, 256f)))
                        } catch (_: IllegalArgumentException) { null }
                        catch (_: IllegalStateException) { null }
                    } else null
                    if (points != null && points.all { it.x.isFinite() && it.y.isFinite() }) {
                        // Source-local -> control-local mapping includes inverse press,
                        // drag and ancestor transforms, keeping the sampled page aligned.
                        val p = points[0]; val x = (points[1] - p) / 256f; val y = (points[2] - p) / 256f
                        val edge = if (reflectionEdge == MeloXBackdropEdge.Right) panelWidth else 0f
                        matrix.values[0] = if (reflectionEdge != null) -compression * x.x else x.x; matrix.values[1] = x.y
                        matrix.values[4] = if (reflectionEdge != null) -compression * y.x else y.x; matrix.values[5] = y.y
                        matrix.values[12] = (if (reflectionEdge != null) edge * (1f + compression) - compression * p.x else p.x) + padding
                        matrix.values[13] = p.y + padding
                        withTransform({ transform(matrix) }) { drawLayer(source.layer) }
                    } else {
                        val dx = source.origin.x - origin.x
                        if (reflectionEdge != null) {
                            val edge = if (reflectionEdge == MeloXBackdropEdge.Right) panelWidth else 0f
                            withTransform({
                                translate(edge * (1f + compression) - compression * dx + padding, source.origin.y - origin.y + padding)
                                scale(-compression, 1f, Offset.Zero)
                            }) { drawLayer(source.layer) }
                        } else translate(dx + padding, source.origin.y - origin.y + padding) { drawLayer(source.layer) }
                    }
                }
            onDrawWithContent {
              if (backdrop.layer.size.width > 0 && backdrop.layer.size.height > 0 && size.width > 0 && size.height > 0) {
                sampleLayer.record(size = sampleSize) {
                    if (underlay != null && underlay !== backdrop) {
                         drawSource(underlay)
                    }
                     scale(sampleScale.x.glassFinite(), sampleScale.y.glassFinite(), sampleCenter) { drawSource(backdrop) }
                }
                 if (reflectionEdge == null) {
                     translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(sampleLayer) }
                 } else clipRect(left = edgeStart, right = edgeEnd) {
                     drawContext.canvas.saveLayer(Rect(edgeStart, 0f, edgeEnd, size.height), edgePaint)
                     translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(sampleLayer) }
                     drawRect(edgeMask, blendMode = BlendMode.DstIn)
                     drawContext.canvas.restore()
                 }
            }
            drawContent()
            }
        }
}
