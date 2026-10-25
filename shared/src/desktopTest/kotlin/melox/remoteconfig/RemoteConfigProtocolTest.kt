package melox.remoteconfig

import kotlinx.coroutines.*
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import melox.platform.getPreferences
import melox.music.model.MusicSource
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Proxy
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Native P-256 signatures and actual HTTP/cache/cancellation. The server and signing
 * key are test fixtures; production retains its pinned public key and fixed endpoints.
 */
class RemoteConfigProtocolTest {
    private val root = Files.createTempDirectory("melox-remote-protocol").toFile()
    private val server = MockWebServer()
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val store = MeloXRemoteConfigStore(File(root, "state.json"))
    private val runtimeClient = MeloXRemoteConfigRuntime::class.java.getDeclaredField("client").apply { isAccessible = true }
    private val originalClient = runtimeClient.get(null)
    private var time = 1000L
    private lateinit var client: MeloXRemoteConfigClient

    @BeforeTest fun start() {
        server.start()
        val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/latest.json")).build())
        }.build()
        val routing = MeloXGitHubRouting(http)
        routing.selectSource(MeloXGitHubSource.GhFast)
        client = MeloXRemoteConfigClient(store, MeloXRemoteConfigVerifier(key.public.encoded, "fixture-p256"), routing, now = { time })
        MeloXRemoteConfigRuntime.stop()
        getPreferences("melox_remote_config_consent").clear()
        runtimeClient.set(null, client)
        MeloXRemoteConfigRuntime.initializeAndRefresh()
    }

    @AfterTest fun close() = runBlocking<Unit> {
        MeloXRemoteConfigRuntime.setEnabled(false)
        runtimeClient.set(null, originalClient)
        getPreferences("melox_remote_config_consent").clear()
        getPreferences("melox_github_routing").clear()
        server.shutdown()
        root.deleteRecursively()
    }

    private val fixtureOrder = listOf("bilibili", "kugou", "qq_music", "netease", "qishui", "kuwo")
    private fun envelope(version: Int = 5, min: Int = 10, max: Int = 20, disabled: List<String> = listOf("qq_playback"), order: List<String> = fixtureOrder): String {
        val payload = JSONObject().put("schemaVersion", 1).put("configVersion", version).put("issuedAtEpochSeconds", 100)
            .put("minVersionCode", min).put("maxVersionCode", max).put("disabledCapabilities", JSONArray(disabled))
            .put("strategies", JSONObject().put("netease_playback", "v1").put("qq_playback", "v1").put("kugou_playback", "v1"))
            .put("crossProviderFallback", JSONObject().put("enabled", true).put("timeoutMs", 4000)
                .put("order", JSONArray(order)).put("disabledProviders", JSONArray(listOf("kugou"))))
            .toString().toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run { initSign(key.private); update(payload); sign() }
        return JSONObject().put("keyId", "fixture-p256").put("algorithm", "SHA256withECDSA")
            .put("payload", Base64.getUrlEncoder().withoutPadding().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString()
    }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private suspend fun refresh() = withTimeout(5000) { client.refresh(20, force = true) }
    private suspend fun until(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(10) }

    @Test fun realSignedHttpPersistsEtagAnd304KeepsVerifiedConfigWithoutAccountHeaders() = runBlocking {
        server.enqueue(response(envelope()).setHeader("ETag", "\"v5\""))
        val first = refresh()
        assertEquals(MeloXRemoteConfigSource.VerifiedRemote, first.source)
        assertEquals(5, first.config.configVersion)
        assertEquals(fixtureOrder, first.config.fallback.order)
        val request = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        assertEquals("MeloX-Desktop", request.getHeader("User-Agent"))
        time = 2000L; server.enqueue(MockResponse().setResponseCode(304))
        val next = refresh()
        assertEquals("\"v5\"", checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)).getHeader("If-None-Match"))
        assertEquals(first.config, next.config)
        assertEquals(2000L, next.lastCheckedAtEpochMs); assertEquals(1000L, next.lastUpdatedAtEpochMs)
        assertEquals(5, store.read().highestConfigVersion)
        assertEquals(5, client.load(20).config.configVersion)
    }

    @Test fun legacySignedOrderUsesExpandedBuiltInOrderAndRetainsDisabledSources() = runBlocking {
        server.enqueue(response(envelope(order = listOf("bilibili", "kugou", "qq_music"))))
        val status = refresh()
        assertEquals(MeloXRemoteConfigSource.VerifiedRemote, status.source)
        assertEquals(MeloXRemoteConfigDefaults.FallbackOrder, status.config.fallback.order)
        assertEquals(setOf("kugou"), status.config.fallback.disabledProviders)
    }

    @Test fun tamperedSignatureRetainsLastValidCacheAndReturnsActualVerificationError() = runBlocking {
        server.enqueue(response(envelope())); refresh()
        val valid = store.read().envelope
        val tampered = JSONObject(envelope()).put("payload", Base64.getUrlEncoder().encodeToString("{}".toByteArray()))
        server.enqueue(response(tampered.toString()))
        val status = refresh()
        assertEquals(5, status.config.configVersion)
        assertTrue(status.error.orEmpty().contains("signature"))
        assertEquals(valid, store.read().envelope)
    }

    @Test fun clearingCachePreservesRollbackFloorAndRejectsOlderEvenCorrectlySignedEnvelope() = runBlocking {
        server.enqueue(response(envelope(6))); refresh()
        client.clear(20)
        assertNull(store.read().envelope); assertEquals(6, store.read().highestConfigVersion)
        server.enqueue(response(envelope(5)))
        val status = refresh()
        assertEquals(MeloXRemoteConfigSource.BuiltIn, status.source)
        assertTrue(status.error.orEmpty().contains("rollback"))
        assertNull(store.read().envelope); assertEquals(6, store.read().highestConfigVersion)
    }

    @Test fun oversizedChunkedBodyIsBoundedAndNeverBecomesCachedConfiguration() = runBlocking {
        server.enqueue(response(envelope())); refresh()
        val valid = store.read().envelope
        server.enqueue(MockResponse().setChunkedBody("x".repeat(MeloXRemoteConfigVerifier.MaxEnvelopeBytes + 10000), 8192))
        val status = refresh()
        assertTrue(status.error.orEmpty().contains("too large"))
        assertEquals(valid, store.read().envelope)
    }

    @Test fun versionAndConsentGateTheActualPolicyAndNeverDisableUserAddedOrLocalSources() = runBlocking {
        server.enqueue(response(envelope(disabled = listOf("qq_playback", "arbitrary_local_switch"))))
        val verified = refresh()
        val effective = MeloXRemoteConfigPolicy.effectiveConfig(true, verified)
        assertFalse(MeloXRemoteConfigPolicy.providerPlaybackEnabled(effective, MusicSource.QQMusic))
        assertTrue(MeloXRemoteConfigPolicy.providerPlaybackEnabled(effective, MusicSource.Local))
        assertTrue(MeloXRemoteConfigPolicy.providerPlaybackEnabled(effective, MusicSource.Jellyfin))
        assertFalse("arbitrary_local_switch" in effective.disabledCapabilities)
        assertTrue(MeloXRemoteConfigPolicy.providerPlaybackEnabled(MeloXRemoteConfigPolicy.effectiveConfig(false, verified), MusicSource.QQMusic))
        server.enqueue(response(envelope(6, min = 21, max = 30)))
        val inapplicable = refresh()
        assertEquals(MeloXRemoteConfigSource.VersionInapplicable, inapplicable.source)
        assertEquals(MeloXRemoteConfigDefaults.Config, MeloXRemoteConfigPolicy.effectiveConfig(true, inapplicable))
    }

    @Test fun disabledRuntimeMakesNoRequestAndExplicitConsentPerformsGenuineSignedRefresh() = runBlocking {
        MeloXRemoteConfigRuntime.refresh()
        assertEquals(0, server.requestCount)
        assertEquals(MeloXRemoteConfigSource.BuiltIn, MeloXRemoteConfigRuntime.status.value.source)
        server.enqueue(response(envelope()))
        MeloXRemoteConfigRuntime.setEnabled(true)
        until { MeloXRemoteConfigRuntime.status.value.source == MeloXRemoteConfigSource.VerifiedRemote }
        assertTrue(MeloXRemoteConfigRuntime.enabled.value)
        assertEquals(5, MeloXRemoteConfigPolicy.activeConfig().configVersion)
        assertFalse(MeloXRemoteConfigPolicy.providerPlaybackEnabled(MusicSource.QQMusic))
        assertTrue(MeloXRemoteConfigPolicy.providerPlaybackEnabled(MusicSource.Local))
    }

    @Test fun withdrawingDuringBlockedResponseCancelsSocketClearsCacheAndRestoresBuiltinPolicyPromptly() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        MeloXRemoteConfigRuntime.setEnabled(true)
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })
        withTimeout(2000) { MeloXRemoteConfigRuntime.setEnabled(false) }
        assertFalse(MeloXRemoteConfigRuntime.enabled.value)
        assertFalse(MeloXRemoteConfigConsent.enabled())
        assertNull(store.read().envelope)
        assertFalse(MeloXRemoteConfigRuntime.status.value.refreshing)
        assertEquals(MeloXRemoteConfigDefaults.Config, MeloXRemoteConfigPolicy.activeConfig())
    }

    @Test fun signedLoginBreakersPreventActualPhoneRequestsAndBrowserOpeningWhileLocalStillResolves() = runBlocking<Unit> {
        server.enqueue(response(envelope(disabled = listOf("netease_phone_login", "qq_phone_login", "spotify_oauth", "qq_playback"))))
        MeloXRemoteConfigRuntime.setEnabled(true)
        until { MeloXRemoteConfigRuntime.status.value.source == MeloXRemoteConfigSource.VerifiedRemote }
        val before = server.requestCount
        val netease = melox.network.NeteasePhoneAuthClient()
        val qq = melox.provider.qqmusic.QQMusicPhoneAuthClient()
        assertFailsWith<IllegalStateException> { netease.sendCode("86", "00000000000") }
        assertFailsWith<IllegalStateException> { netease.login("86", "00000000000", "000000") }
        assertFailsWith<IllegalStateException> { qq.sendCode("86", "00000000000") }
        assertFailsWith<IllegalStateException> { qq.login("86", "00000000000", "000000") }
        var browserOpened = false
        assertFailsWith<IllegalStateException> {
            melox.provider.spotify.SpotifyDesktopLogin().login("fixture-client", openBrowser = { browserOpened = true })
        }
        assertFalse(browserOpened)
        assertEquals(before, server.requestCount, "Breakers are checked before starting login transports")
        val localFile = File(root, "本地 + # %.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val track = melox.music.model.MusicTrack(
            melox.music.model.MusicResourceId(MusicSource.Local, "own-file"), "Local", emptyList(),
            providerMetadata = melox.music.model.ProviderTrackMetadata.Local(localFile.toURI().toString(), "own-file"),
        )
        val resolver = melox.playback.ProviderPlaybackResolver(
            melox.playback.NeteasePlaybackResolver(cookieProvider = { "" }, localSourceProvider = { null }),
            melox.music.provider.MusicProviderRegistry(listOf(melox.provider.local.LocalProvider())),
            providerPlaybackEnabled = MeloXRemoteConfigPolicy::providerPlaybackEnabled,
        )
        val request = resolver.resolveProviderRequest(melox.playback.ProviderPlaybackResolver.uriForTrack(track, melox.music.model.AudioQualityTier.Standard))
        assertEquals(localFile.canonicalPath, request.uri)
    }

    @Test fun foregroundResumeRefreshesSignedPolicyAndStopPreventsLaterAutomaticRequests() = runBlocking<Unit> {
        server.enqueue(response(envelope()))
        MeloXRemoteConfigRuntime.setEnabled(true)
        until { MeloXRemoteConfigRuntime.status.value.config.configVersion == 5 }
        val before = server.requestCount
        MeloXRemoteConfigRuntime.setForeground(false)
        assertEquals(before, server.requestCount)
        server.enqueue(response(envelope(6)))
        MeloXRemoteConfigRuntime.setForeground(true)
        until { MeloXRemoteConfigRuntime.status.value.config.configVersion == 6 }
        assertEquals(before + 1, server.requestCount)
        repeat(before + 1) { assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(1, TimeUnit.SECONDS) }) }
        MeloXRemoteConfigRuntime.stop()
        MeloXRemoteConfigRuntime.setForeground(false)
        MeloXRemoteConfigRuntime.setForeground(true)
        assertNull(withContext(Dispatchers.IO) { server.takeRequest(100, TimeUnit.MILLISECONDS) })
    }

    @Test fun corruptCachedEnvelopeDropsConditionalEtagAndRefetchesWithoutLosingRollbackFloor() = runBlocking<Unit> {
        server.enqueue(response(envelope()).setHeader("ETag", "\"v5\""))
        refresh()
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        store.write(store.read().copy(envelope = "corrupt envelope"))
        assertEquals(MeloXRemoteConfigSource.BuiltIn, client.load(20).source)
        server.enqueue(response(envelope(6)).setHeader("ETag", "\"v6\""))
        val repaired = refresh()
        assertNull(checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)).getHeader("If-None-Match"))
        assertEquals(MeloXRemoteConfigSource.VerifiedRemote, repaired.source)
        assertEquals(6, repaired.config.configVersion)
        assertEquals(6, store.read().highestConfigVersion)
    }
}
