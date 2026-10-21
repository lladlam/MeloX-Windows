package melox.ui.foundation

import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.ui.theme.meloXSfTypeface
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontFeature
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.shaper.ShapingOptions

enum class MeloXSymbol(
    val sfName: String,
    val codePoints: IntArray,
) {
    Home("house", intArrayOf(0x10039E)),
    Explore("safari", intArrayOf(0x1003AC)),
    Library("music.note.list", intArrayOf(0x10046C)),
    Settings("gear", intArrayOf(0x10035F)),
    Person("person.crop.circle", intArrayOf(0x10026D)),
    Search("magnifyingglass", intArrayOf(0x1002AB)),
    ChevronLeft("chevron.left", intArrayOf(0x100189)),
    ChevronRight("chevron.right", intArrayOf(0x10018A)),
    ChevronUpDown("chevron.up.chevron.down", intArrayOf(0x10018F)),
    XMark("xmark", intArrayOf(0x100184)),
    Ellipsis("ellipsis", intArrayOf(0x100360)),
    Clock("clock", intArrayOf(0x10042B)),
    Plus("plus", intArrayOf(0x10017C)),
    Share("square.and.arrow.up", intArrayOf(0x100202)),
    Message("message", intArrayOf(0x100324)),
    Info("info.circle", intArrayOf(0x100174)),
    Heart("heart", intArrayOf(0x1002B4)),
    ListBullet("list.bullet", intArrayOf(0x1002F2)),
    Checkmark("checkmark", intArrayOf(0x100185)),
    Download("arrow.down.circle", intArrayOf(0x100078)),
    Send("paperplane", intArrayOf(0x10021F)),
    Upload("arrow.up.circle.fill", intArrayOf(0x100077)),
    Refresh("arrow.clockwise", intArrayOf(0x100148)),
    MusicNote("music.note", intArrayOf(0x10046A)),
    Calendar("calendar", intArrayOf(0x100249)),
    Flame("flame.fill", intArrayOf(0x10066D)),
    Podcast("dot.radiowaves.left.and.right", intArrayOf(0x100319)),
    Walk("figure.walk.motion", intArrayOf(0x101411)),
    Sparkles("sparkles", intArrayOf(0x1001BF)),
    Quote("quote.bubble", intArrayOf(0x10032E)),
    Display("display", intArrayOf(0x1008B9)),
    Pip("pip", intArrayOf(0x100833)),
    Grid("circle.grid.2x2.fill", intArrayOf(0x1007BF)),
    Rotate("rectangle.landscape.rotate", intArrayOf(0x101EEF)),
    Mic("mic", intArrayOf(0x1002B0)),
    Drive("internaldrive", intArrayOf(0x10097E)),
    Bug("ladybug", intArrayOf(0x100BD4)),
    Play("play.fill", intArrayOf(0x100284)),
    Pause("pause.fill", intArrayOf(0x100286)),
    PreviousTrack("backward.fill", intArrayOf(0x10028A)),
    NextTrack("forward.fill", intArrayOf(0x10028C)),
    Shuffle("shuffle", intArrayOf(0x10029D)),
    Repeat("repeat", intArrayOf(0x10029E)),
    RepeatOne("repeat.1", intArrayOf(0x10029F)),
    Infinity("infinity", intArrayOf(0x100BE0)),
    Waveform("waveform", intArrayOf(0x10066B)),
    Badge("text.badge.plus", intArrayOf(0x1002F8)),
    Trash("trash", intArrayOf(0x100211)),
    Moon("moon", intArrayOf(0x1001B9)),
    Switch("switch.2", intArrayOf(0x10070A)),
    Book("book.pages", intArrayOf(0x10173E)),
    Chat("bubble.left", intArrayOf(0x10032A)),
    Volume("speaker.wave.2.fill", intArrayOf(0x1002A7)),
    VolumeMuted("speaker.slash.fill", intArrayOf(0x1002A3)),
    VolumeLow("speaker.wave.1.fill", intArrayOf(0x1002A5)),
    VolumeHigh("speaker.wave.3.fill", intArrayOf(0x1002A9)),
    Queue("text.line.first.and.arrowtriangle.forward", intArrayOf(0x10163F)),
    Question("questionmark.circle", intArrayOf(0x10005C)),
    CheckCircle("checkmark.circle.fill", intArrayOf(0x100063)),
    Cloud("cloud", intArrayOf(0x1001C2)),
    Globe("globe", intArrayOf(0x1001AA)),
    HeartFill("heart.fill", intArrayOf(0x1002B5)),
    Lyrics("text.bubble", intArrayOf(0x100332)),
    PersonFill("person.fill", intArrayOf(0x10026A)),
    Star("star", intArrayOf(0x1002C2)),
    StarFill("star.fill", intArrayOf(0x1002C3)),
    SquareGrid("square.grid.2x2", intArrayOf(0x1001F7)),
    SquareStack("square.stack", intArrayOf(0x10040B)),
    MusicMic("music.mic", intArrayOf(0x10046B)),
    ICloud("icloud", intArrayOf(0x10030B)),
    Messages("bubble.left.and.bubble.right", intArrayOf(0x1004A4)),
}

/**
 * Pinned Android `MeloXActionIcon` token table. Branch order is significant and
 * first-match wins, exactly like the Kotlin `when` in the pinned source, so a
 * repeated token resolves to the same SF Symbol on both platforms.
 *
 * An unmapped token fails fast. Android does the same, and a silent fallback
 * glyph would ship a plausible-looking but wrong icon on a settings row.
 */
internal fun meloXActionIconSymbol(token: String): MeloXSymbol = when (token) {
    "◷" -> MeloXSymbol.Clock
    "+", "＋" -> MeloXSymbol.Plus
    "↓×" -> MeloXSymbol.Download
    "↗" -> MeloXSymbol.Share
    // Android Mail is SF Symbols `paperplane`, not the `message` bubble.
    "✉" -> MeloXSymbol.Send
    "◎", "◌" -> MeloXSymbol.Message
    "i", "#", "ⓘ" -> MeloXSymbol.Info
    "♡", "♥" -> MeloXSymbol.Heart
    "▣", "⇥", "♬" -> MeloXSymbol.ListBullet
    "✓" -> MeloXSymbol.Checkmark
    "○" -> MeloXSymbol.Ellipsis
    "♫", "♪" -> MeloXSymbol.MusicNote
    "✦" -> MeloXSymbol.Sparkles
    "❞" -> MeloXSymbol.Quote
    "▱" -> MeloXSymbol.Rotate
    "▤" -> MeloXSymbol.Pip
    "☷", "▦", "▥" -> MeloXSymbol.Grid
    "⌁" -> MeloXSymbol.Mic
    "▰" -> MeloXSymbol.Drive
    "⚙" -> MeloXSymbol.Settings
    "⌘" -> MeloXSymbol.Bug
    "↑" -> MeloXSymbol.Upload
    "↓" -> MeloXSymbol.Download
    "‹" -> MeloXSymbol.ChevronLeft
    "›" -> MeloXSymbol.ChevronRight
    "•••", "…" -> MeloXSymbol.Ellipsis
    "×" -> MeloXSymbol.XMark
    "↻" -> MeloXSymbol.Refresh
    else -> error("Missing pinned SF Symbol token: $token")
}

private fun codePointToString(codePoints: IntArray): String {
    val sb = StringBuilder()
    for (cp in codePoints) sb.appendCodePoint(cp)
    return sb.toString()
}

@Composable
fun MeloXSymbolIcon(
    symbol: MeloXSymbol,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Int = 22,
    iconSize: Int = size,
    weight: FontWeight = FontWeight.Normal,
    nativeGlyph: Boolean = false,
) {
    if (!nativeGlyph && (symbol == MeloXSymbol.Settings || symbol == MeloXSymbol.Home)) {
        Icon(
            painter = painterResource(if (symbol == MeloXSymbol.Home) "icons/melox_home_tab.svg" else "icons/ic_melox_brand_gear.svg"),
            contentDescription = null,
            modifier = modifier.size(size.dp),
            tint = color,
        )
        return
    }
    val density = LocalDensity.current
    val em = with(density) { iconSize.sp.toPx() }
    val foreground = if (color == Color.Unspecified) LocalContentColor.current else color
    val font = remember(weight, em) { Font(meloXSfTypeface(weight), em).apply { isSubpixel = true } }
    val line = remember(symbol, font) {
        TextLine.make(codePointToString(symbol.codePoints), font,
            ShapingOptions.DEFAULT.withFeatures(arrayOf(FontFeature("ss16", 1))))
    }
    val bounds = remember(line) { line.textBlob?.use { it.tightBounds } }
    DisposableEffect(line) { onDispose { line.close() } }
    DisposableEffect(font) { onDispose { font.close() } }
    // Center actual ink, as Android Paint.getTextBounds does. A Text layout's
    // ascender box clips wide SF glyphs and offsets their optical center.
    Canvas(modifier = modifier.size(size.dp)) {
        drawIntoCanvas { canvas ->
            val bounds = bounds ?: return@drawIntoCanvas
            Paint().use { paint ->
                paint.color = foreground.toArgb()
                paint.isAntiAlias = true
                canvas.nativeCanvas.drawTextLine(line,
                    this.size.width / 2f - (bounds.left + bounds.right) / 2f,
                    this.size.height / 2f - (bounds.top + bounds.bottom) / 2f, paint)
            }
        }
    }
}

/**
 * Apple Music-style search affordance: magnifier and back arrow share one
 * icon-enter/exit fade instead of an abrupt glyph swap.
 */
@Composable
fun MeloXSearchBackMorphIcon(
    focused: Boolean,
    modifier: Modifier = Modifier,
    color: Color,
    contentDescription: String? = null,
) {
    androidx.compose.animation.AnimatedContent(
        targetState = focused,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(MeloXMotion.iconEnterTween()) togetherWith
                androidx.compose.animation.fadeOut(MeloXMotion.iconExitTween()))
        },
        modifier = modifier,
        label = "search-back-sf-transition",
    ) { isFocused ->
        MeloXSymbolIcon(
            symbol = if (isFocused) MeloXSymbol.ChevronLeft else MeloXSymbol.Search,
            color = color,
            size = 22,
        )
    }
}
