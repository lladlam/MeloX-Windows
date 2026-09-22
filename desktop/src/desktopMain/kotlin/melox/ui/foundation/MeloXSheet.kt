package melox.ui.foundation

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

@Composable
fun MeloXGlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MeloXMotion.panelEnterSpec()) + expandVertically(
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f)
        ),
        exit = fadeOut(MeloXMotion.panelExitSpec()) + shrinkVertically(
            animationSpec = spring(dampingRatio = 0.90f, stiffness = 500f)
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 30) onDismiss()
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(MeloXGlass.sheetShape)
                    .background(MeloXColors.Surface)
                    .shadow(24.dp, MeloXGlass.sheetShape)
                    .pointerInput(Unit) { /* consume */ },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.42f)),
                )
                if (title != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MeloXTypography.headline,
                        color = MeloXColors.OnSurface,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                content()
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}
