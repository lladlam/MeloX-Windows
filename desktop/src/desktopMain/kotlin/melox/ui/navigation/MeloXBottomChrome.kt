package melox.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import melox.ui.glass.MeloXShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.lerpDp
import melox.ui.foundation.smoothStep
import melox.ui.foundation.sprungFrac
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXChromeTokens
import melox.ui.glass.bottomBarTone
import melox.ui.glass.bottomGlassSurfaceColor
import melox.ui.glass.bottomLiquidGlassTint
import melox.ui.glass.MeloXSystemColors
import melox.ui.glass.meloXLiquidBottomBar
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.meloXLiquidTabSelection
import melox.ui.glass.meloXLiquidCaptureLayer
import melox.ui.glass.rememberMeloXLayerBackdrop
import melox.ui.glass.meloXRecordBackdrop
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import melox.ui.glass.publicdemo.PublicDampedDragAnimation
import melox.ui.glass.publicdemo.PublicInteractiveHighlight
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

/** Bottom chrome geometry and motion from Android main 94e17c9 MeloXApp.kt. */
@Composable
fun MeloXBottomChrome(
    selectedTab: AppTab,
    hasMedia: Boolean,
    minimized: Boolean,
    visibleRootTabs: List<AppTab>,
    split: Boolean = false,
    modifier: Modifier = Modifier,
    onSelect: (AppTab) -> Unit,
    miniPlayer: @Composable (compactProgress: Float) -> Unit,
) {
    val dockScope = rememberCoroutineScope()
    val navigationPosition = melox.settings.MeloXSettingsRuntime.splitNavigationPosition
    val miniPosition = melox.settings.MeloXSettingsRuntime.splitMiniPlayerPosition
    val coupled = melox.settings.meloXChromeScrollCoupled(split, navigationPosition, miniPosition)
    val inlineMini = !split || (coupled && !navigationPosition.isSideRail)

    val rawProgress by animateFloatAsState(
        targetValue = if (minimized && coupled) 1f else 0f,
        animationSpec = if (minimized) MeloXMotion.tabCollapseSpec() else MeloXMotion.tabExpandSpec(),
        label = "melox-tab-minimize-progress",
    )
    val progress = rawProgress.coerceIn(0f, 1f)
    val bounceFrac = sprungFrac(rawProgress)

    val labelStage = smoothStep(progress, 0.00f, 0.32f)
    val dropStage = smoothStep(progress, 0.78f, 1.00f)

    val navHeight = lerpDp(MeloXChromeTokens.ExpandedSize, MeloXChromeTokens.CompactSize, bounceFrac)
    val searchSize = lerpDp(MeloXChromeTokens.ExpandedSize, MeloXChromeTokens.CompactSize, bounceFrac.coerceAtLeast(0f))
    val mediaReveal by animateFloatAsState(
        targetValue = if (hasMedia && inlineMini) 1f else 0f,
        animationSpec = MeloXMotion.mediaRevealSpec(),
        label = "melox-mini-player-reveal",
    )
    val expandedChromeHeight = 64.dp + 60.dp * mediaReveal
    val chromeHeight = lerpDp(expandedChromeHeight, 56.dp, dropStage)
    val labelAlpha = 1f - labelStage
    val expandedLayerAlpha = 1f - smoothStep(progress, 0.43f, 0.72f)
    val compactLayerAlpha = smoothStep(progress, 0.52f, 0.82f)

    melox.ui.layout.MeloXPositionedChrome(split = split, hasMedia = hasMedia && !inlineMini,
        coupledProgress = if (coupled) progress else 0f, navigationPosition = navigationPosition, miniPlayerPosition = miniPosition,
        modifier = modifier, miniPlayer = {
            Box(Modifier.fillMaxWidth().padding(horizontal = if (miniPosition.isSideRail) 4.dp else 12.dp, vertical = 8.dp)) {
                miniPlayer(if (coupled) progress else 0f)
            }
        }) {
    if (split && navigationPosition.isSideRail) {
        MeloXVerticalNavigation(selectedTab, visibleRootTabs, onSelect, if (coupled) progress else 0f, hasMedia && coupled)
    } else {
    Column(
        modifier = Modifier
            .testTag("bottom-root-tabs").semantics { stateDescription = visibleRootTabs.joinToString(">") { it.name } }
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
            val horizontalMargin = MeloXChromeTokens.HorizontalMargin
            val compactSize = MeloXChromeTokens.CompactWidth
            val expandedGap = MeloXChromeTokens.SearchGap
            val compactGap = 8.dp
            val expandedNavWidth = maxWidth - horizontalMargin * 2 - expandedGap - MeloXChromeTokens.ExpandedSize
            val navWidth = lerpDp(expandedNavWidth, compactSize, bounceFrac).coerceAtLeast(1.dp)
            val navOffsetX = horizontalMargin - (navWidth - expandedNavWidth).coerceAtLeast(0.dp) * MeloXChromeTokens.NavExpandLeftShare
            val navShape = MeloXShapes.capsule
            val primaryTabs = visibleRootTabs

            val desiredCompactMiniVisibleWidth =
                (maxWidth - horizontalMargin * 2 - compactSize * 2 - compactGap * 2)
                    .coerceAtLeast(80.dp)
            val compactMiniWrapperWidth = desiredCompactMiniVisibleWidth
            val compactMiniWrapperX = horizontalMargin + compactSize + compactGap
            val miniWrapperWidth = lerpDp(maxWidth - horizontalMargin * 2, compactMiniWrapperWidth, bounceFrac).coerceAtLeast(1.dp)
            val miniWrapperX = lerpDp(horizontalMargin, compactMiniWrapperX, bounceFrac)
            val miniLift = lerpDp(66.dp, 0.dp, bounceFrac) * mediaReveal
            val miniCenterAlignNudge = lerpDp(0.dp, 1.dp, bounceFrac) * mediaReveal

            if (hasMedia && inlineMini) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = miniWrapperX, y = -miniLift + miniCenterAlignNudge)
                        .width(miniWrapperWidth)
                        .zIndex(1f),
                    content = { miniPlayer(progress) },
                )
            }

            val tone = bottomBarTone()
            val dark = tone > 0.5f
            val selectionTint = androidx.compose.ui.graphics.lerp(Color.Black.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f), tone)
            val frostedGlass = melox.settings.MeloXSettingsRuntime.frostedGlassEnabled

            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = navOffsetX)
                    .width(navWidth)
                    .height(navHeight),
            ) {
                val tabBarMaxWidthPx = constraints.maxWidth
                val density = androidx.compose.ui.platform.LocalDensity.current
                val selectionEdgeInset = 10.dp
                val selectionEdgeInsetPx = with(density) { selectionEdgeInset.toPx() }
                val selectionTravelWidthPx = (tabBarMaxWidthPx - selectionEdgeInsetPx * 2f).coerceAtLeast(1f)
                val tabCount = primaryTabs.size.coerceAtLeast(1)
                val selectionSegmentPx = selectionTravelWidthPx / tabCount.toFloat()
                val selectionWidth = (maxWidth - selectionEdgeInset * 2f) / tabCount.toFloat()
                val dropletHeight = (navHeight - 8.dp).coerceAtLeast(1.dp)
                val dropletWidth = (selectionWidth * 1.15f + 2.dp)
                    .coerceAtMost(selectionWidth + 12.dp).coerceAtLeast(1.dp)
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
                val tabsBackdrop = rememberMeloXLayerBackdrop()

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
                                surfaceColor = bottomGlassSurfaceColor(),
                                pressProgress = dampedDock.pressProgress,
                                dark = dark,
                            )
                            .then(dockHighlight.modifier),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = expandedLayerAlpha }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            primaryTabs.forEach { tab ->
                                RootTabButton(
                                    title = tab.label,
                                    glyph = tab.toSymbol(),
                                    selected = frostedGlass && selectedTab == tab,
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
                                     .graphicsLayer { alpha = compactLayerAlpha }
                                     .zIndex(2f)
                                     .then(if (compactLayerAlpha > 0.5f) Modifier.clickable { onSelect(selectedTab) } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                MeloXSymbolIcon(
                                    symbol = if (selectedTab == AppTab.Search) MeloXSymbol.Home else selectedTab.toSymbol(),
                                    size = 25,
                                    color = if (selectedTab == AppTab.Search) MaterialTheme.colorScheme.onSurface else MeloXSystemColors.Red,
                                    )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .height(dropletHeight)
                            .clearAndSetSemantics { }
                            .meloXRecordBackdrop(tabsBackdrop, visible = false)
                             .meloXLiquidCaptureLayer(navShape, bottomGlassSurfaceColor(), dampedDock.pressProgress)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryTabs.forEach { tab ->
                            RootTabButton(
                                title = tab.label,
                                glyph = tab.toSymbol(),
                                selected = true,
                                labelAlpha = labelAlpha,
                                interactive = false,
                                scale = 1f + 0.2f * dampedDock.pressProgress,
                                onClick = { onSelect(tab) },
                            )
                        }
                    }

                    val lensAlpha by animateFloatAsState(
                        targetValue = if (selectedIndex >= 0 && progress < 0.56f) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 180f),
                        label = "melox-tab-selection-alpha",
                    )
                    val lensVisibility = lensAlpha * expandedLayerAlpha
                    if (lensVisibility > 0.001f && !frostedGlass) Box(
                        modifier = Modifier
                            // Sibling overlay, not content inside the panel
                            // backdrop: it can lift beyond one tab unclipped.
                            .width(dropletWidth)
                             .height(dropletHeight)
                             .graphicsLayer { alpha = lensVisibility }
                            .offset {
                                IntOffset(
                                    x = (selectionEdgeInsetPx + (dampedDock.value + 0.5f) * selectionSegmentPx - with(density) { dropletWidth.toPx() } / 2f).roundToInt(),
                                    y = with(density) { 4.dp.toPx() }.roundToInt(),
                                )
                            }
                            .meloXLiquidTabSelection(
                                 shape = MeloXShapes.capsule,
                                 selected = lensVisibility > 0.001f,
                                 panelBackdrop = tabsBackdrop,
                                pressProgress = dampedDock.pressProgress,
                                scaleX = dampedDock.scaleX,
                                scaleY = dampedDock.scaleY,
                                velocity = dampedDock.velocity,
                                 tint = selectionTint,
                                 dark = dark,
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
                    .meloXLiquidBottomBar(
                        shape = MeloXShapes.capsule,
                        tint = bottomLiquidGlassTint(),
                        refractionHeight = lerpDp(10.dp, 8.dp, bounceFrac),
                        surfaceColor = bottomGlassSurfaceColor(),
                        dark = dark,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(AppTab.Search) },
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Search,
                    size = lerpDp(28.dp, 27.dp, bounceFrac).value.roundToInt(),
                    color = if (selectedTab == AppTab.Search) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface,
                    )
            }
        }
    }
    }
    }
}

@Composable
private fun MeloXVerticalNavigation(selectedTab: AppTab, tabs: List<AppTab>, onSelect: (AppTab) -> Unit,
    compactProgress: Float, reserveMiniSlot: Boolean) {
    Box(Modifier.fillMaxWidth().testTag("bottom-root-tabs").semantics { stateDescription = tabs.joinToString(">") { it.name } }
        .padding(horizontal = 4.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().meloXLiquidBottomBar(shape = MeloXShapes.capsule,
            tint = bottomLiquidGlassTint(), surfaceColor = bottomGlassSurfaceColor())
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            (if (compactProgress > .5f) listOf(if (selectedTab == AppTab.Search) tabs.firstOrNull() ?: AppTab.Settings else selectedTab,
                AppTab.Search) else tabs + AppTab.Search).distinct().forEach { tab ->
                if (tab == AppTab.Search && compactProgress > .5f && reserveMiniSlot) Spacer(Modifier.height(56.dp))
                val color = if (tab == selectedTab) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface
                Column(Modifier.fillMaxWidth().height(56.dp).testTag("root-tab:${tab.name}")
                    .clickable { onSelect(tab) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    MeloXSymbolIcon(tab.toSymbol(), size = 24, color = color)
                    Text(tab.label, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = color)
                }
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
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MeloXSystemColors.Red
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        },
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 480f),
        label = "melox-tab-foreground",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
