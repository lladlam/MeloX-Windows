package melox

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch

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
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { melox.lan.MeloXLanRuntime.start() }
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
            var windowVisible by remember { mutableStateOf(true) }
            var trayInstalled by remember { mutableStateOf(false) }
            var restoreRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(windowVisible, windowState.isMinimized) {
                melox.remoteconfig.MeloXRemoteConfigRuntime.setForeground(windowVisible && !windowState.isMinimized)
            }
            val quitScope = rememberCoroutineScope()
            var quitting by remember { mutableStateOf(false) }
            val quitApplication: () -> Unit = {
                if (!quitting) {
                    quitting = true
                    quitScope.launch {
                        try { melox.dlna.DlnaSession.disconnect(resumeLocal = false) }
                        finally {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { melox.lan.MeloXLanRuntime.stop() }
                            melox.playback.PlaybackCommands.playerController()?.release()
                            exitApplication()
                        }
                    }
                }
            }
            val showWindow: () -> Unit = { windowVisible = true; windowState.isMinimized = false; restoreRequest++ }
            val currentShow by rememberUpdatedState(showWindow)
            val currentQuit by rememberUpdatedState(quitApplication)
            val closeToTray = melox.settings.MeloXSettingsRuntime.closeToTrayEnabled
            DisposableEffect(closeToTray) {
                if (!closeToTray && !windowVisible) currentShow()
                val tray = if (closeToTray) melox.platform.MeloXSystemTray.install(
                    melox.ui.desktop.desktopPlatformString("tray_show"), melox.ui.desktop.desktopPlatformString("tray_quit"),
                    { currentShow() }, { currentQuit() }) else null
                trayInstalled = tray != null
                onDispose { tray?.close(); trayInstalled = false }
            }
            val closeWindow: () -> Unit = {
                if (melox.settings.MeloXSettingsRuntime.closeToTrayEnabled && trayInstalled) windowVisible = false
                else quitApplication()
            }
            val currentClose by rememberUpdatedState(closeWindow)
            Window(
                title = "MeloX",
                state = windowState,
                visible = windowVisible,
                undecorated = true,
                onCloseRequest = closeWindow,
            ) {
                LaunchedEffect(windowVisible, restoreRequest) {
                    if (windowVisible && restoreRequest > 0) { window.toFront(); window.requestFocus() }
                }
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
                    fun foreground() = window.isVisible && window.isFocused && (window.extendedState and java.awt.Frame.ICONIFIED) == 0
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
                    melox.ui.desktop.MeloXWindowChrome(this, windowState) { currentClose() }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    melox.ui.desktop.LocalMeloXWindowChrome provides chrome,
                    melox.ui.player.LocalMeloXDisplayAwakeOwner provides displayAwake,
                    melox.ui.player.LocalMeloXWindowForeground provides (windowVisible && focused && !windowState.isMinimized),
                    melox.ui.player.LocalMeloXImmersiveWindowOwner provides immersiveOwner,
                ) {
                    MeloXApp(onExit = quitApplication)
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
