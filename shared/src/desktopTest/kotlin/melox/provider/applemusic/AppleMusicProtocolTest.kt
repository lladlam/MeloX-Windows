package melox.provider.applemusic

import kotlinx.coroutines.runBlocking
import melox.lyrics.LyricSource
import melox.music.model.*
import melox.music.provider.MusicCapability
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.*

class AppleMusicProtocolTest {
    private fun client(handler: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("protocol fixture")
            .body(handler(request).toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Test fun sessionTokensArePrivateInLogsAndRoundTripWithoutLoss() {
        val session = AppleMusicSession(developerToken = "developer-secret", musicUserToken = "user-secret", storefront = "JP")
        assertFalse(session.toString().contains("developer-secret"))
        assertFalse(session.toString().contains("user-secret"))
        assertTrue(session.isConfigured && session.hasUserAuthorization)
        val unconfigured = AppleMusicSession()
        assertFalse(unconfigured.isConfigured)
        assertFalse(unconfigured.hasUserAuthorization)
        assertFalse(AppleMusicSession(developerToken = "d").hasUserAuthorization)
    }

    @Test fun searchUsesOfficialCatalogAndPreservesNativeIdentityWithoutPreviewPlayback() = runBlocking<Unit> {
        val requests = mutableListOf<Request>()
        val http = client { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("/search") -> {
                    assertEquals("native track", request.url.queryParameter("term"))
                    assertEquals("songs", request.url.queryParameter("types"))
                    assertEquals("Bearer developer", request.header("Authorization"))
                    assertNull(request.header("Music-User-Token"))
                    """{"results":{"songs":{"next":"more","meta":{"total":9},"data":[
                        {"id":"111","attributes":{"name":"Native song","artistName":"Artist","albumName":"Album","durationInMillis":180000,
                         "artwork":{"url":"https://img.example/{w}x{h}.jpg"},"previews":[{"url":"https://p.example/preview.m4a"}]}}]}}}"""
                }
                else -> """{"results":{}}"""
            }
        }
        val session = AppleMusicSession(developerToken = "developer")
        val provider = AppleMusicApiClient({ session }, http)
        assertTrue(MusicCapability.Search in provider.capabilities)
        assertTrue(MusicCapability.Playback !in provider.capabilities, "REST preview cannot advertise full playback")
        val page = provider.searchSongs("native track", 1, 10)
        val track = page.items.single()
        assertEquals(MusicResourceId(MusicSource.AppleMusic, "111"), track.id)
        assertEquals(180_000L, track.durationMs)
        assertEquals("https://img.example/800x800.jpg", track.artworkUrl)
        assertEquals(TrackAvailability.PreviewOnly, track.availability)
        assertEquals(9L, page.total)
        assertTrue(page.hasMore)
        val preview = assertIs<ProviderTrackMetadata.AppleMusic>(track.providerMetadata).previewUrl
        assertEquals("https://p.example/preview.m4a", preview)
        // The preview URL exists as metadata but full playback is still explicitly refused.
        val resolution = provider.resolvePlayback(track, AudioQualityTier.Standard)
        assertIs<PlaybackResolution.Unavailable>(resolution)
        assertTrue(requests.all { it.header("User-Agent") == "MeloX-Desktop/1.0" && it.header("Accept-Language") != null })
    }

    @Test fun credentialValidationVerifiesAuthorizedStorefrontAndNeverAcceptsPartialCredentials() = runBlocking<Unit> {
        var charts = 0
        val http = client { request -> when {
            request.url.encodedPath.startsWith("/v1/catalog/") && request.url.encodedPath.endsWith("/charts") -> {
                assertTrue(request.url.encodedPath in setOf("/v1/catalog/us/charts", "/v1/catalog/jp/charts"))
                charts++; """{"results":{}}"""
            }
            request.url.encodedPath == "/v1/me/storefront" -> {
                assertEquals("Bearer developer", request.header("Authorization"))
                assertEquals("user-token", request.header("Music-User-Token"))
                """{"data":[{"id":"gb"}]}"""
            }
            else -> error("Unexpected ${request.url.encodedPath}")
        } }
        val authorized = AppleMusicApiClient({ AppleMusicSession("developer", "user-token", "jp") }, http).validateSession()
        assertEquals("gb", authorized.storefront)
        assertEquals(1, charts)

        val missingUser = AppleMusicApiClient({ AppleMusicSession("developer", "", "jp") }, http).validateSession()
        assertEquals("jp", missingUser.storefront)

        assertFailsWith<IllegalArgumentException> {
            runBlocking { AppleMusicApiClient({ AppleMusicSession(musicUserToken = "user-token") }, http).validateSession() }
        }
        val failing = AppleMusicApiClient({ AppleMusicSession("developer", "user-token") }, client {
            """{"errors":[{"status":"401","title":"unauthorized"}]}"""
        })
        assertFailsWith<java.io.IOException> { failing.validateSession() }
    }

    @Test fun invalidCatalogIdentityIsRejectedInsteadOfReachingUrlPath() = runBlocking<Unit> {
        val provider = AppleMusicApiClient({ AppleMusicSession("developer") }, client { error("must not be called") })
        assertFailsWith<IllegalArgumentException> {
            runBlocking { provider.lyrics(MusicTrack(MusicResourceId(MusicSource.AppleMusic, "../me/library"), "x", emptyList())) }
        }
        assertFailsWith<IllegalArgumentException> {
            runBlocking { provider.albumDetail(MusicAlbumSummary(MusicResourceId(MusicSource.AppleMusic, "a/b"), "x", null)) }
        }
    }

    @Test fun lyricsUseRealTtmlAndPlainAppleResponseWithoutSyntheticWordTiming() = runBlocking<Unit> {
        fun lyricsJson(ttml: String?, text: String?): String {
            val attributes = JSONObject().apply { ttml?.let { put("ttml", it) }; text?.let { put("text", it) } }
            return JSONObject().put("data", JSONArray().put(JSONObject().put("relationships",
                JSONObject().put("lyrics", JSONObject().put("data",
                    JSONArray().put(JSONObject().put("attributes", attributes))))))).toString()
        }
        val ttmlBody = "<tt xmlns='http://www.w3.org/ns/ttml'><body><div><p begin='00:00:01.000' end='00:00:03.000'>First line</p></div></body></tt>"
        val http = client { request -> assertEquals("lyrics", request.url.queryParameter("include")); lyricsJson(ttmlBody, null) }
        val provider = AppleMusicApiClient({ AppleMusicSession("developer") }, http)
        val ttml = provider.lyrics(MusicTrack(MusicResourceId(MusicSource.AppleMusic, "1"), "x", emptyList()))
        assertEquals(LyricSource.AppleMusic, ttml.source)
        assertEquals("First line", ttml.lines.single().text)
        assertEquals(1_000L, ttml.lines.single().timeMs)

        val plainProvider = AppleMusicApiClient({ AppleMusicSession("developer") }, client { lyricsJson(null, "line one\nline two") })
        val plain = plainProvider.lyrics(MusicTrack(MusicResourceId(MusicSource.AppleMusic, "1"), "x", emptyList()))
        assertEquals(1, plain.lines.size, "Untimed block must stay one authored line")
        assertEquals("line one\nline two", plain.lines.single().text)
        assertEquals(0L, plain.lines.single().timeMs)
        assertFalse(plain.pseudoTimingAllowed, "Untimed Apple lyrics must not be presented as word timing")

        // A TTML payload without the TTML namespace cannot be parsed: fall back to the real text.
        val fallback = AppleMusicApiClient({ AppleMusicSession("developer") }, client { lyricsJson("<tt><body/></tt>", "fallback text") })
            .lyrics(MusicTrack(MusicResourceId(MusicSource.AppleMusic, "1"), "x", emptyList()))
        assertEquals("fallback text", fallback.lines.single().text)
        assertFalse(fallback.pseudoTimingAllowed)

        val empty = AppleMusicApiClient({ AppleMusicSession("developer") }, client { """{"data":[]}""" })
            .lyrics(MusicTrack(MusicResourceId(MusicSource.AppleMusic, "1"), "x", emptyList()))
        assertTrue(empty.lines.isEmpty())
    }
}
