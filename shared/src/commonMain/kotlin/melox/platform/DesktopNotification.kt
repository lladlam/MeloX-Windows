package melox.platform

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import javax.swing.SwingUtilities

/**
 * 桌面通知管理器。
 * 使用 Java AWT SystemTray 实现跨平台通知。
 */
object DesktopNotification {
    private var trayIcon: TrayIcon? = null
    private val toolkit = Toolkit.getDefaultToolkit()

    fun initialize() {
        if (!SystemTray.isSupported()) {
            logWarn("DesktopNotification", "系统托盘不支持")
            return
        }
        try {
            val tray = SystemTray.getSystemTray()
            val image = toolkit.getImage(javaClass.getResource("/icon.png"))
                ?: toolkit.createImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            trayIcon = TrayIcon(image, "MeloX Desktop")
            trayIcon?.isImageAutoSize = true
            tray.add(trayIcon)
        } catch (e: Exception) {
            logWarn("DesktopNotification", "初始化系统托盘失败: ${e.message}")
        }
    }

    fun showNotification(title: String, message: String) {
        SwingUtilities.invokeLater {
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        }
    }

    fun showError(title: String, message: String) {
        SwingUtilities.invokeLater {
            trayIcon?.displayMessage(title, message, TrayIcon.MessageType.ERROR)
        }
    }

    fun showMusicNotification(title: String, artist: String) {
        showNotification("正在播放", "$title - $artist")
    }
}
