package melox.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import melox.ui.theme.MeloXColors

object MeloXGlass {
    val capsuleShape = RoundedCornerShape(50)
    val compactShape = RoundedCornerShape(16.dp)
    val cardShape = RoundedCornerShape(22.dp)
    val largeCardShape = RoundedCornerShape(28.dp)
    val sheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 36.dp, bottomEnd = 36.dp)
    val dialogShape = RoundedCornerShape(28.dp)

    // Glass surface colors for dark theme
    val surfaceColor = Color(0x15FFFFFF)
    val surfaceHighColor = Color(0x1FFFFFFF)
    val separatorColor = Color(0x4A3C3C43)
    val highlightColor = Color(0x0DFFFFFF)

    // Button variants
    val buttonDefaultTint = MeloXColors.Primary
    val buttonDefaultSurface = MeloXColors.SurfaceVariant
    val buttonProminentSurface = Color(0xEBFF2442)
    val buttonDestructiveSurface = Color(0x1FFF2442)
}

@Composable
fun Modifier.glassSurface(
    shape: RoundedCornerShape = MeloXGlass.cardShape,
    backgroundColor: Color = MeloXGlass.surfaceColor,
    borderAlpha: Float = 0.12f,
    shadowElevation: Dp = 0.dp,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressAlpha = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        pressAlpha.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = MeloXMotion.interactivePress(),
        )
    }

    return this
        .shadow(elevation = shadowElevation, shape = shape)
        .clip(shape)
        .background(backgroundColor)
        .border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = borderAlpha + pressAlpha.value * 0.08f),
            shape = shape,
        )
}

@Composable
fun Modifier.glassCard(
    backgroundColor: Color = MeloXColors.SurfaceVariant,
): Modifier = this.glassSurface(
    shape = MeloXGlass.cardShape,
    backgroundColor = backgroundColor,
    shadowElevation = 2.dp,
)

@Composable
fun Modifier.glassCapsule(
    backgroundColor: Color = MeloXGlass.surfaceColor,
): Modifier = this.glassSurface(
    shape = MeloXGlass.capsuleShape,
    backgroundColor = backgroundColor,
)

@Composable
fun Modifier.glassButton(
    backgroundColor: Color = MeloXColors.Primary,
    contentColor: Color = Color.White,
): Modifier = this
    .clip(MeloXGlass.capsuleShape)
    .background(backgroundColor)
    .border(0.5.dp, Color.White.copy(alpha = 0.15f), MeloXGlass.capsuleShape)
