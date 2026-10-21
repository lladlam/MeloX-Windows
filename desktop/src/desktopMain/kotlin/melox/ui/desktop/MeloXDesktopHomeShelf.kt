package melox.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.navigation.MeloXPointerMode
import melox.ui.screens.MeloXArtworkImage
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.abs

/** One real scroll sequence; the inspector occludes its trailing part without dropping items. */
@Composable
internal fun <T> DesktopHomePagingShelf(values: List<T>, itemKey: (T) -> Any, width: Dp, spacing: Dp,
    reserved: Int, tag: String, card: @Composable (T) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val onVerticalWheel by rememberUpdatedState(LocalDesktopHomeVerticalWheel.current)
    val hovered by interaction.collectIsHoveredAsState()
    val stride = with(LocalDensity.current) { (width + spacing).toPx() }
    val reservedPx = with(LocalDensity.current) { reserved.dp.toPx() }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val info = state.layoutInfo
    val count = floor((info.viewportSize.width - reservedPx + spacingPx) / stride).toInt().coerceAtLeast(1)
    val leading = (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / stride).roundToInt()
    val last = (values.size - count).coerceAtLeast(0)
    LaunchedEffect(values.map(itemKey), count) {
        if (state.firstVisibleItemIndex > last) state.scrollToItem(last)
    }
    fun page(direction: Int) {
        val target = if (direction < 0) ((leading - 1).coerceAtLeast(0) / count) * count
            else (((leading / count) + 1) * count).coerceAtMost(last)
        scope.launch {
            if (MeloXSettingsRuntime.reduceMotion) state.scrollToItem(target)
            else state.animateScrollBy((target - state.firstVisibleItemIndex) * stride - state.firstVisibleItemScrollOffset,
                tween(280))
        }
    }
    Box(Modifier.fillMaxWidth().testTag("desktop-home-shelf:$tag").semantics {
        stateDescription = "first:${state.firstVisibleItemIndex};offset:${state.firstVisibleItemScrollOffset};page-size:$count"
    }.hoverable(interaction).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Scroll) {
                    val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                    val scrollParent = onVerticalWheel
                    // Desktop LazyRow maps an ordinary vertical wheel to horizontal scrolling.
                    // Route that wheel to Home; horizontal wheels and touch drags stay native.
                    if (scrollParent != null && abs(delta.y) > abs(delta.x)) {
                        scrollParent(delta.y)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }) {
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(spacing), contentPadding = PaddingValues(end = reserved.dp)) {
            items(values, key = itemKey) { value -> Box(Modifier.width(width)) { card(value) } }
        }
        val show = hovered || MeloXPointerMode.touch
        if (show && state.canScrollBackward) HomePageButton(false, "desktop-home-page:$tag:previous",
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { page(-1) }
        if (show && state.canScrollForward) HomePageButton(true, "desktop-home-page:$tag:next",
            Modifier.align(Alignment.CenterEnd).padding(end = (reserved + 8).dp)) { page(1) }
    }
}

@Composable
private fun HomePageButton(next: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(28.dp, 44.dp).shadow(8.dp, CircleShape).clip(CircleShape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = .92f)).border(.7.dp, Color.White.copy(alpha = .34f), CircleShape)
        .testTag(tag).semantics { contentDescription = desktopString(if (next) "ui.desktop.pagination.next" else "ui.desktop.pagination.previous") }
        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(if (next) MeloXSymbol.ChevronRight else MeloXSymbol.ChevronLeft, size = 15,
            weight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
    }
}

@Composable
internal fun DesktopHomeMediaCard(title: String, subtitle: String, artwork: String?, width: Dp, tag: String, playCount: Long = 0,
    onOpen: () -> Unit, onPlay: (() -> Unit)? = null, isCircular: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val duration = if (MeloXSettingsRuntime.reduceMotion) 0 else 240
    val scale by animateFloatAsState(if (hovered) 1.018f else 1f, tween(duration), label = "home-media-hover")
    Column(Modifier.width(width).hoverable(interaction), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width)) {
            MeloXArtworkImage(artwork, MaterialTheme.colorScheme.surfaceVariant, Modifier.fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }.clip(if (isCircular) CircleShape else ContinuousRoundedRectangle(9.dp))
                .testTag(tag).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onOpen))
            if (MeloXSettingsRuntime.showPlaylistPlayCount && playCount > 0) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = .48f)).padding(horizontal = 7.dp, vertical = 5.dp)) {
                DesktopHomePlayCount(playCount)
            }
            if (onPlay != null) androidx.compose.animation.AnimatedVisibility(hovered || MeloXPointerMode.touch,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                enter = fadeIn(tween(duration)) + scaleIn(tween(duration)), exit = fadeOut(tween(duration)) + scaleOut(tween(duration))) {
                Box(Modifier.size(42.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Color(0xFFFF3B30))
                    .testTag("$tag:play").semantics { contentDescription = desktopFormat("ui.common.play_named", title) }
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onPlay), contentAlignment = Alignment.Center) {
                    MeloXSymbolIcon(MeloXSymbol.Play, size = 17, weight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DesktopHomePlayCount(count: Long) {
    val text = remember(count) { NumberFormat.getCompactNumberInstance(Locale.getDefault(), NumberFormat.Style.SHORT).format(count) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MeloXSymbolIcon(MeloXSymbol.Play, size = 9, weight = FontWeight.SemiBold, color = Color.White.copy(alpha = .92f))
        Text(text, color = Color.White.copy(alpha = .92f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
