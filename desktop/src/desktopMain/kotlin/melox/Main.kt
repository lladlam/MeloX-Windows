package melox

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import melox.ui.MeloXApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date

fun main() {
    melox.platform.logInfo("MeloXStartup", "commit=${MeloXBuildConfig.BUILD_COMMIT} pid=${ProcessHandle.current().pid()} os=${System.getProperty("os.name")} ${System.getProperty("os.version")} arch=${System.getProperty("os.arch")} java=${System.getProperty("java.version")}")
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = melox.diagnostics.MeloXProcessLogs.redact(sw.toString())
        melox.platform.logError("MeloXCrash", "thread=${thread.name}\n$stackTrace")
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val logFile = File(System.getProperty("user.home"), "MeloX-crash-$timestamp.log")
        try {
            logFile.writeText(
                """
                |MeloX Desktop Crash Log
                |========================
                |Time: ${Date()}
                |Thread: ${melox.diagnostics.MeloXProcessLogs.redact(thread.name)}
                |Exception: ${throwable.javaClass.name}: ${melox.diagnostics.MeloXProcessLogs.redact(throwable.message.orEmpty())}
                |
                |Stack Trace:
                |$stackTrace
                """.trimMargin()
            )
            System.err.println("CRASH LOG written to: ${logFile.absolutePath}")
            System.err.println(stackTrace)
        } catch (e: Exception) {
            System.err.println("Failed to write crash log: $e")
            System.err.println(stackTrace)
        }
    }

    try {
        melox.settings.MeloXSettingsPreferences.initializeCritical()
        application {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    val images = melox.ui.artwork.MeloXArtworkMemoryCache.stats()
                    melox.platform.logInfo("MeloXMemory", "${melox.diagnostics.MeloXMemoryDiagnostics.describe()} " +
                        "artwork_entries=${images.entries} artwork_budgeted_bytes=${images.retainedWeight} artwork_budget_bytes=${images.maxWeight}")
                    kotlinx.coroutines.delay(60_000L)
                }
            }
            val windowState = rememberWindowState(
                width = if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) 1280.dp else 427.dp,
                height = if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) 800.dp else 924.dp,
                position = WindowPosition.PlatformDefault,
            )
            androidx.compose.runtime.LaunchedEffect(windowState.isMinimized) {
                melox.remoteconfig.MeloXRemoteConfigRuntime.setForeground(!windowState.isMinimized)
            }
            val closeWindow: () -> Unit = {
                melox.playback.PlaybackCommands.playerController()?.release()
                exitApplication()
            }
            Window(
                title = "MeloX",
                state = windowState,
                undecorated = true,
                onCloseRequest = closeWindow,
            ) {
                val desktopLayout = melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled
                androidx.compose.runtime.DisposableEffect(window, desktopLayout) {
                    val previousMinimum = window.minimumSize
                    if (desktopLayout) {
                        val frame = window.insets
                        window.minimumSize = java.awt.Dimension(980 + frame.left + frame.right, 540 + frame.top + frame.bottom)
                    }
                    onDispose { window.minimumSize = previousMinimum }
                }
                androidx.compose.runtime.LaunchedEffect(desktopLayout) {
                    if (desktopLayout) windowState.size = androidx.compose.ui.unit.DpSize(
                        maxOf(windowState.size.width, 980.dp), maxOf(windowState.size.height, 540.dp))
                }
                val immersiveOwner = androidx.compose.runtime.remember(windowState, window) { melox.ui.player.MeloXImmersiveWindowOwner(windowState, window) }
                androidx.compose.runtime.DisposableEffect(immersiveOwner) { onDispose { immersiveOwner.close() } }
                val displayAwake = androidx.compose.runtime.remember { melox.platform.MeloXDisplayAwakeController(initialForeground = false) }
                androidx.compose.runtime.DisposableEffect(window, displayAwake) {
                    fun foreground() = window.isFocused && (window.extendedState and java.awt.Frame.ICONIFIED) == 0
                    val listener = object : java.awt.event.WindowAdapter() {
                        override fun windowLostFocus(event: java.awt.event.WindowEvent) { displayAwake.setForeground(false) }
                        override fun windowGainedFocus(event: java.awt.event.WindowEvent) { displayAwake.setForeground(foreground()) }
                        override fun windowStateChanged(event: java.awt.event.WindowEvent) { displayAwake.setForeground(foreground()) }
                    }
                    window.addWindowFocusListener(listener)
                    window.addWindowStateListener(listener)
                    displayAwake.setForeground(foreground())
                    onDispose {
                        window.removeWindowFocusListener(listener)
                        window.removeWindowStateListener(listener)
                        displayAwake.close()
                    }
                }
                val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
                val chrome = androidx.compose.runtime.remember(window, windowState) {
                    melox.ui.desktop.MeloXWindowChrome(this, windowState, closeWindow)
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    melox.ui.desktop.LocalMeloXWindowChrome provides chrome,
                    melox.ui.player.LocalMeloXDisplayAwakeOwner provides displayAwake,
                    melox.ui.player.LocalMeloXWindowForeground provides (focused && !windowState.isMinimized),
                    melox.ui.player.LocalMeloXImmersiveWindowOwner provides immersiveOwner,
                ) {
                    MeloXApp(onExit = closeWindow)
                }
            }
        }
    } catch (e: Exception) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stackTrace = melox.diagnostics.MeloXProcessLogs.redact(sw.toString())
        melox.platform.logError("MeloXStartup", stackTrace)
        val logFile = File(System.getProperty("user.home"), "MeloX-startup-error.log")
        try {
            logFile.writeText("Startup Error:\n$stackTrace")
            System.err.println("STARTUP ERROR written to: ${logFile.absolutePath}")
        } catch (_: Exception) {}
        System.err.println(stackTrace)
        throw e
    }
}
