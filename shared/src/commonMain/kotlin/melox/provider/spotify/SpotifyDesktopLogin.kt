package melox.provider.spotify

import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import melox.network.MeloXHttpClient
import okhttp3.OkHttpClient

/** Binds before opening the browser; listener and PKCE state die with the login screen. */
class SpotifyDesktopLogin(private val httpClient: OkHttpClient = MeloXHttpClient.shared) {
    suspend fun login(
        clientId: String = SpotifyClientConfig.effective(),
        openBrowser: (URI) -> Unit = { Desktop.getDesktop().browse(it) },
    ): SpotifySession = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "请先配置 Spotify Client ID" }
        check(loginLock.tryLock()) { "Spotify 登录已在进行中" }
        var server: HttpServer? = null
        try {
            val redirect = URI(SpotifyOAuth.RedirectUri)
            val listener = HttpServer.create(InetSocketAddress(redirect.host, redirect.port), 0)
            server = listener
            val callback = CompletableDeferred<String>()
            val oauth = SpotifyOAuth(clientId, httpClient)
            val authorization = URI(oauth.authorizationUrl())
            val expectedState = requireNotNull(SpotifySessionStore.transaction()).state
            listener.createContext(redirect.path) { exchange ->
                exchange.use {
                    val params = runCatching { parseCallbackQuery(exchange.requestURI.rawQuery.orEmpty()) }.getOrDefault(emptyMap())
                    val valid = exchange.requestMethod == "GET" && exchange.requestURI.path == redirect.path &&
                        exchange.remoteAddress.address.isLoopbackAddress &&
                        SpotifyOAuthLogic.stateMatches(expectedState, params["state"]) &&
                        (!params["code"].isNullOrBlank() || !params["error"].isNullOrBlank())
                    val accepted = valid && callback.complete(SpotifyOAuth.RedirectUri + "?" + exchange.requestURI.rawQuery)
                    val text = if (accepted) "Authorization received. Return to MeloX to finish signing in." else "Invalid or expired authorization callback."
                    val body = text.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                    exchange.responseHeaders.set("Cache-Control", "no-store")
                    exchange.sendResponseHeaders(if (accepted) 200 else 400, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            }
            listener.start()
            openBrowser(authorization)
            withTimeout(10 * 60_000L) { oauth.handleCallback(callback.await()) }
        } finally {
            server?.stop(0)
            try { SpotifySessionStore.clearTransaction() } finally { loginLock.unlock() }
        }
    }

    companion object {
        private val loginLock = Mutex()
        internal fun parseCallbackQuery(query: String): Map<String, String> = query.split('&').mapNotNull { pair ->
            val pieces = pair.split('=', limit = 2)
            if (pieces.size != 2) null else URLDecoder.decode(pieces[0], "UTF-8") to URLDecoder.decode(pieces[1], "UTF-8")
        }.also { pairs -> require(pairs.map { it.first }.distinct().size == pairs.size) { "Duplicate callback parameter" } }.toMap()
    }
}
