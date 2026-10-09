package melox.provider.spotify

import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class SpotifyDesktopLoginTest {
    @Test fun pkceUsesRfc7636Challenge() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            SpotifyOAuthLogic.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun loopbackRejectsWrongStateThenExchangesCodeWithVerifier() = runBlocking {
        val server = MockWebServer()
        server.start()
        val old = SpotifySessionStore.read()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"test-access-token","refresh_token":"test-refresh-token","expires_in":3600}"""))
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val request = if (chain.request().url.toString() == SpotifyOAuth.TokenEndpoint) {
                    chain.request().newBuilder().url(server.url("/token")).build()
                } else chain.request()
                chain.proceed(request)
            }.build()
            var authorization: URI? = null
            var state: String? = null
            var verifier: String? = null
            val session = withTimeout(15_000) {
                SpotifyDesktopLogin(http).login("test-client") { uri ->
                    authorization = uri
                    state = SpotifySessionStore.transaction()?.state
                    verifier = SpotifySessionStore.transaction()?.codeVerifier
                    http.newCall(Request.Builder().url(SpotifyOAuth.RedirectUri + "?state=wrong&code=code").build()).execute().use {
                        assertEquals(400, it.code)
                    }
                    http.newCall(Request.Builder().url(SpotifyOAuth.RedirectUri + "?state=$state&code=verified-code").build()).execute().use {
                        assertEquals(200, it.code)
                    }
                }
            }
            assertEquals("127.0.0.1", URI(SpotifyOAuth.RedirectUri).host)
            assertTrue(authorization.toString().contains("code_challenge_method=S256"))
            assertTrue(authorization.toString().contains("redirect_uri=http%3A%2F%2F127.0.0.1"))
            assertEquals("test-access-token", session.accessToken)
            assertEquals("test-refresh-token", SpotifySessionStore.read().refreshToken)
            assertNull(SpotifySessionStore.transaction())
            val request = server.takeRequest()
            val form = SpotifyDesktopLogin.parseCallbackQuery(request.body.readUtf8())
            assertEquals("verified-code", form["code"])
            assertEquals(verifier, form["code_verifier"])
            assertEquals(SpotifyOAuth.RedirectUri, form["redirect_uri"])
        } finally {
            SpotifySessionStore.write(old)
            SpotifySessionStore.clearTransaction()
            server.shutdown()
        }
    }

    @Test fun duplicateCallbackParametersCannotOverrideState() {
        assertFails { SpotifyDesktopLogin.parseCallbackQuery("state=valid&state=evil&code=x") }
    }
}
