package timber.log

import java.util.logging.Level
import java.util.logging.Logger

/** JVM logging adapter for the read-only Android vendored InnerTube sources. */
object Timber {
    private val logger = Logger.getLogger("MeloX.InnerTube")
    fun d(message: String, vararg args: Any?) = log(Level.FINE, null, message, args)
    fun w(message: String, vararg args: Any?) = log(Level.WARNING, null, message, args)
    fun w(error: Throwable, message: String, vararg args: Any?) = log(Level.WARNING, error, message, args)
    fun e(error: Throwable, message: String, vararg args: Any?) = log(Level.SEVERE, error, message, args)
    private fun log(level: Level, error: Throwable?, message: String, args: Array<out Any?>) {
        if (!logger.isLoggable(level)) return
        val formatted = if (args.isEmpty()) message else runCatching { message.format(*args) }.getOrDefault(message)
        logger.log(level, formatted, error)
    }
}
