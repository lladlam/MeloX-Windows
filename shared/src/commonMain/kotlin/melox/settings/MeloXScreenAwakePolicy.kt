package melox.settings

/** Pinned SharedHost keepScreenOn scopes; desktop foreground replaces visible Android View lifetime. */
object MeloXScreenAwakePolicy {
    fun required(mode: MeloXScreenAwakeMode, lyricsPage: Boolean, lyricsInterfaceHidden: Boolean,
                 skylineVisible: Boolean, skylineKeepsAwake: Boolean,
                 foreground: Boolean, playerVisible: Boolean = true): Boolean =
        foreground && playerVisible && ((skylineVisible && skylineKeepsAwake) || when (mode) {
            MeloXScreenAwakeMode.Disabled -> false
            MeloXScreenAwakeMode.Player -> true
            MeloXScreenAwakeMode.Lyrics -> lyricsPage
            MeloXScreenAwakeMode.HiddenLyricsInterface -> lyricsPage && lyricsInterfaceHidden
        })
}
