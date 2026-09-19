package melox.platform

expect fun currentTimeMillis(): Long

expect fun elapsedRealtime(): Long

expect fun logDebug(tag: String, message: String)

expect fun logWarn(tag: String, message: String)

expect fun logError(tag: String, message: String)

expect fun logInfo(tag: String, message: String)

expect fun clearDesktopCookieJar()
