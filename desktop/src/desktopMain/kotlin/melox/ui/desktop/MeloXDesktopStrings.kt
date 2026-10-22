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
        "window_minimize" -> if (chinese) "最小化" else "Minimize"
        "window_maximize" -> if (chinese) "最大化" else "Maximize"
        "window_restore" -> if (chinese) "还原" else "Restore"
        "window_close" -> if (chinese) "关闭" else "Close"
        "unavailable" -> if (chinese) "此音乐源或当前 Windows 后端尚不支持" else "Not available for this source or Windows backend"
        "account_title" -> if (chinese) "账户" else "Account"
        "account_private_messages" -> if (chinese) "私信" else "Private Messages"
        "account_settings" -> if (chinese) "账户设置" else "Account Settings"
        "account_logout" -> if (chinese) "退出登录" else "Log Out"
        "account_login" -> if (chinese) "登录" else "Log In"
        "account_done" -> if (chinese) "完成" else "Done"
        "login_phone" -> if (chinese) "手机号" else "Phone"
        "login_client_cookie" -> if (chinese) "本机客户端" else "Client Cookie"
        "login_send_code" -> if (chinese) "获取验证码" else "Send Code"
        "login_client_message" -> if (chinese) "读取当前 Windows 用户的网易云音乐客户端 Cookie，验证后用于 MeloX 登录。"
            else "Read the NetEase Cloud Music client cookie for this Windows user, verify it, and use it to sign in to MeloX."
        "login_client_windows_only" -> if (chinese) "本机客户端登录仅支持 Windows。" else "Client-cookie sign-in is only available on Windows."
        "login_find_client" -> if (chinese) "查找客户端登录态" else "Find Client Sign-In"
        "login_import_client" -> if (chinese) "读取客户端登录态" else "Import Client Sign-In"
        "login_client_not_found" -> if (chinese) "未找到网易云音乐客户端登录数据，请先在客户端登录"
            else "No NetEase Cloud Music client sign-in was found. Sign in with the client first."
        "login_choose_profile" -> if (chinese) "发现多个客户端数据目录，请选择后再读取" else "Multiple client profiles were found. Choose one, then import it."
        "services_title" -> if (chinese) "音乐服务" else "Music Services"
        "tray_title" -> if (chinese) "关闭窗口时隐藏到系统托盘" else "Hide to system tray when closing the window"
        "caption_title" -> if (chinese) "沉浸式标题栏" else "Immersive title bar"
        "caption_hint" -> if (chinese) "窗口按钮融入页面背景，跟随界面和主题变化。" else "Blend window controls into the page background and theme."
        "tray_hint" -> if (chinese) "保留后台播放；从托盘显示窗口或退出 MeloX。" else "Keep playback running; show the window or quit MeloX from the tray."
        "tray_show" -> if (chinese) "显示 MeloX" else "Show MeloX"
        "tray_quit" -> if (chinese) "退出 MeloX" else "Quit MeloX"
        "services_current_source" -> if (chinese) "当前音乐源" else "Current music source"
        "services_providers" -> if (chinese) "音乐服务" else "Providers"
        "services_search" -> if (chinese) "搜索设置" else "Search Settings"
        "services_signed_in" -> if (chinese) "已登录" else "Signed In"
        "services_not_signed_in" -> if (chinese) "未登录" else "Not Signed In"
        "services_in_search" -> if (chinese) "加入搜索" else "In Search"
        "services_sign_out" -> if (chinese) "退出" else "Sign Out"
        "services_unified" -> if (chinese) "聚合搜索" else "Combined Search"
        "services_scope_hint" -> if (chinese) "勾选要参与聚合搜索的来源" else "Choose which providers participate in combined search."
        else -> error("Unknown Windows integration label: $key")
    }
}
