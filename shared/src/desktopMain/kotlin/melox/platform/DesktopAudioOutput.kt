package melox.platform

import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinUser
import java.io.File

/** Platform counterpart to Android's system output switcher, opening the actual OS sound surface. */
fun showSystemAudioOutputSwitcher() {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    if (os.startsWith("windows")) {
        val result = Shell32.INSTANCE.ShellExecute(null, "open", "ms-settings:sound", null, null, WinUser.SW_SHOWNORMAL)
        check(result.toLong() > 32) { "无法打开 Windows 音频输出设置" }
        return
    }
    if (os.contains("mac")) {
        check(ProcessBuilder("open", "x-apple.systempreferences:com.apple.Sound-Settings.extension").start().waitFor() == 0) {
            "无法打开系统音频输出设置"
        }
        return
    }
    check(!System.getenv("DISPLAY").isNullOrBlank() || !System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) {
        "当前会话没有桌面音频输出设置窗口"
    }
    val path = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    val command = listOf(listOf("pavucontrol"), listOf("gnome-control-center", "sound"), listOf("systemsettings", "kcm_pulseaudio"))
        .firstOrNull { args -> path.any { File(it, args.first()).canExecute() } }
        ?: error("未找到系统音频输出设置")
    ProcessBuilder(command).start()
}
