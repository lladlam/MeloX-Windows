package melox.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

@Composable
fun MeloXGlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MeloXColors.Surface)
                    .shadow(24.dp, RoundedCornerShape(28.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MeloXTypography.headline,
                            color = MeloXColors.OnSurface,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    content()
                }
            }
        }
    }
}
