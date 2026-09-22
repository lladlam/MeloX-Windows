package melox.ui.theme

import androidx.compose.ui.graphics.Color

object MeloXColors {
    // Dark theme (primary - MeloX is always dark)
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF151518)
    val SurfaceVariant = Color(0xFF252529)
    val OnBackground = Color(0xFFF5F5F7)
    val OnSurface = Color(0xFFF5F5F7)
    val OnSurfaceVariant = Color(0xFFB8B8C0)
    val Primary = Color(0xFF0A84FF)
    val OnPrimary = Color(0xFFFFFFFF)
    val Error = Color(0xFFFF453A)

    // Light theme colors (for completeness)
    val LightBackground = Color(0xFFF7F7FA)
    val LightSurface = Color(0xFFFDFDFE)
    val LightSurfaceVariant = Color(0xFFE9E9EE)
    val LightOnBackground = Color(0xFF17171A)
    val LightOnSurface = Color(0xFF17171A)
    val LightOnSurfaceVariant = Color(0xFF5D5D66)
    val LightPrimary = Color(0xFF007AFF)
    val LightError = Color(0xFFFF3B30)

    // Glass system tokens
    val GlassBlue = Color(0xFF0A84FF)
    val GlassRed = Color(0xFFFF3B30)
    val Red = GlassRed  // Android MeloXSystemColors.Red
    val Blue = GlassBlue
    val SecondaryFill = Color(0x26787880)  // 15% opacity
    val TertiaryFill = Color(0x1F767680)   // 12% opacity
    val Separator = Color(0x4A3C3C43)      // 29% opacity

    // Glass surface
    val GlassSurfaceDark = Color(0x0DFFFFFF)   // White@5% for dark glass
    val GlassSurfaceLight = Color(0x1FFFFFFF)  // White@12% for light glass

    // Toggle
    val ToggleAccentDark = Color(0xFF30D158)
    val ToggleAccentLight = Color(0xFF34C759)
    val ToggleTrackDark = Color(0x5C787880)    // 36% opacity
    val ToggleTrackLight = Color(0x33787878)   // 20% opacity

    // Tab selection
    val TabSelectionDark = Color(0x4DFF2442)   // Red@30%
    val TabSelectionLight = Color(0x29FF2442)  // Red@16%
    val TabSelectionBorderDark = Color(0x6BFFFFFF) // White@42%
    val TabSelectionBorderLight = Color(0x57FF2442) // Red@34%

    // Player
    val PlayerBackground = Color(0xFF000000)
    val PlayerProgressBg = Color(0x33FFFFFF)   // White@20%
    val PlayerProgressFill = Color(0xF5FFFFFF) // White@96%
    val PlayerTextDim = Color(0x80FFFFFF)      // White@50%
    val PlayerControlBg = Color(0xFFFFFFFF)    // White@100% for play button

    // Mini Player
    val MiniPlayerSurface = Color(0x0FFFFFFF)  // White@6%

    // Legacy aliases (used by old screens - will be removed when screens are rebuilt)
    val PrimaryVariant = Primary
    val Secondary = Color(0xFF30D158)
    val Outline = Color(0xFF3C3C3C)
    val OutlineVariant = Color(0xFF2C2C2C)
    val SidebarBackground = Surface
    val SidebarItemSelected = SurfaceVariant
    val SidebarItemHover = Color(0xFF1E1E1E)
    val CardBackground = SurfaceVariant
    val CardBackgroundHover = Color(0xFF2E2E2E)
    val ChipBackground = Color(0xFF2C2C2C)
    val ChipBackgroundSelected = Primary
    val TextPrimary = OnSurface
    val TextSecondary = OnSurfaceVariant
    val TextTertiary = Color(0xFF666666)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val PlayerProgress = PlayerProgressFill
    val PlayerProgressBackground = PlayerProgressBg
    val LyricsCurrent = OnSurface
    val LyricsPast = Color(0xFF666666)
    val LyricsFuture = Color(0xFF999999)
    val MiniPlayerBackground = Surface
    val SurfaceHigh = SurfaceVariant
    val Divider = Separator

    // Source provider colors
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
