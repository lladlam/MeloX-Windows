package melox.provider.jellyfin

import kotlinx.coroutines.*
import melox.music.model.*
import melox.playback.ProviderPlaybackResolver
import melox.playback.toCommonTier
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.test.*
import melox.player.FfmpegPcmDecoder
import okio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Local HTTP protocol fixtures exercise the production client, not a live user server. */
class JellyfinProtocolTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).readTimeout(30, TimeUnit.SECONDS).build()
    private lateinit var session: JellyfinSession
    @BeforeTest fun start() {
        server.start()
        session = JellyfinSession(serverUrl = server.url("/jellyfin").toString(), accessToken = "synthetic-token", userId = "user-one", userName = "Fixture", serverId = "server-one")
    }
    @AfterTest fun stop() { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun provider() = JellyfinProvider({ session }, client)
    private fun request() = assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    private val audio = """{"Id":"audio-one","Name":"Generated tone","Album":"Generated album","RunTimeTicks":80000000,"ArtistItems":[{"Id":"artist-one","Name":"Generated artist"}]}"""

    @Test fun authenticationUsesDesktopIdentityStableDeviceAndNoQueryToken() = runBlocking {
        server.enqueue(json("""{"AccessToken":"synthetic-token","User":{"Id":"user-one","Name":"Fixture"}}"""))
        server.enqueue(json("""{"Id":"server-one","ServerName":"Fixture server","Version":"test-protocol"}"""))
        val result = JellyfinApiClient(client).authenticate(session.baseUrl + "/", "Fixture", "synthetic-password")
        assertEquals(session, result)
        val auth = request()
        assertEquals("/jellyfin/Users/AuthenticateByName", auth.path)
        assertEquals("synthetic-password", JSONObject(auth.body.readUtf8()).getString("Pw"))
        assertTrue(auth.getHeader("X-Emby-Authorization")!!.contains("Client=\"MeloX-Desktop\""))
        assertNull(auth.getHeader("X-Emby-Token"))
        assertEquals(auth.getHeader("X-Emby-Authorization"), request().getHeader("X-Emby-Authorization"))
        server.enqueue(json("{}"))
        JellyfinApiClient(client).publicServerInfo(session.baseUrl)
        assertEquals(auth.getHeader("X-Emby-Authorization"), request().getHeader("X-Emby-Authorization"))
    }

    @Test fun catalogPaginationMappingAndPlaybackKeepNativeIdentityAndHeaders() = runBlocking {
        server.enqueue(json("""{"Items":[$audio],"TotalRecordCount":403}"""))
        val track = provider().searchSongs("空格 + & #", 3, 200).items.single()
        val req = request()
        assertEquals("400", req.requestUrl!!.queryParameter("StartIndex"))
        assertEquals("空格 + & #", req.requestUrl!!.queryParameter("SearchTerm"))
        assertEquals("Audio", req.requestUrl!!.queryParameter("IncludeItemTypes"))
        assertEquals("synthetic-token", req.getHeader("X-Emby-Token"))
        assertEquals(8000L, track.durationMs)
        assertEquals("Generated artist", track.artistText)
        assertEquals(ProviderTrackMetadata.Jellyfin("server-one"), track.providerMetadata)
        val playable = assertIs<PlaybackResolution.Playable>(provider().resolvePlayback(track, AudioQualityTier.Master))
        assertEquals(mapOf("X-Emby-Token" to "synthetic-token"), playable.requestHeaders)
        assertNull(playable.actualQuality)
        assertFalse(playable.url.contains("synthetic-token"))
        assertTrue(playable.url.contains("/Audio/audio-one/universal?UserId=user-one"))
        assertFalse(ProviderPlaybackResolver.uriForTrack(track, AudioQualityTier.Master).contains("synthetic-token"))
        for ((type, fetch) in listOf<Pair<String, suspend () -> Unit>>(
            "MusicAlbum" to { provider().searchAlbums("a", 1, 20); Unit },
            "MusicArtist" to { provider().searchArtists("a", 1, 20); Unit },
            "Playlist" to { provider().userPlaylists(1, 20); Unit },
        )) {
            server.enqueue(json("""{"Items":[],"TotalRecordCount":0}""")); fetch()
            assertEquals(type, request().requestUrl!!.queryParameter("IncludeItemTypes"))
        }
        assertEquals("user-one", provider().accountSummary()!!.id)
    }

    @Test fun playlistDuplicatesUseMembershipIdsForRemoveAndMoveAnd204IsSuccess() = runBlocking<Unit> {
        val playlist = MusicPlaylistSummary(MusicResourceId(MusicSource.Jellyfin, "playlist-one"), "Fixture playlist")
        server.enqueue(json("""{"Items":[${audio.dropLast(1)},"PlaylistItemId":"entry-first"},${audio.dropLast(1)},"PlaylistItemId":"entry-second"}],"TotalRecordCount":2}"""))
        val tracks = provider().playlistDetail(playlist, 1, 100).tracks
        assertEquals(tracks[0].id, tracks[1].id)
        assertEquals("/jellyfin/Playlists/playlist-one/Items", request().requestUrl!!.encodedPath)
        server.enqueue(MockResponse().setResponseCode(204))
        provider().removeTrackFromPlaylist(tracks[1], playlist)
        val remove = request()
        assertEquals("DELETE", remove.method)
        assertEquals("entry-second", remove.requestUrl!!.queryParameter("EntryIds"))
        assertNull(remove.requestUrl!!.queryParameter("Ids"))
        server.enqueue(MockResponse().setResponseCode(204))
        provider().reorderPlaylistTrack(playlist, tracks[0], 1)
        assertEquals("/jellyfin/Playlists/playlist-one/Items/entry-first/Move/1", request().path)
        assertFailsWith<IllegalArgumentException> { provider().removeTrackFromPlaylist(tracks[0].copy(providerMetadata = ProviderTrackMetadata.Empty), playlist) }
    }

    @Test fun realWriteMethodsSendServerEndpointsAndOnlyRenameName() = runBlocking {
        server.enqueue(json("""{"Id":"playlist-new"}"""))
        val playlist = provider().createPlaylist(" New playlist ")
        assertEquals("Audio", JSONObject(request().body.readUtf8()).getString("MediaType"))
        server.enqueue(MockResponse().setResponseCode(204)); provider().renamePlaylist(playlist, "Renamed")
        val rename = request()
        assertEquals("/jellyfin/Playlists/playlist-new", rename.path)
        assertEquals(setOf("Name"), JSONObject(rename.body.readUtf8()).keySet())
        val track = MusicTrack(MusicResourceId(MusicSource.Jellyfin, "audio-one"), "tone", emptyList(), providerMetadata = ProviderTrackMetadata.Jellyfin("server-one"))
        server.enqueue(MockResponse().setResponseCode(204)); provider().addTrackToPlaylist(track, playlist)
        assertEquals("audio-one", request().requestUrl!!.queryParameter("Ids"))
        server.enqueue(json("{}")); provider().setFavorite(track, true)
        assertEquals("POST", request().method)
        server.enqueue(MockResponse().setResponseCode(204)); provider().setFavorite(track, false)
        assertEquals("DELETE", request().method)
    }

    @Test fun foreignSourceServerAndChangedSessionAreRejected() = runBlocking<Unit> {
        val track = MusicTrack(MusicResourceId(MusicSource.Jellyfin, "audio-one"), "tone", emptyList(), providerMetadata = ProviderTrackMetadata.Jellyfin("other-server"))
        assertFailsWith<IllegalArgumentException> { provider().resolvePlayback(track, AudioQualityTier.Standard) }
        assertFailsWith<IllegalArgumentException> { provider().resolvePlayback(track.copy(id = MusicResourceId(MusicSource.Local, "audio-one")), AudioQualityTier.Standard) }
        val reached = CompletableDeferred<Unit>()
        val proceed = java.util.concurrent.CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                reached.complete(Unit)
                proceed.await(2, TimeUnit.SECONDS)
                return json("""{"Items":[$audio]}""")
            }
        }
        val search = async { provider().searchSongs("q", 1, 10) }
        reached.await()
        session = session.copy(userId = "user-two", accessToken = "synthetic-token-two")
        proceed.countDown()
        assertFailsWith<CancellationException> { search.await() }
    }

    @Test fun responseBoundsErrorsAndAuthenticationRedirectsAreEnforced() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(401).setBody("do-not-display-body-token"))
        val error = assertFailsWith<java.io.IOException> { provider().searchSongs("q", 1, 10) }
        assertFalse(error.message!!.contains("do-not-display"))
        request()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/credential-leak")))
        assertFailsWith<java.io.IOException> { JellyfinApiClient(client).authenticate(session.baseUrl, "u", "p") }
        request(); assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        server.enqueue(json("x".repeat(4 * 1024 * 1024 + 1)))
        assertFailsWith<java.io.IOException> { provider().searchSongs("q", 1, 10) }
        request()
        assertFailsWith<IllegalArgumentException> { JellyfinApiClient.normalizeServerUrl("https://user:pass@example.com/") }
    }

    @Test fun cancellingDuringResponseBodyClosesOwnedSocketPromptly() = runBlocking {
        server.enqueue(json("""{"Items":[]}""").throttleBody(1, 10, TimeUnit.SECONDS))
        val pending = launch(Dispatchers.IO) { provider().searchSongs("q", 1, 10) }
        withContext(Dispatchers.IO) { request() }
        withTimeout(2000) { pending.cancelAndJoin() }
        assertTrue(pending.isCancelled)
    }

    @Test fun artworkCredentialsAreRestrictedToOriginBasePathAndImageEndpoint() {
        val image = session.baseUrl + "/Items/audio-one/Images/Primary"
        assertEquals(mapOf("X-Emby-Token" to "synthetic-token"), JellyfinArtworkAccess.requestHeaders(image, session))
        for (url in listOf(server.url("/Items/audio-one/Images/Primary").toString(), session.baseUrl + "/Users/user-one", image.replace("localhost", "foreign.invalid"))) {
            assertTrue(JellyfinArtworkAccess.requestHeaders(url, session).isEmpty(), url)
        }
        assertTrue(JellyfinArtworkAccess.requestHeaders(image, JellyfinSession()).isEmpty())
        val key = JellyfinArtworkAccess.cacheKey(image, session)
        assertFalse(key.contains(session.accessToken))
        assertNotEquals(key, JellyfinArtworkAccess.cacheKey(image, session.copy(accessToken = "synthetic-token-two")))
        assertNotEquals(key, JellyfinArtworkAccess.cacheKey(image, JellyfinSession()))
    }

    @Test fun productionResolverRetainsServerMetadataSeparatesAccountsAndDoesNotInventQuality() {
        val track = MusicTrack(MusicResourceId(MusicSource.Jellyfin, "audio-one"), "generated", emptyList(),
            providerMetadata = ProviderTrackMetadata.Jellyfin("server-one", "playlist-one", "entry-one"))
        val uri = ProviderPlaybackResolver.uriForTrack(track, melox.audio.MusicQualityRuntime.selected.toCommonTier())
        val resolver = ProviderPlaybackResolver(
            melox.playback.NeteasePlaybackResolver(cookieProvider = { "" }, localSourceProvider = { null }),
            melox.music.provider.MusicProviderRegistry(listOf(provider())),
            authKeyProvider = { session.accessToken },
        )
        assertEquals("synthetic-token", resolver.resolveProviderRequest(uri).headers["X-Emby-Token"])
        assertNull(melox.playback.ProviderPlaybackQualityRuntime.actualFor(track.id))
        session = session.copy(accessToken = "synthetic-token-two", userId = "user-two")
        val second = resolver.resolveProviderRequest(uri)
        assertEquals("synthetic-token-two", second.headers["X-Emby-Token"])
        assertTrue(second.uri.contains("UserId=user-two"))
        session = session.copy(serverId = "other-server", accessToken = "synthetic-token-three")
        assertFailsWith<IllegalArgumentException> { resolver.resolveProviderRequest(uri) }
    }

    @Test fun nativeDecoderConsumesActualAuthorizedUniversalStreamPcm() = runBlocking {
        val frames = 11025
        val wave = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        wave.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { index ->
            val value = (6000 * sin(2 * PI * 1000 * index / 44100)).roundToInt().toShort()
            wave.putShort(value); wave.putShort(value)
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wave.array())))
        val track = MusicTrack(MusicResourceId(MusicSource.Jellyfin, "audio-one"), "generated", emptyList(), providerMetadata = ProviderTrackMetadata.Jellyfin("server-one"))
        val playback = assertIs<PlaybackResolution.Playable>(provider().resolvePlayback(track, AudioQualityTier.Standard))
        val output = java.io.ByteArrayOutputStream()
        FfmpegPcmDecoder(playback.url, playback.requestHeaders, 0).use { decoder ->
            val block = ByteArray(3528)
            while (true) {
                val count = decoder.read(block)
                if (count == 0) break
                output.write(block, 0, count)
            }
            decoder.verifyExit()
        }
        val req = request()
        assertEquals("synthetic-token", req.getHeader("X-Emby-Token"))
        assertEquals("/jellyfin/Audio/audio-one/universal", req.requestUrl!!.encodedPath)
        val pcm = ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(frames * 4, pcm.remaining())
        var energy = 0.0
        while (pcm.hasRemaining()) { val sample = pcm.short.toDouble(); energy += sample * sample }
        assertTrue(sqrt(energy / (frames * 2)) in 4200.0..4280.0)
    }
}
