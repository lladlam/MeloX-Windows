package melox.platform

actual fun clearDesktopCookieJar() {
    // Desktop cookie jar cleanup is a no-op; no WebView cookie store exists.
}
