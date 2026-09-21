package melox.ui.foundation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import melox.ui.theme.MeloXSFProFontFamily

enum class MeloXSymbol(
    val sfName: String,
    val codePoints: IntArray,
) {
    Home("house", intArrayOf(0xE039E)),
    Explore("safari", intArrayOf(0xE0506)),
    Library("music.note.list", intArrayOf(0xE0319)),
    Settings("gear", intArrayOf(0xE0244)),
    Search("magnifyingglass", intArrayOf(0xE047E)),
    Play("play.fill", intArrayOf(0xE03F2)),
    Pause("pause.fill", intArrayOf(0xE03F0)),
    PreviousTrack("backward.fill", intArrayOf(0xE03E6)),
    NextTrack("forward.fill", intArrayOf(0xE03ED)),
    Shuffle("shuffle", intArrayOf(0xE0466)),
    Repeat("repeat", intArrayOf(0xE0435)),
    Heart("heart", intArrayOf(0xE0297)),
    HeartFill("heart.fill", intArrayOf(0xE0298)),
    Volume("speaker.wave.2.fill", intArrayOf(0xE04B0)),
    Queue("text.line.first.and.arrowtriangle.forward", intArrayOf(0xE0497)),
    Lyrics("text.bubble", intArrayOf(0xE0486)),
    Share("arrow.up.circle", intArrayOf(0xE010F)),
    ChevronLeft("chevron.left", intArrayOf(0xE018B)),
    ChevronRight("chevron.right", intArrayOf(0xE018D)),
    MoreHorizontal("ellipsis", intArrayOf(0xE0215)),
    Checkmark("checkmark", intArrayOf(0xE0177)),
    XMark("xmark", intArrayOf(0xE050F)),
    Plus("plus", intArrayOf(0xE03CE)),
    Minus("minus", intArrayOf(0xE03B7)),
    ArrowUp("arrow.up", intArrayOf(0xE010C)),
    ArrowDown("arrow.down", intArrayOf(0xE0106)),
    Refresh("arrow.clockwise", intArrayOf(0xE0100)),
    Download("arrow.down.circle", intArrayOf(0xE0103)),
    Upload("arrow.up.circle", intArrayOf(0xE0111)),
    Trash("trash", intArrayOf(0xE0481)),
    Folder("folder", intArrayOf(0xE0233)),
    MusicNote("music.note", intArrayOf(0xE0318)),
    Podcast("radio", intArrayOf(0xE042C)),
    Cloud("icloud", intArrayOf(0xE02AE)),
    Clock("clock", intArrayOf(0xE0195)),
    Phone("phone", intArrayOf(0xE03BA)),
    Globe("globe", intArrayOf(0xE0260)),
    Key("key", intArrayOf(0xE02B6)),
    Lock("lock", intArrayOf(0xE02D4)),
    Wifi("wifi", intArrayOf(0xE04C0)),
    QualityHifi("hifispeaker", intArrayOf(0xE0292)),
    Airplay("airplayaudio", intArrayOf(0xE00F2)),
    ListBullet("list.bullet", intArrayOf(0xE02D0)),
    SquareGrid("square.grid.2x2", intArrayOf(0xE0474)),
    Person("person", intArrayOf(0xE03B6)),
    PersonFill("person.fill", intArrayOf(0xE03B7)),
    Star("star", intArrayOf(0xE0471)),
    StarFill("star.fill", intArrayOf(0xE0472)),
    Message("message", intArrayOf(0xE0305)),
    MessageFill("message.fill", intArrayOf(0xE0306)),
}

@Composable
fun MeloXSymbolIcon(
    symbol: MeloXSymbol,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Int = 22,
) {
    val text = remember(symbol) {
        StringBuilder().apply { symbol.codePoints.forEach { appendCodePoint(it) } }.toString()
    }
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = MeloXSFProFontFamily,
            fontSize = size.sp,
            color = color,
        ),
    )
}
