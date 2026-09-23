package melox.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import melox.ui.foundation.MeloXMotion
import kotlin.math.roundToInt

private fun meloXPanelEnter(initialScale: Float = 0.96f) =
    fadeIn(spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness)) +
        scaleIn(
            initialScale = initialScale,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = MeloXMotion.PanelEnterStiffness),
        )

private fun meloXPanelExit(targetScale: Float = 0.98f) =
    fadeOut(spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness)) +
        scaleOut(
            targetScale = targetScale,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = MeloXMotion.PanelExitStiffness),
        )

@Composable
fun MeloXGlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(visible) }
    LaunchedEffect(visible) {
        visibility.targetState = visible
    }
    val requestDismiss = { onDismiss() }
    val dark = isDark(MaterialTheme.colorScheme.background)
    val hasBackdrop = LocalMeloXBackdrop.current
    val sheetTint = when {
        hasBackdrop && dark -> Color.White.copy(alpha = 0.08f)
        hasBackdrop -> Color.White.copy(alpha = 0.34f)
        dark -> Color.Black
        else -> Color.White
    }
    val sheetSurface = when {
        hasBackdrop && dark -> Color.Black.copy(alpha = 0.12f)
        hasBackdrop -> Color.White.copy(alpha = 0.20f)
        dark -> Color.Black
        else -> Color.White
    }
    if (visibility.currentState || visibility.targetState) {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = meloXPanelEnter(initialScale = 0.97f),
                exit = meloXPanelExit(),
            ) {
                val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = requestDismiss,
                        )
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .offset { IntOffset(0, dragOffset.roundToInt().coerceAtLeast(0)) }
                            .meloXGlassSurface(
                                shape = MeloXShapes.sheet,
                                tint = sheetTint,
                                surfaceColor = sheetSurface,
                                dark = dark,
                            )
                            .pointerInput(dismissDistance) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { _, dragAmount ->
                                        dragOffset = (dragOffset + dragAmount).coerceAtLeast(0f)
                                    },
                                    onDragEnd = {
                                        if (dragOffset >= dismissDistance) requestDismiss()
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        SheetGrabber()
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun MeloXGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(visible) }
    LaunchedEffect(visible) {
        visibility.targetState = visible
    }
    val requestDismiss = { onDismiss() }
    val dark = isDark(MaterialTheme.colorScheme.background)
    val hasBackdrop = LocalMeloXBackdrop.current
    val dialogTint = when {
        hasBackdrop && dark -> Color.White.copy(alpha = 0.08f)
        hasBackdrop -> Color.White.copy(alpha = 0.34f)
        dark -> Color.Black
        else -> Color.White
    }
    val dialogSurface = if (dark) Color.Black else Color.White
    if (visibility.currentState || visibility.targetState) {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = meloXPanelEnter(),
                exit = meloXPanelExit(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = requestDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = modifier
                            .padding(horizontal = 28.dp)
                            .fillMaxWidth()
                            .widthIn(max = 360.dp)
                            .meloXGlassSurface(
                                shape = RoundedCornerShape(28.dp),
                                tint = dialogTint,
                                surfaceColor = dialogSurface,
                                dark = dark,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetGrabber() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(
                    color = Color.White.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(999.dp),
                ),
        )
    }
}

private fun isDark(color: Color): Boolean {
    fun channel(c: Float): Float = if (c <= 0.03928f) c / 12.92f else {
        val x = (c + 0.055f) / 1.055f
        x * x * x
    }
    val r = channel(color.red)
    val g = channel(color.green)
    val b = channel(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b < 0.5f
}
