package melox.platform

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L

actual fun logDebug(tag: String, message: String) = println(melox.diagnostics.MeloXProcessLogs.append("DEBUG", tag, message))

actual fun logWarn(tag: String, message: String) = println(melox.diagnostics.MeloXProcessLogs.append("WARN", tag, message))
actual fun logError(tag: String, message: String) = System.err.println(melox.diagnostics.MeloXProcessLogs.append("ERROR", tag, message))

actual fun logInfo(tag: String, message: String) = println(melox.diagnostics.MeloXProcessLogs.append("INFO", tag, message))
