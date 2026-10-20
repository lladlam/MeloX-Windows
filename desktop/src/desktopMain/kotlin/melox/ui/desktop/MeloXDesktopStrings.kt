package melox.ui.desktop

import java.util.Locale
import java.util.Properties

/** Native Desktop source labels are independent of the pinned Android resources. */
private object DesktopStrings {
    val english = load("en")
    val chinese = load("zh-Hans")
    private fun load(locale: String) = Properties().apply {
        checkNotNull(DesktopStrings::class.java.classLoader.getResourceAsStream("parity/desktop-reference-$locale.properties"))
            .reader(Charsets.UTF_8).use(::load)
    }
}

internal fun desktopString(key: String): String {
    val locale = Locale.getDefault()
    val simplified = locale.language == "zh" && locale.script != "Hant" && locale.country !in setOf("TW", "HK", "MO")
    return checkNotNull((if (simplified) DesktopStrings.chinese else DesktopStrings.english).getProperty(key)) { key }
}

/** Adapt Foundation format specifiers at the call site; exported values stay byte-pure. */
internal fun desktopFormat(key: String, vararg arguments: Any): String =
    String.format(Locale.getDefault(), desktopString(key).replace("%ld", "%d").replace("%@", "%s"), *arguments)

internal fun desktopQualityTitle(quality: melox.audio.MusicQuality): String = desktopString("ui.settings.audio_quality." + when (quality) {
    melox.audio.MusicQuality.Standard -> "standard"
    melox.audio.MusicQuality.High -> "high"
    melox.audio.MusicQuality.Lossless -> "lossless"
    melox.audio.MusicQuality.HiResolution -> "hi_res"
    melox.audio.MusicQuality.HighDefinitionSurround -> "hd_surround"
    melox.audio.MusicQuality.ImmersiveSurround -> "immersive_surround"
    melox.audio.MusicQuality.UltraClearMaster -> "master"
})

/** Windows-only integration labels: no corresponding toggle exists in the macOS source. */
internal fun desktopPlatformString(key: String): String {
    val chinese = Locale.getDefault().language == "zh"
    return when (key) {
        "layout_title" -> if (chinese) "电脑端布局" else "Desktop layout"
        "layout_hint" -> if (chinese) "使用 MeloX 电脑端侧栏和底部播放器；关闭可切回当前布局" else "Use the MeloX desktop sidebar and bottom player; turn off to restore the current layout"
        "settings" -> if (chinese) "设置" else "Settings"
        "unavailable" -> if (chinese) "此音乐源或当前 Windows 后端尚不支持" else "Not available for this source or Windows backend"
        "account_title" -> if (chinese) "账户" else "Account"
        "account_private_messages" -> if (chinese) "私信" else "Private Messages"
        "account_settings" -> if (chinese) "账户设置" else "Account Settings"
        "account_logout" -> if (chinese) "退出登录" else "Log Out"
        "account_login" -> if (chinese) "登录" else "Log In"
        "account_done" -> if (chinese) "完成" else "Done"
        else -> error("Unknown Windows integration label: $key")
    }
}
