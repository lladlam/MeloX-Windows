package melox.playback

import melox.audio.MusicQuality
import melox.audio.NeteasePlaybackUnavailableException
import melox.audio.NeteaseQualityClient
import melox.music.model.*
import melox.music.provider.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Protocol fixtures are local; the online search/decoder replay is separate evidence. */
class NeteasePlaybackRequestTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
        val url = chain.request().url
        chain.proceed(chain.request().newBuilder().url(server.url(url.encodedPath)).build())
    }.build()

    @BeforeTest fun start() { server.start() }
    @AfterTest fun stop() {
        server.shutdown()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    private fun qualityClient(cookie: () -> String = { "" }) = NeteaseQualityClient(cookie, http)
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun availability() = server.enqueue(json("""{"code":200,"songs":[{"id":139774,"l":{"br":128000}}]}"""))
    private fun playable(url: String = "https://audio.invalid/full.mp3") = server.enqueue(json(
        """{"code":200,"data":[{"id":139774,"url":"$url","level":"standard","br":128000,"type":"mp3","freeTrialInfo":null}]}""",
    ))

    @Test fun searchMetadataRoundTripsWithoutSpacesQueryInjectionOrDoubleDecoding() {
        val resolver = NeteasePlaybackResolver()
        for (title in listOf("The\u00a0truth\u00a0that\u00a0you\u00a0leave", "English title", "标题 & + # % = ? /", "literal %26artist%3Dother", "emoji 🎵")) {
            val artist = "Artist A / 歌手 B & + # %26"
            val uri = URI(NeteasePlaybackResolver.uriForSong(139774, MusicQuality.High, title, artist, 223467))
            assertNull(uri.fragment)
            val request = assertNotNull(resolver.fallbackRequest(uri, 139774, MusicQuality.High))
            assertEquals(title, request.title)
            assertEquals(artist, request.artist)
            assertEquals(223467L, request.durationMs)
        }
        assertNull(resolver.fallbackRequest(URI("melox://song/139774?quality=standard&oldFlag"), 139774, MusicQuality.Standard))
    }

    @Test fun encodedSearchActuallyResolvesOfficialFullAudioAndKeepsLegacyBareUriWorking() {
        availability(); playable()
        val resolver = NeteasePlaybackResolver(qualityClient = qualityClient())
        val uri = NeteasePlaybackResolver.uriForSong(139774, MusicQuality.Standard, "The\u00a0truth that you leave", "Artist A / Artist B & +", 223467)
        assertEquals("https://audio.invalid/full.mp3", resolver.resolveUri(uri).uri)
        assertEquals("/eapi/v3/song/detail", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        assertEquals("/eapi/song/enhance/player/url/v1", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        availability(); playable()
        assertEquals("https://audio.invalid/full.mp3", resolver.resolveUri("melox://song/139774?quality=standard&legacyFlag").uri)
    }

    @Test fun anonymousPreviewAndNoUrlDoNotBecomeFalseFullPlaybackSuccess() {
        availability()
        server.enqueue(json("""{"code":200,"data":[{"id":139774,"url":"https://audio.invalid/preview.mp3","freeTrialInfo":{"start":0,"end":30}}]}"""))
        assertFailsWith<NeteasePlaybackUnavailableException> { qualityClient().playbackSourceBlocking(139774, MusicQuality.Standard) }
        availability()
        server.enqueue(json("""{"code":200,"data":[{"id":139774,"url":null,"code":404}]}"""))
        assertFailsWith<NeteasePlaybackUnavailableException> { qualityClient().playbackSourceBlocking(139774, MusicQuality.Standard) }
    }

    @Test fun providerSearchUsesNativeDownloadedNeteaseIdAndSeesLocalSourceChanges() {
        var local = "file:///download-one.mp3"
        var lookedUp: Long? = null
        val netease = NeteasePlaybackResolver(localSourceProvider = { lookedUp = it; local })
        val resolver = ProviderPlaybackResolver(netease, MusicProviderRegistry(listOf(melox.provider.local.LocalProvider())))
        val track = MusicTrack(MusicResourceId(MusicSource.Netease, "139774"), "Title + & #", listOf(MusicArtistRef(name = "Artist A")))
        val uri = ProviderPlaybackResolver.uriForTrack(track, AudioQualityTier.Standard)
        assertEquals(local, resolver.resolveProviderRequest(uri).uri)
        assertEquals(139774L, lookedUp)
        local = "file:///download-two.mp3"
        assertEquals(local, resolver.resolveProviderRequest(uri).uri)
        assertFails { resolver.resolveProviderRequest(uri.replace("/139774?", "/invalid?")) }
    }

    @Test fun providerAndLegacySearchShareOptInFallbackWithExactDecodedMetadataAndHeaders() {
        val track = MusicTrack(MusicResourceId(MusicSource.Netease, "139774"), "Title + & #", listOf(MusicArtistRef(name = "Artist A")), durationMs = 223467)
        var enabled = true
        val fallbackProvider = object : MusicProvider, SearchCapability, PlaybackCapability {
            override val source = MusicSource.QQMusic
            override val displayName = "Protocol fixture"
            override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback)
            override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
                assertEquals("Title + & # Artist A", query)
                return MusicPage(listOf(track.copy(id = MusicResourceId(source, "native-qq"))), page, pageSize, 1L, false)
            }
            override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier) = PlaybackResolution.Playable(
                url = "https://audio.invalid/fallback.mp3", requestHeaders = mapOf("Referer" to "https://fixture.invalid/"), requestedQuality = quality,
            )
        }
        val netease = NeteasePlaybackResolver(qualityClient = qualityClient(), crossProviderFallback = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { enabled }, registryProvider = { MusicProviderRegistry(listOf(fallbackProvider)) },
        ))
        val resolver = ProviderPlaybackResolver(netease, MusicProviderRegistry(listOf(melox.provider.local.LocalProvider())))
        fun unavailable() {
            availability()
            server.enqueue(json("""{"code":200,"data":[{"id":139774,"url":null,"code":404}]}"""))
        }
        unavailable()
        val uri = ProviderPlaybackResolver.uriForTrack(track, AudioQualityTier.Standard)
        val resolved = resolver.resolveProviderRequest(uri)
        assertEquals("https://audio.invalid/fallback.mp3", resolved.uri)
        assertEquals(mapOf("Referer" to "https://fixture.invalid/"), resolved.headers)
        enabled = false
        unavailable()
        assertFailsWith<NeteasePlaybackUnavailableException> { resolver.resolveProviderRequest(uri) }
        CrossProviderPlaybackRuntime.clear(139774)
    }
}
