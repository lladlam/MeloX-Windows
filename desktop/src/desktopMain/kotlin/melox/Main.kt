package melox

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import melox.ui.MeloXApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val logFile = File(System.getProperty("user.home"), "MeloX-crash-$timestamp.log")
        try {
            logFile.writeText(
                """
                |MeloX Desktop Crash Log
                |========================
                |Time: ${Date()}
                |Thread: ${thread.name}
                |Exception: ${throwable.javaClass.name}: ${throwable.message}
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
        application {
            Window(
                title = "MeloX Desktop",
                onCloseRequest = ::exitApplication,
            ) {
                MeloXApp()
            }
        }
    } catch (e: Exception) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        val logFile = File(System.getProperty("user.home"), "MeloX-startup-error.log")
        try {
            logFile.writeText("Startup Error:\n$stackTrace")
            System.err.println("STARTUP ERROR written to: ${logFile.absolutePath}")
        } catch (_: Exception) {}
        System.err.println(stackTrace)
        throw e
    }
}
