package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import melox.lyrics.*
import melox.music.model.MusicTrack
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import melox.settings.MeloXLyricsStyle
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXTextPVStyle
import kotlin.math.PI
import kotlin.math.sin

private data class AlternativeLyricsState(val lines: List<LyricLine>, val index: Int, val loading: Boolean, val error: String?)

/** All presentations consume the existing provider-first/AMLL loader and real output clock. */
@Composable
private fun rememberAlternativeLyrics(track: MusicTrack, active: Boolean, authoredOnly: Boolean = false): AlternativeLyricsState {
    val loaded = LocalMeloXPlayerLyrics.current ?: rememberMeloXPlayerLyrics(track)
    val document = remember(loaded.document, authoredOnly, MeloXSettingsRuntime.lyricPseudoTimingEnabled) {
        loaded.document?.let { if (!authoredOnly && MeloXSettingsRuntime.lyricPseudoTimingEnabled) it.withPseudoTiming() else it }
    }
    val position by melox.playback.MeloXPlaybackOutput.positionMs.collectAsState()
    val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val duration by melox.playback.MeloXPlaybackOutput.durationMs.collectAsState()
    val lines = document?.lines.orEmpty()
    val advance = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, rememberBilibiliLyricOffset(track))
    var index by remember(track.id, document) { mutableIntStateOf(document?.highlightedIndex(position + advance)?.coerceIn(0, lines.lastIndex.coerceAtLeast(0)) ?: 0) }
    LaunchedEffect(active, track.id, document, position, playback.isPlaying, duration, advance) {
        val current = document ?: return@LaunchedEffect
        if (!active || lines.isEmpty()) return@LaunchedEffect
        if (!playback.isPlaying) {
            index = current.highlightedIndex(position + advance)?.coerceIn(0, lines.lastIndex) ?: index
            return@LaunchedEffect
        }
        val anchor = System.nanoTime()
        while (isActive) {
            val rendered = (position + (System.nanoTime() - anchor) / 1_000_000).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
            val next = current.highlightedIndex(rendered + advance)?.coerceIn(0, lines.lastIndex) ?: index
            index = next
            val event = lines.getOrNull(next + 1)?.timeMs ?: LyricTimelineProcessor.nextEventTimeMs(current, rendered + advance)
            kotlinx.coroutines.delay((event?.minus(rendered + advance) ?: 250L).coerceIn(16L, 500L))
        }
    }
    return AlternativeLyricsState(lines, index, loaded.loading, loaded.error)
}

@Composable
internal fun MeloXAlternativeLyricsPanel(track: MusicTrack, modifier: Modifier = Modifier, onInteraction: () -> Unit = {}, active: Boolean = true) {
    val style = MeloXSettingsRuntime.lyricsStyle
    val state = rememberAlternativeLyrics(track, active, authoredOnly = style == MeloXLyricsStyle.TextPV)
    val loaded = LocalMeloXPlayerLyrics.current
    val tagged = modifier.testTag("player-lyrics").semantics {
        stateDescription = style.name
        this[MeloXLyricWordLinesKey] = state.lines.count { it.syllables.isNotEmpty() }
        this[MeloXLyricDocumentKey] = loaded?.document?.let { "${it.source}:${it.quality};lines=${it.lines.size};pseudoTimingAllowed=${it.pseudoTimingAllowed}" } ?: "loading"
    }
    if (style == MeloXLyricsStyle.Eva) {
        Box(tagged.fillMaxSize().clickable(onClick = onInteraction), Alignment.Center) {
            AlternativeLoading(state)
            if (state.lines.isNotEmpty()) AnimatedContent(state.index,
                transitionSpec = {
                    (slideInVertically(tween(520, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(420, delayMillis = 80)) + scaleIn(tween(520), initialScale = .92f)) togetherWith
                        (slideOutVertically(tween(360)) { -it / 4 } + fadeOut(tween(280)) + scaleOut(targetScale = 1.06f))
                }, label = "eva-lyric-composition") { index ->
                state.lines.getOrNull(index)?.let { line -> EvaComposition(line, state.lines.getOrNull(index - 1), state.lines.getOrNull(index + 1), index) { PlaybackCommands.playerController()?.seekTo(line.timeMs) } }
            }
        }
    } else {
        val phase = remember { Animatable(0f) }
        val speed = MeloXSettingsRuntime.textPVAnimationSpeed
        val intensity = if (!active || MeloXSettingsRuntime.lyricReduceMotion) 0f else MeloXSettingsRuntime.textPVMotionIntensity
        LaunchedEffect(active, speed, intensity) {
            if (!active || speed <= 0 || intensity <= 0) { phase.snapTo(0f); return@LaunchedEffect }
            while (isActive) { phase.animateTo(1f, tween((14_000f / speed).toInt().coerceIn(3500, 140000), easing = FastOutSlowInEasing)); phase.snapTo(0f) }
        }
        Box(tagged.fillMaxSize().clickable(onClick = onInteraction)) {
            TextPVBackground(MeloXSettingsRuntime.textPVStyle, { phase.value }, intensity)
            AlternativeLoading(state)
            if (state.lines.isNotEmpty()) AnimatedContent(state.index, Modifier.fillMaxSize(),
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(tween(520), initialScale = .82f)) togetherWith (fadeOut(tween(240)) + scaleOut(tween(360), targetScale = 1.18f)) }, label = "text-pv-line") { index ->
                state.lines.getOrNull(index)?.let { line -> TextPVComposition(line, state.lines.getOrNull(index + 1), MeloXSettingsRuntime.textPVStyle, { phase.value }, intensity, index) { PlaybackCommands.playerController()?.seekTo(line.timeMs) } }
            }
        }
    }
}

@Composable
private fun EvaComposition(line: LyricLine, previous: LyricLine?, next: LyricLine?, lineIndex: Int, onSeek: () -> Unit) {
    val length = line.text.codePointCount(0, line.text.length)
    Column(Modifier.fillMaxWidth().testTag("lyric-line-$lineIndex").semantics { this[MeloXLyricTimeMsKey] = line.timeMs }
        .padding(horizontal = 30.dp).clickable(onClick = onSeek), verticalArrangement = Arrangement.Center) {
        previous?.let { Text(it.text.uppercase(), color = Color.White.copy(alpha = .20f), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.rotate(-2f)) }
        Spacer(Modifier.size(18.dp))
        when {
            length <= 7 -> Text(line.text, color = Color.White, fontSize = 52.sp, lineHeight = 55.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, modifier = Modifier.rotate(-1.5f))
            length <= 18 -> Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.size(width = 5.dp, height = 48.dp).background(Color.White)); Spacer(Modifier.width(14.dp))
                Text(line.text, color = Color.White, fontSize = 38.sp, lineHeight = 43.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            }
            else -> Text(line.text, color = Color.White, fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Justify)
        }
        if (MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank()) Text(line.romanization.orEmpty(), color = Color.White.copy(alpha = .48f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        if (MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank()) Text(line.translation.orEmpty(), color = Color.White.copy(alpha = .72f), fontSize = 16.sp, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 7.dp))
        Spacer(Modifier.size(24.dp))
        next?.let { Text(it.text, color = Color.White.copy(alpha = .28f), fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 38.dp).rotate(1f)) }
    }
}

private enum class TextPVPattern { BlueRays, SplitSlash, PlaneBlocks, Grunge, Geometry, Rain, Hud, Film, Radial, Web, Ink, Villain, Clouds, Gingham, Stars, Pixels, CrimeTape, Petals }
private data class TextPVVisualSpec(val pattern: TextPVPattern, val background: Color, val accent: Color, val secondary: Color, val foreground: Color,
    val alignment: Alignment, val textAlign: TextAlign, val fontSize: Float, val lineHeight: Float, val weight: FontWeight, val rotation: Float,
    val letterSpacing: Float, val uppercase: Boolean, val marker: String?, val nextOpacity: Float, val motionX: Float, val motionY: Float, val pulse: Float)

private fun MeloXTextPVStyle.visualSpec(): TextPVVisualSpec = when (this) {
    MeloXTextPVStyle.BlueBold -> TextPVVisualSpec(TextPVPattern.BlueRays, Color(0xFF123A91), Color(0xFF8CC8FF), Color.White, Color.White, Alignment.CenterStart, TextAlign.Start, 50f, 54f, FontWeight.Black, -2f, -.4f, false, null, .18f, 16f, 0f, .018f)
    MeloXTextPVStyle.KineticSplit -> TextPVVisualSpec(TextPVPattern.SplitSlash, Color(0xFFF0E6D5), Color(0xFF8E1832), Color.Black, Color(0xFF15110F), Alignment.CenterEnd, TextAlign.End, 47f, 51f, FontWeight.ExtraBold, -5f, -.8f, false, null, .28f, -22f, 8f, .012f)
    MeloXTextPVStyle.BluePlane -> TextPVVisualSpec(TextPVPattern.PlaneBlocks, Color(0xFF0C2B68), Color(0xFF46A8FF), Color(0xFFBBD9FF), Color.White, Alignment.TopStart, TextAlign.Start, 44f, 48f, FontWeight.Bold, 0f, 1.2f, true, "PLANE", .24f, 28f, 18f, .008f)
    MeloXTextPVStyle.CyberGrunge -> TextPVVisualSpec(TextPVPattern.Grunge, Color(0xFF071318), Color(0xFF76E8FF), Color(0xFFFF4B89), Color(0xFFDBFAFF), Alignment.BottomStart, TextAlign.Start, 43f, 47f, FontWeight.Black, -1f, 1.4f, true, "GRUNGE", .16f, 12f, -10f, .022f)
    MeloXTextPVStyle.Geometric -> TextPVVisualSpec(TextPVPattern.Geometry, Color(0xFFF5C928), Color.Black, Color.White, Color.Black, Alignment.Center, TextAlign.Center, 39f, 44f, FontWeight.Black, 0f, -.6f, false, null, .30f, 0f, -16f, .026f)
    MeloXTextPVStyle.RainCity -> TextPVVisualSpec(TextPVPattern.Rain, Color(0xFF05131B), Color(0xFF3DFFB5), Color(0xFF8BE9FD), Color(0xFFD7FFF0), Alignment.BottomCenter, TextAlign.Center, 40f, 45f, FontWeight.Medium, 0f, 2.1f, false, "CITY RAIN", .13f, 0f, 22f, .004f)
    MeloXTextPVStyle.CyberpunkHUD -> TextPVVisualSpec(TextPVPattern.Hud, Color(0xFF090C15), Color(0xFFFFD23F), Color(0xFF00E5FF), Color(0xFFFFF1A8), Alignment.CenterEnd, TextAlign.End, 38f, 43f, FontWeight.Bold, 0f, 1.8f, true, "HUD", .20f, -18f, 0f, .010f)
    MeloXTextPVStyle.EmotionCinema -> TextPVVisualSpec(TextPVPattern.Film, Color(0xFF24334A), Color(0xFF9CC8FF), Color(0xFFF1C9AE), Color.White, Alignment.BottomStart, TextAlign.Start, 41f, 49f, FontWeight.SemiBold, 0f, .1f, false, "SCENE", .34f, 0f, -12f, .006f)
    MeloXTextPVStyle.HystericNight -> TextPVVisualSpec(TextPVPattern.Radial, Color(0xFF180A25), Color(0xFFE46CFF), Color(0xFFFF477E), Color.White, Alignment.Center, TextAlign.Center, 46f, 48f, FontWeight.Black, 4f, -.9f, true, null, .14f, 18f, 18f, .040f)
    MeloXTextPVStyle.SpiderWeb -> TextPVVisualSpec(TextPVPattern.Web, Color(0xFF07090F), Color(0xFFE7ECFF), Color(0xFF8C52FF), Color.White, Alignment.TopEnd, TextAlign.End, 42f, 47f, FontWeight.ExtraBold, 2f, .6f, false, "WEB", .17f, -14f, 12f, .014f)
    MeloXTextPVStyle.StaggeredText -> TextPVVisualSpec(TextPVPattern.Ink, Color(0xFFEBE7DD), Color(0xFF151515), Color(0xFFE84A5F), Color(0xFF151515), Alignment.CenterStart, TextAlign.Start, 54f, 56f, FontWeight.Black, -7f, -1.2f, false, null, .38f, 32f, -12f, .030f)
    MeloXTextPVStyle.CalmVillain -> TextPVVisualSpec(TextPVPattern.Villain, Color(0xFF17202A), Color(0xFFC6A15B), Color(0xFF66788A), Color(0xFFF1E9DC), Alignment.CenterStart, TextAlign.Start, 39f, 47f, FontWeight.Light, 0f, 2.8f, true, "ACT I", .26f, 5f, 0f, .002f)
    MeloXTextPVStyle.GirlyClouds -> TextPVVisualSpec(TextPVPattern.Clouds, Color(0xFFF3A9C3), Color.White, Color(0xFFFFE4EF), Color.White, Alignment.Center, TextAlign.Center, 38f, 43f, FontWeight.Bold, -1f, .3f, false, null, .32f, 12f, -12f, .020f)
    MeloXTextPVStyle.SweetPink -> TextPVVisualSpec(TextPVPattern.Gingham, Color(0xFFFFD0E1), Color(0xFFFF5E91), Color.White, Color(0xFF9B2851), Alignment.Center, TextAlign.Center, 37f, 42f, FontWeight.Black, 0f, .8f, false, "SWEET", .28f, -8f, 8f, .016f)
    MeloXTextPVStyle.FlyMeToTheMoon -> TextPVVisualSpec(TextPVPattern.Stars, Color(0xFF080D24), Color(0xFFBFCBFF), Color(0xFF6F81C7), Color.White, Alignment.Center, TextAlign.Center, 36f, 45f, FontWeight.Light, 0f, 3.2f, true, "ORBIT", .24f, 0f, -24f, .008f)
    MeloXTextPVStyle.KawaiiPixel -> TextPVVisualSpec(TextPVPattern.Pixels, Color(0xFFB8F1EA), Color(0xFFFF79AE), Color.White, Color(0xFF285A64), Alignment.BottomEnd, TextAlign.End, 40f, 42f, FontWeight.Black, 0f, 0f, false, "READY!", .30f, -16f, -6f, .012f)
    MeloXTextPVStyle.CrimeScene -> TextPVVisualSpec(TextPVPattern.CrimeTape, Color(0xFFE3D8C1), Color(0xFFB1182B), Color.Black, Color(0xFF17110D), Alignment.CenterStart, TextAlign.Start, 45f, 49f, FontWeight.Black, -3f, -.5f, true, "EVIDENCE", .22f, 24f, 6f, .018f)
    MeloXTextPVStyle.Haruhikage -> TextPVVisualSpec(TextPVPattern.Petals, Color(0xFFB9CAE3), Color(0xFFFFEFF7), Color(0xFF6D7FA0), Color(0xFF26364F), Alignment.BottomStart, TextAlign.Start, 37f, 46f, FontWeight.Medium, 0f, .4f, false, "春日影", .35f, 8f, -18f, .003f)
    MeloXTextPVStyle.Dynamic -> MeloXTextPVStyle.BlueBold.visualSpec()
    MeloXTextPVStyle.Minimal -> MeloXTextPVStyle.CalmVillain.visualSpec()
    MeloXTextPVStyle.Cyber -> MeloXTextPVStyle.CyberGrunge.visualSpec()
}

@Composable
private fun TextPVBackground(style: MeloXTextPVStyle, phaseProvider: () -> Float, motionIntensity: Float) {
    val spec = remember(style) { style.visualSpec() }
    Canvas(Modifier.fillMaxSize()) {
        if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) return@Canvas
        val motion = phaseProvider() * motionIntensity
        drawRect(spec.background.copy(alpha = .78f))
        when (spec.pattern) {
            TextPVPattern.BlueRays -> repeat(8) { i -> val x = size.width * ((i * .17f + motion * .12f) % 1.2f - .1f); drawLine(spec.accent.copy(alpha = .12f), Offset(x, 0f), Offset(x - size.height * .22f, size.height), 3f) }
            TextPVPattern.SplitSlash -> {
                drawLine(spec.accent.copy(alpha = .85f), Offset(-size.width * .1f, size.height * (.75f - motion * .12f)), Offset(size.width * 1.1f, size.height * (.25f + motion * .12f)), size.minDimension * .07f)
                drawLine(spec.secondary.copy(alpha = .22f), Offset(0f, size.height * .28f), Offset(size.width, size.height * .08f), 5f)
            }
            TextPVPattern.PlaneBlocks -> repeat(5) { i -> drawRect(spec.accent.copy(alpha = .07f + i * .025f), Offset(size.width * (.06f + i * .18f), size.height * ((i * .14f + motion * .08f) % .8f)), Size(size.width * (.18f + i * .045f), size.height * .28f)) }
            TextPVPattern.Grunge -> repeat(22) { i -> val y = size.height * ((i * .071f + motion * .09f) % 1f); drawLine(if (i % 2 == 0) spec.accent.copy(alpha = .11f) else spec.secondary.copy(alpha = .09f), Offset(0f, y), Offset(size.width * (.35f + (i % 7) * .1f), y + (i % 3 - 1) * 7f), 1f + i % 4) }
            TextPVPattern.Geometry -> repeat(5) { i -> val inset = size.minDimension * (.08f + i * .07f); drawRect(spec.accent.copy(alpha = .08f + i * .025f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(3f)) }
            TextPVPattern.Rain -> repeat(32) { i -> val x = size.width * ((i * .137f) % 1f); val y = size.height * ((i * .221f + motion * .45f) % 1.15f - .1f); drawLine(spec.accent.copy(alpha = .16f), Offset(x, y), Offset(x - 7, y + 46 + i % 5 * 8), 2f) }
            TextPVPattern.Hud -> { drawRect(spec.accent.copy(alpha = .18f), Offset(size.width * .06f, size.height * .08f), Size(size.width * .88f, size.height * .84f), style = Stroke(3f)); repeat(7) { i -> drawLine(spec.secondary.copy(alpha = .12f), Offset(size.width * .58f, size.height * (.16f + i * .09f)), Offset(size.width * (.92f - motion * .03f), size.height * (.16f + i * .09f)), 2f) } }
            TextPVPattern.Film -> { drawRect(Color.Black.copy(alpha = .24f), Offset.Zero, Size(size.width, size.height * .11f)); drawRect(Color.Black.copy(alpha = .24f), Offset(0f, size.height * .89f), Size(size.width, size.height * .11f)); drawCircle(spec.accent.copy(alpha = .10f), size.minDimension * .34f, Offset(size.width * (.28f + motion * .05f), size.height * .48f)) }
            TextPVPattern.Radial -> repeat(12) { i -> val a = i * PI.toFloat() / 6 + motion * .45f; drawLine(spec.accent.copy(alpha = .075f), center, Offset(center.x + kotlin.math.cos(a) * size.maxDimension, center.y + sin(a) * size.maxDimension), size.minDimension * .022f) }
            TextPVPattern.Web -> { repeat(10) { i -> val a = i * PI.toFloat() / 5 + motion * .08f; drawLine(spec.accent.copy(alpha = .12f), Offset(size.width, 0f), Offset(size.width + kotlin.math.cos(a) * size.maxDimension, sin(a) * size.maxDimension), 1.5f) }; repeat(4) { i -> drawCircle(spec.secondary.copy(alpha = .10f), size.minDimension * (.16f + i * .12f), Offset(size.width, 0f), style = Stroke(2f)) } }
            TextPVPattern.Ink -> repeat(6) { i -> val y = size.height * (.17f + i * .13f); drawLine(spec.accent.copy(alpha = .09f), Offset(size.width * .04f, y), Offset(size.width * (.58f + i * .05f), y - 18 + motion * 8), 8f - i * .7f) }
            TextPVPattern.Villain -> repeat(5) { i -> val y = size.height * (.20f + i * .14f); drawLine(spec.accent.copy(alpha = .08f + i * .018f), Offset(size.width * .08f, y), Offset(size.width * (.88f - i * .05f), y), 1f) }
            TextPVPattern.Clouds -> repeat(7) { i -> val x = size.width * ((i * .2f + motion * .08f) % 1.2f - .1f); drawCircle(spec.accent.copy(alpha = .15f), size.minDimension * (.10f + i % 3 * .035f), Offset(x, size.height * (.18f + i % 4 * .18f))) }
            TextPVPattern.Gingham -> { val spacing = size.minDimension / 9; repeat(14) { i -> drawLine(spec.accent.copy(alpha = .09f), Offset(i * spacing, 0f), Offset(i * spacing, size.height), spacing * .32f) }; repeat(22) { i -> drawLine(spec.secondary.copy(alpha = .13f), Offset(0f, i * spacing), Offset(size.width, i * spacing), spacing * .28f) } }
            TextPVPattern.Stars -> { repeat(28) { i -> drawCircle(spec.accent.copy(alpha = .12f + i % 4 * .03f), 1.5f + i % 3, Offset(size.width * ((i * .173f + motion * .015f) % 1f), size.height * ((i * .317f) % 1f))) }; drawCircle(spec.accent.copy(alpha = .18f), size.minDimension * .24f, Offset(size.width * .78f, size.height * .22f)) }
            TextPVPattern.Pixels -> { val spacing = (size.minDimension / 13).coerceAtLeast(8f); repeat((size.width / spacing).toInt().coerceAtMost(80) + 1) { i -> drawLine(spec.accent.copy(alpha = .09f), Offset(i * spacing, 0f), Offset(i * spacing, size.height), 2f) }; repeat((size.height / spacing).toInt().coerceAtMost(120) + 1) { i -> drawLine(spec.secondary.copy(alpha = .12f), Offset(0f, i * spacing), Offset(size.width, i * spacing), 2f) } }
            TextPVPattern.CrimeTape -> repeat(3) { i -> val y = size.height * (.22f + i * .27f + motion * .025f); drawLine(spec.accent.copy(alpha = .76f), Offset(-size.width * .15f, y + size.height * .08f), Offset(size.width * 1.15f, y - size.height * .08f), size.minDimension * .045f) }
            TextPVPattern.Petals -> repeat(18) { i -> val x = size.width * ((i * .113f + motion * .05f) % 1.1f); val y = size.height * ((i * .179f + motion * .10f) % 1.1f); drawOval(spec.accent.copy(alpha = .18f), Offset(x, y), Size(8f + i % 4 * 3, 16f + i % 5 * 3)) }
        }
    }
}

@Composable
private fun TextPVComposition(line: LyricLine, next: LyricLine?, style: MeloXTextPVStyle, phaseProvider: () -> Float, motionIntensity: Float, lineIndex: Int, onSeek: () -> Unit) {
    val spec = remember(style) { style.visualSpec() }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = spec.alignment) {
        Column(Modifier.fillMaxWidth(.94f).testTag("lyric-line-$lineIndex").semantics { this[MeloXLyricTimeMsKey] = line.timeMs }
            .clickable(onClick = onSeek), horizontalAlignment = when (spec.textAlign) { TextAlign.Center -> Alignment.CenterHorizontally; TextAlign.End -> Alignment.End; else -> Alignment.Start }) {
            spec.marker?.let { Text("$it // ${((line.timeMs / 10) % 1000).toString().padStart(3, '0')}", color = spec.accent, fontSize = 11.sp, letterSpacing = 2.sp) }
            Text(if (spec.uppercase) line.text.uppercase() else line.text, color = spec.foreground, fontSize = spec.fontSize.sp, lineHeight = spec.lineHeight.sp, fontWeight = spec.weight, letterSpacing = spec.letterSpacing.sp, textAlign = spec.textAlign,
                modifier = Modifier.rotate(spec.rotation).graphicsLayer { val wave = sin(phaseProvider() * 2 * PI.toFloat()) * motionIntensity; translationX = wave * spec.motionX; translationY = wave * spec.motionY; scaleX = 1 + wave * spec.pulse; scaleY = 1 + wave * spec.pulse })
            if (MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank()) Text(line.translation.orEmpty(), color = spec.foreground.copy(alpha = .62f), fontSize = 15.sp, textAlign = spec.textAlign, modifier = Modifier.padding(top = 12.dp))
            next?.let { Text(it.text, color = spec.foreground.copy(alpha = spec.nextOpacity), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = spec.textAlign, modifier = Modifier.padding(top = 24.dp)) }
        }
    }
}

@Composable
internal fun MeloXSkylineLyricsPanel(track: MusicTrack, modifier: Modifier = Modifier, onInteraction: () -> Unit = {}, active: Boolean = true) {
    val state = rememberAlternativeLyrics(track, active)
    val position by melox.playback.MeloXPlaybackOutput.positionMs.collectAsState()
    val advance = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, rememberBilibiliLyricOffset(track))
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active, MeloXSettingsRuntime.lyricReduceMotion, MeloXSettingsRuntime.skylineAmbientDrift) {
        if (!active || MeloXSettingsRuntime.lyricReduceMotion || MeloXSettingsRuntime.skylineAmbientDrift <= 0) { phase.snapTo(0f); return@LaunchedEffect }
        while (isActive) { phase.animateTo(1f, tween(12000)); phase.snapTo(0f) }
    }
    Box(modifier.fillMaxSize().testTag("player-skyline").clickable(onClick = onInteraction)) {
        val ambient = remember(state.lines, state.index, MeloXSettingsRuntime.skylineAmbientLines, MeloXSettingsRuntime.skylineAmbientMaximumCharacters, MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts) {
            state.lines.drop(state.index + 1).take(MeloXSettingsRuntime.skylineAmbientLines).flatMap { it.text.chunked(MeloXSettingsRuntime.skylineAmbientMaximumCharacters) }.filter(String::isNotBlank).take(MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts)
        }
        if (ambient.isNotEmpty()) Column(Modifier.testTag("skyline-ambient").align(Alignment.CenterEnd).fillMaxWidth(.58f).rotate(-5f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ambient.forEachIndexed { index, text ->
                val fade = 1 - index.toFloat() / ambient.size.coerceAtLeast(1)
                Text(text, color = Color.White.copy(alpha = (.08f * MeloXSettingsRuntime.skylineAmbientOpacity * (.45f + fade * .55f)).coerceIn(.02f, .22f)), fontSize = MeloXSettingsRuntime.skylineAmbientFontSize.sp, lineHeight = (MeloXSettingsRuntime.skylineAmbientFontSize * 1.08f).sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("skyline-ambient-$index").graphicsLayer { translationX = if (MeloXSettingsRuntime.lyricReduceMotion) 0f else sin(phase.value * 2 * PI.toFloat() + index) * 14 * MeloXSettingsRuntime.skylineAmbientDrift }.rotate(sin((index + 1) * 1.73f) * MeloXSettingsRuntime.skylineAmbientMaximumTilt).blur((MeloXSettingsRuntime.skylineAmbientBlur * (1 + index * .08f)).dp))
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (MeloXSettingsRuntime.skylineShowSongInfo) {
                Column(Modifier.testTag("skyline-song-info").weight(.42f), verticalArrangement = Arrangement.Center) {
                    MeloXArtworkImage(track.artworkUrl, Color.DarkGray, Modifier.size(132.dp).clip(RoundedCornerShape(20.dp)))
                    Text(track.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                    Text(track.artistText, color = Color.White.copy(alpha = .52f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(34.dp))
            }
            AnimatedContent(state.index, Modifier.weight(if (MeloXSettingsRuntime.skylineShowSongInfo) .58f else 1f),
                transitionSpec = { (slideInVertically(tween(560, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(420))) togetherWith (slideOutVertically(tween(380)) { -it / 5 } + fadeOut(tween(300))) }, label = "skyline-focus") { index ->
                val line = state.lines.getOrNull(index); val next = state.lines.getOrNull(index + 1)
                val end = line?.durationMs?.let { line.timeMs + it } ?: next?.timeMs ?: (line?.timeMs?.plus(3000) ?: 1)
                val progress = if (line == null || line.syllables.isEmpty()) 0f else ((position + advance - line.timeMs).toFloat() / (end - line.timeMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                Column(Modifier.fillMaxWidth(MeloXSettingsRuntime.skylineCurrentWidth), verticalArrangement = Arrangement.Center) {
                    Text(line?.text.orEmpty(), color = Color.White, fontSize = MeloXSettingsRuntime.skylineCurrentFontSize.sp, lineHeight = (MeloXSettingsRuntime.skylineCurrentFontSize * 1.12f).sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("skyline-current-line").scale(1 + (MeloXSettingsRuntime.skylineCurrentMaximumScale - 1) * progress).clickable { line?.let { PlaybackCommands.playerController()?.seekTo(it.timeMs) } })
                    if (MeloXSettingsRuntime.showLyricTranslation && !line?.translation.isNullOrBlank()) Text(line.translation.orEmpty(), color = Color.White.copy(alpha = .62f), fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    next?.let { Text(it.text, color = Color.White.copy(alpha = MeloXSettingsRuntime.skylineNextOpacity), fontSize = MeloXSettingsRuntime.skylineNextFontSize.sp, lineHeight = (MeloXSettingsRuntime.skylineNextFontSize * 1.2f).sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("skyline-next-line").padding(top = MeloXSettingsRuntime.skylineCurrentSpacing.dp)) }
                }
            }
        }
        AlternativeLoading(state)
    }
}

@Composable
private fun AlternativeLoading(state: AlternativeLyricsState) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(state.error, color = MaterialTheme.colorScheme.error) }
        state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("暂无歌词", color = Color.White.copy(alpha = .56f)) }
    }
}
