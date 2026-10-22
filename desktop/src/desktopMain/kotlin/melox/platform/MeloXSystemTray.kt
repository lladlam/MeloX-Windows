package melox.platform

import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO

/** A tray is usable only after successful registration; callers never hide without one. */
internal class MeloXSystemTray private constructor(private val tray: SystemTray, private val icon: TrayIcon) : AutoCloseable {
    override fun close() { tray.remove(icon) }

    companion object {
        fun install(showLabel: String, quitLabel: String, onShow: () -> Unit, onQuit: () -> Unit): MeloXSystemTray? {
            if (!SystemTray.isSupported()) return null
            return runCatching {
                val image = requireNotNull(MeloXSystemTray::class.java.getResourceAsStream("/parity/desktop-logo.png"))
                    .use { requireNotNull(ImageIO.read(it)) }
                val menu = PopupMenu().apply {
                    add(MenuItem(showLabel).apply { addActionListener { onShow() } })
                    addSeparator()
                    add(MenuItem(quitLabel).apply { addActionListener { onQuit() } })
                }
                val icon = TrayIcon(image, "MeloX", menu).apply {
                    isImageAutoSize = true
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(event: MouseEvent) { if (event.button == MouseEvent.BUTTON1 && event.clickCount == 1) onShow() }
                    })
                }
                val tray = SystemTray.getSystemTray()
                tray.add(icon)
                MeloXSystemTray(tray, icon)
            }.onFailure { logInfo("MeloXTray", "Tray registration failed: ${it.javaClass.simpleName}") }.getOrNull()
        }
    }
}
