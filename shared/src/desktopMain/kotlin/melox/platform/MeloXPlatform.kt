package melox.platform

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L

actual fun logDebug(tag: String, message: String) = println("[$tag] $message")

actual fun logWarn(tag: String, message: String) = println("[WARN $tag] $message")
actual fun logError(tag: String, message: String) = System.err.println("[$tag] $message")

actual fun logInfo(tag: String, message: String) = println("[$tag] $message")
