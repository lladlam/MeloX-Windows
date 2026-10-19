package melox.settings

enum class MeloXSplitChromePosition(val right: Boolean, val vertical: Int) {
    BottomLeft(false, 1), BottomRight(true, 1), Left(false, 0), Right(true, 0), TopLeft(false, -1), TopRight(true, -1);

    val isSideRail: Boolean get() = vertical == 0

    companion object {
        fun fromPreference(value: String): MeloXSplitChromePosition = entries.firstOrNull { it.name == value } ?: BottomLeft
    }
}

fun meloXChromeScrollCoupled(split: Boolean, navigation: MeloXSplitChromePosition, miniPlayer: MeloXSplitChromePosition): Boolean =
    !split || navigation == miniPlayer
