package melox.ui.foundation

import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import melox.ui.theme.MeloXSFProFontFamily

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
    ChevronUpDown("chevron.chevron.down", intArrayOf(0x10018F)),
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
    Queue("text.line.first.and.arrowtriangle.forward", intArrayOf(0x10163F)),
    Question("questionmark.circle", intArrayOf(0x10005C)),
    CheckCircle("checkmark.circle.fill", intArrayOf(0x100063)),
    Cloud("cloud", intArrayOf(0x1002F8)),
    Globe("globe", intArrayOf(0x10026D)),
    HeartFill("heart.fill", intArrayOf(0x1002B4)),
    Lyrics("text.bubble", intArrayOf(0x10032E)),
    PersonFill("person.fill", intArrayOf(0x10026D)),
    Star("star", intArrayOf(0x1001BF)),
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
) {
    Text(
        text = codePointToString(symbol.codePoints),
        modifier = modifier,
        style = TextStyle(
            fontFamily = MeloXSFProFontFamily,
            fontSize = size.sp,
            color = color,
        ),
    )
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
