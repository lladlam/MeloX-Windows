package melox.ui.theme

import androidx.compose.ui.graphics.Color

object MeloXColors {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF1A1A1A)
    val SurfaceVariant = Color(0xFF242424)
    val SurfaceHigh = Color(0xFF2E2E2E)
    val OnSurface = Color(0xFFE8E8E8)
    val OnSurfaceVariant = Color(0xFF999999)
    val Primary = Color(0xFFFF2442)
    val PrimaryVariant = Color(0xFFCC1C35)
    val OnPrimary = Color.White
    val Secondary = Color(0xFF03DAC5)
    val Outline = Color(0xFF3C3C3C)
    val OutlineVariant = Color(0xFF2C2C2C)
    val Divider = Color(0xFF1E1E1E)
    val Error = Color(0xFFCF6679)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)

    val PlayerProgress = Color(0xFFFF2442)
    val PlayerProgressBackground = Color(0xFF3C3C3C)

    val CardBackground = Color(0xFF161616)
    val CardBackgroundHover = Color(0xFF1E1E1E)
    val ChipBackground = Color(0xFF2C2C2C)
    val ChipBackgroundSelected = Color(0xFFFF2442)

    val SidebarBackground = Color(0xFF111111)
    val SidebarItemHover = Color(0xFF1E1E1E)
    val SidebarItemSelected = Color(0xFF252525)

    val LyricsCurrent = Color.White
    val LyricsPast = Color(0xFF666666)
    val LyricsFuture = Color(0xFF999999)

    val MiniPlayerBackground = Color(0xFF1A1A1A)

    val TextPrimary = Color(0xFFE8E8E8)
    val TextSecondary = Color(0xFF999999)
    val TextTertiary = Color(0xFF666666)

    val sourceColors = mapOf(
        "netease" to Color(0xFFFF2442),
        "qq" to Color(0xFF12B7F5),
        "kugou" to Color(0xFF2CA2F9),
        "kuwo" to Color(0xFFFF6600),
        "bilibili" to Color(0xFFFB7299),
        "spotify" to Color(0xFF1DB954),
        "youtubemusic" to Color(0xFFFF0000),
        "applemusic" to Color(0xFFFC3C44),
        "jellyfin" to Color(0xFF00A4DC),
        "local" to Color(0xFF888888),
    )
}
