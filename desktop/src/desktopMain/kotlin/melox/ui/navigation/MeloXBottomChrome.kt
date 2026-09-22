package melox.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.lerpDp
import melox.ui.foundation.smoothStep
import melox.ui.glass.MeloXSystemColors
import melox.ui.glass.meloXLiquidBottomBar
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.meloXLiquidTabSelection
import melox.ui.glass.publicdemo.PublicDampedDragAnimation
import melox.ui.glass.publicdemo.PublicInteractiveHighlight
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

/**
 * 1:1 port of Android MeloXBottomChrome (MeloXApp.kt lines 889–1218).
 *
 * Progress stages (smoothStep hermite):
 *   labelStage   0.00→0.32  label fade
 *   sizeStage    0.00→0.36  nav/search 64→48dp
 *   shrinkStage  0.25→0.82  nav width shrink, mini reposition
 *   dropStage    0.78→1.00  chrome height drop to 56dp
 *   expandedLayerAlpha  1−ss(0.43,0.72)
 *   compactLayerAlpha   ss(0.52,0.82)
 *
 * Dock: PublicDampedDragAnimation with pressedScale 78/56, tap mapping by
 * selection segment, drag-to-select with damped value. Selection lens is a
 * sibling overlay (not a child of the panel backdrop).
 */
@Composable
fun MeloXBottomChrome(
    selectedTab: AppTab,
    hasMedia: Boolean,
    minimized: Boolean,
    visibleRootTabs: List<AppTab>,
    modifier: Modifier = Modifier,
    onSelect: (AppTab) -> Unit,
    miniPlayer: @Composable (RowScope.(compactProgress: Float) -> Unit),
) {
    val dockScope = rememberCoroutineScope()

    val rawProgress by animateFloatAsState(
        targetValue = if (minimized) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 330f,
            visibilityThreshold = 0.001f,
        ),
        label = "melox-tab-minimize-progress",
    )
    val progress = rawProgress.coerceIn(0f, 1f)

    val labelStage = smoothStep(progress, 0.00f, 0.32f)
    val sizeStage = smoothStep(progress, 0.00f, 0.36f)
    val shrinkStage = smoothStep(progress, 0.25f, 0.82f)
    val dropStage = smoothStep(progress, 0.78f, 1.00f)

    val navHeight = lerpDp(64.dp, 48.dp, sizeStage)
    val searchSize = lerpDp(64.dp, 48.dp, sizeStage)
    val mediaReveal by animateFloatAsState(
        targetValue = if (hasMedia) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 360f),
        label = "melox-mini-player-reveal",
    )
    val expandedChromeHeight by animateDpAsState(
        targetValue = if (hasMedia) 124.dp else 64.dp,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 360f),
        label = "melox-chrome-height",
    )
    val chromeHeight = lerpDp(expandedChromeHeight, 56.dp, dropStage)
    val labelAlpha = 1f - labelStage
    val expandedLayerAlpha = 1f - smoothStep(progress, 0.43f, 0.72f)
    val compactLayerAlpha = smoothStep(progress, 0.52f, 0.82f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Mei keeps the navigation capsule 8dp above the bottom edge.
            .padding(bottom = 8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(chromeHeight),
        ) {
            val maxWidth = maxWidth
            val horizontalMargin = 12.dp
            val compactSize = 48.dp
            val expandedGap = 8.dp
            val compactGap = 8.dp
            val expandedNavWidth = maxWidth - horizontalMargin * 2 - expandedGap - 64.dp
            val navWidth = lerpDp(expandedNavWidth, compactSize, shrinkStage)
            val navShape = RoundedCornerShape(50)
            val primaryTabs = visibleRootTabs

            val desiredCompactMiniVisibleWidth =
                (maxWidth - horizontalMargin * 2 - compactSize * 2 - compactGap * 2)
                    .coerceAtLeast(80.dp)
            val compactMiniWrapperWidth = desiredCompactMiniVisibleWidth
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap
            val miniWrapperWidth = lerpDp(maxWidth - horizontalMargin * 2, compactMiniWrapperWidth, shrinkStage)
            val miniWrapperX = lerpDp(horizontalMargin, compactMiniWrapperX, shrinkStage)
            val miniLift = lerpDp(66.dp, 0.dp, shrinkStage) * mediaReveal

            if (hasMedia) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = miniWrapperX, y = -miniLift)
                        .width(miniWrapperWidth),
                    content = { miniPlayer(progress) },
                )
            }

            val dark = true
            // Mei uses the app's red accent for the selected tab.
            val selectionTint = MeloXSystemColors.Red.copy(alpha = 0.30f)
            val selectionBorder = Color.White.copy(alpha = 0.42f)

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = horizontalMargin)
                    .width(navWidth)
                    .height(navHeight),
            ) {
                val tabBarMaxWidthPx = constraints.maxWidth
                val density = androidx.compose.ui.platform.LocalDensity.current
                val selectionEdgeInset = 5.dp
                val selectionEdgeInsetPx = with(density) { selectionEdgeInset.toPx() }
                val selectionTravelWidthPx = (tabBarMaxWidthPx - selectionEdgeInsetPx * 2f).coerceAtLeast(1f)
                val tabCount = primaryTabs.size.coerceAtLeast(1)
                val selectionSegmentPx = selectionTravelWidthPx / tabCount.toFloat()
                val selectionWidth = (maxWidth - selectionEdgeInset * 2f) / tabCount.toFloat()
                val selectedIndex = primaryTabs.indexOfFirst { it == selectedTab }
                val dockExpanded = progress < 0.56f
                val currentProgress by rememberUpdatedState(progress)
                val currentSelectedTab by rememberUpdatedState(selectedTab)
                val currentPrimaryTabs by rememberUpdatedState(primaryTabs)
                val currentSelectionSegmentPx by rememberUpdatedState(selectionSegmentPx)
                val currentSelectionEdgeInsetPx by rememberUpdatedState(selectionEdgeInsetPx)
                val currentOnSelect by rememberUpdatedState(onSelect)
                val dampedDock = remember(tabCount) {
                    PublicDampedDragAnimation(
                        animationScope = dockScope,
                        initialValue = selectedIndex.coerceAtLeast(0).toFloat(),
                        valueRange = 0f..(tabCount - 1).toFloat(),
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        pressedScale = 78f / 56f,
                        onTap = { position ->
                            if (currentProgress < 0.56f) {
                                val index = ((position.x - currentSelectionEdgeInsetPx) / currentSelectionSegmentPx)
                                    .toInt()
                                    .coerceIn(0, tabCount - 1)
                                currentOnSelect(currentPrimaryTabs[index])
                            } else if (currentProgress >= 0.68f) {
                                currentOnSelect(currentSelectedTab)
                            }
                        },
                        onDragStopped = {
                            val target = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                            animateToValue(target.toFloat())
                            currentOnSelect(currentPrimaryTabs[target])
                        },
                        onDrag = { _, dragAmount ->
                            updateValue(
                                targetValue + dragAmount.x / currentSelectionSegmentPx.coerceAtLeast(1f),
                            )
                        },
                    )
                }
                LaunchedEffect(selectedIndex, dockExpanded) {
                    if (dockExpanded && selectedIndex >= 0) {
                        dampedDock.animateToValue(selectedIndex.toFloat())
                    }
                }
                val dockHighlight = remember(dockScope, dampedDock) {
                    PublicInteractiveHighlight(
                        animationScope = dockScope,
                        position = { size, _ ->
                            Offset(
                                currentSelectionEdgeInsetPx +
                                    (dampedDock.value + 0.5f) * currentSelectionSegmentPx,
                                size.height / 2f,
                            )
                        },
                    )
                }

                // Backdrop panel and moving optical element are siblings.
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(dockHighlight.gestureModifier)
                        .then(dampedDock.modifier),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .meloXLiquidBottomBar(
                                shape = navShape,
                                tint = bottomLiquidGlassTint(),
                                surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.18f),
                                pressProgress = dampedDock.pressProgress,
                            )
                            .then(dockHighlight.modifier),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            primaryTabs.forEach { tab ->
                                RootTabButton(
                                    title = tab.label,
                                    glyph = tab.toSymbol(),
                                    selected = selectedTab == tab,
                                    labelAlpha = labelAlpha,
                                    interactive = false,
                                    onClick = { onSelect(tab) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = expandedLayerAlpha }
                                .padding(horizontal = 5.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            primaryTabs.forEach { tab ->
                                RootTabButton(
                                    title = tab.label,
                                    glyph = tab.toSymbol(),
                                    selected = selectedTab == tab,
                                    labelAlpha = labelAlpha,
                                    interactive = dockExpanded,
                                    onClick = { onSelect(tab) },
                                )
                            }
                        }

                        if (progress > 0.50f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = compactLayerAlpha },
                                contentAlignment = Alignment.Center,
                            ) {
                                MeloXSymbolIcon(
                                    symbol = selectedTab.toSymbol(),
                                    size = 25,
                                    color = MeloXSystemColors.Red,
                                    )
                            }
                        }
                    }

                    val lensAlpha by animateFloatAsState(
                        targetValue = if (selectedIndex >= 0 && progress < 0.56f) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 440f),
                        label = "melox-tab-selection-alpha",
                    )
                    val lensVisibility = lensAlpha * expandedLayerAlpha
                    Box(
                        modifier = Modifier
                            // Sibling overlay, not content inside the panel
                            // backdrop: it can lift beyond one tab unclipped.
                            .width(selectionWidth)
                            .fillMaxHeight()
                            .offset {
                                IntOffset(
                                    x = (selectionEdgeInsetPx + dampedDock.value * selectionSegmentPx).roundToInt(),
                                    y = 0,
                                )
                            }
                            .padding(4.dp)
                            .meloXLiquidTabSelection(
                                shape = RoundedCornerShape(50),
                                selected = lensVisibility > 0.001f,
                                pressProgress = dampedDock.pressProgress,
                                scaleX = dampedDock.scaleX,
                                scaleY = dampedDock.scaleY,
                                velocity = dampedDock.velocity,
                                tint = selectionTint,
                            ),
                    )
                }
            }

            // Search capsule (bottom-end)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = -horizontalMargin)
                    .size(searchSize)
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(50),
                        tint = bottomLiquidGlassTint(),
                        blurRadius = 6.dp,
                        lensRadius = 12.dp,
                        refractionHeight = 18.dp,
                        surfaceColor = bottomGlassFallbackColor().copy(alpha = 0.16f),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(AppTab.Search) },
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Search,
                    size = lerpDp(28.dp, 27.dp, sizeStage).value.roundToInt(),
                    color = if (selectedTab == AppTab.Search) MeloXSystemColors.Red else MeloXColors.OnSurface,
                    )
            }
        }
    }
}

@Composable
private fun RowScope.RootTabButton(
    title: String,
    glyph: MeloXSymbol,
    selected: Boolean,
    labelAlpha: Float,
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MeloXSystemColors.Red
        } else {
            MeloXColors.OnSurface.copy(alpha = 0.78f)
        },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "melox-tab-foreground",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .then(
                if (interactive) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MeloXSymbolIcon(
            symbol = glyph,
            size = 24,
            color = foreground,
            )
        Text(
            text = title,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
        )
    }
}

@Composable
private fun bottomLiquidGlassTint(): Color =
    Color.Black.copy(alpha = 0.10f)  // dark theme

@Composable
private fun bottomGlassFallbackColor(): Color =
    MeloXColors.Surface.copy(alpha = 0.58f)  // dark theme

fun AppTab.toSymbol(): MeloXSymbol = when (this) {
    AppTab.Home -> MeloXSymbol.Home
    AppTab.Explore -> MeloXSymbol.Explore
    AppTab.Library -> MeloXSymbol.Library
    AppTab.Podcasts -> MeloXSymbol.Podcast      // Android: RadioWaves (dot.radiowaves.left.and.right)
    AppTab.Downloads -> MeloXSymbol.Download
    AppTab.Cloud -> MeloXSymbol.Drive           // Android: Storage (internaldrive)
    AppTab.Settings -> MeloXSymbol.Settings
    AppTab.Search -> MeloXSymbol.Search
}
