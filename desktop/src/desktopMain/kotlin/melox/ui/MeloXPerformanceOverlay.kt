package melox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import java.util.Locale

/** Compose frame-clock intervals and JVM heap, sampled only while the overlay is enabled. */
@Composable
internal fun MeloXPerformanceOverlay(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("帧时采样中…") }
    LaunchedEffect(Unit) {
        val intervals = LongArray(90)
        var count = 0L
        var previous = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val delta = now - previous
            previous = now
            if (delta <= 0) continue
            intervals[(count % intervals.size).toInt()] = delta
            count++
            if (count % 6L == 0L) {
                val samples = minOf(count, intervals.size.toLong()).toInt()
                val average = intervals.take(samples).average() / 1_000_000.0
                val maximum = intervals.take(samples).maxOrNull()!!.toDouble() / 1_000_000.0
                val runtime = Runtime.getRuntime()
                val heap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                text = String.format(Locale.ROOT, "帧时 FPS %.1f\n平均 %.1f ms\n最大 %.1f ms\nJVM 堆 %d MB", 1000.0 / average, average, maximum, heap)
            }
        }
    }
    Text(text, modifier.testTag("performance-overlay").semantics {
        stateDescription = if (text == "帧时采样中…") "sampling" else "sampled"
    }.padding(top = 48.dp, end = 12.dp)
        .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(8.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
