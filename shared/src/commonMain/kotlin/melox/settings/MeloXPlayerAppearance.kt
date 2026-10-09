package melox.settings

enum class MeloXPlayerBackdropKind { FlowingLight, AppleLyrics, BlurredArtwork, MeiMesh }

enum class MeloXResolvedPlayerShell { AppleMusicPages, ClassicColumn }

fun playerBackdropKind(mode: MeloXPlayerBackgroundMode): MeloXPlayerBackdropKind = when (mode) {
    MeloXPlayerBackgroundMode.FlowingLight -> MeloXPlayerBackdropKind.FlowingLight
    MeloXPlayerBackgroundMode.AppleLyrics -> MeloXPlayerBackdropKind.AppleLyrics
    MeloXPlayerBackgroundMode.BlurredArtwork -> MeloXPlayerBackdropKind.BlurredArtwork
    MeloXPlayerBackgroundMode.MeiMesh -> MeloXPlayerBackdropKind.MeiMesh
}

fun resolvedPlayerShell(shell: MeloXPlayerShell): MeloXResolvedPlayerShell = when (shell) {
    MeloXPlayerShell.AppleMusic -> MeloXResolvedPlayerShell.AppleMusicPages
    MeloXPlayerShell.Classic -> MeloXResolvedPlayerShell.ClassicColumn
}

fun playerShowsLyricsArtworkBackdrop(mode: MeloXPlayerBackgroundMode, lyricsPage: Boolean): Boolean =
    lyricsPage && mode == MeloXPlayerBackgroundMode.AppleLyrics

enum class MeloXResolvedTheme { Dark, Light }

fun resolveMeloXTheme(mode: MeloXThemeMode, systemDark: Boolean?): MeloXResolvedTheme = when (mode) {
    MeloXThemeMode.Dark -> MeloXResolvedTheme.Dark
    MeloXThemeMode.Light -> MeloXResolvedTheme.Light
    MeloXThemeMode.System -> if (systemDark == false) MeloXResolvedTheme.Light else MeloXResolvedTheme.Dark
}

data class MeloXGlassDraw(
    val blurPx: Float,
    val lensShiftPx: Float,
    val refractionPx: Float,
    val highlightAlpha: Float,
)

fun meloXGlassDraw(
    blurRadiusPx: Float,
    lensRadiusPx: Float,
    refractionHeightPx: Float,
    dark: Boolean,
): MeloXGlassDraw {
    val blur = blurRadiusPx.coerceAtLeast(0f)
    val lens = lensRadiusPx.coerceAtLeast(0f)
    val refraction = refractionHeightPx.coerceAtLeast(0f)
    val shift = lens * 0.18f + refraction * 0.42f
    val highlight = (if (dark) 0.32f else 0.48f) + (refraction / 64f).coerceIn(0f, 0.28f)
    return MeloXGlassDraw(blur, shift, refraction, highlight.coerceAtMost(1f))
}

fun playerIsolationAlpha(enabled: Boolean, expansion: Float = 1f): Float {
    if (!enabled) return 0f
    return isolationSmoothStep(expansion, 0.34f, 0.82f)
}

/**
 * Player expansion (1 = fully open, 0 = collapsed) for the interactive collapse gesture.
 *
 * [androidx.compose.animation.core.SeekableTransitionState.fraction] is the progress from
 * `currentState` to `targetState`, so its direction depends on which endpoint is open:
 *
 * - Collapsing (`currentExpanded = true`, `targetExpanded = false`): `fraction` grows from 0 to 1
 *   with the downward drag, in the same direction as the gesture progress. Expansion is therefore
 *   its complement, `1 - fraction`, and fades as the player is dragged away.
 * - Expanding (`currentExpanded = false`, `targetExpanded = true`): `fraction` already grows from 0
 *   to 1 with expansion, so it is used directly.
 * - Settled (`currentExpanded == targetExpanded`): `fraction` resets to 0, so the settled endpoint
 *   decides the value instead of `1 - fraction`, which would otherwise jump to the opposite extreme
 *   the moment the transition finishes.
 */
fun meloXPlayerExpansion(
    currentExpanded: Boolean,
    targetExpanded: Boolean,
    fraction: Float,
): Float {
    if (currentExpanded == targetExpanded) return if (currentExpanded) 1f else 0f
    val progress = fraction.coerceIn(0f, 1f)
    return if (currentExpanded) 1f - progress else progress
}

internal fun isolationSmoothStep(value: Float, start: Float, end: Float): Float {
    if (start == end) return if (value < start) 0f else 1f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
