package melox.provider.qishui

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.music.provider.MusicCapability
import melox.music.provider.loadAllPlaylistTracks
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import kotlin.test.*

/** Explicit HTTP protocol responses, not evidence of authenticated upstream playback. */
class QishuiProviderTest {
    private val trackId = "7079108541549643812"
    private val trackJson = """{"id":"$trackId","name":"测试歌曲","duration":297240,"artists":[{"id":"a1","name":"歌手"}],"album":{"id":"b1","name":"专辑","url_cover":{"urls":["https://example.com/"],"uri":"cover.jpg"}}}"""
    private val session = QishuiSession("session-test", "u1", "用户", "1234567890123456", "2345678901234567")
    private fun client(body: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body(body(chain.request()).toResponseBody()).build()
    }.build()
    private fun seo(duration: Double = 30.0, encrypted: Boolean = false): JSONObject = JSONObject()
        .put("seo_track", JSONObject().put("track", JSONObject(trackJson)))
        .put("track_player", JSONObject().put("expire_at", 1793000000L).put("video_model", JSONObject()
            .put("video_duration", duration).apply { if (encrypted) put("spade_a", "encrypted") }
            .put("video_list", org.json.JSONArray("""[{"main_url":"https://audio.douyinvod.com/test.m4a","video_meta":{"bitrate":128000,"vtype":"m4a"}}]""")).toString()))

    @Test fun nestedTracksPreserveLongIdentityMillisecondsAndArtwork() {
        val track = assertNotNull(QishuiParsing.track(JSONObject("""{"entity":{"track_wrapper":{"track":$trackJson}}}""")))
        assertEquals(MusicResourceId(MusicSource.Qishui, trackId), track.id)
        assertEquals(297240L, track.durationMs); assertEquals("歌手", track.artistText)
        assertEquals("https://example.com/cover.jpg", track.artworkUrl)
        assertNotEquals(MusicResourceId(MusicSource.Netease, trackId), track.id)
    }
    @Test fun h5ShortAudioIsExplicitlyAPreview() = runBlocking {
        val track = assertNotNull(QishuiParsing.track(JSONObject(trackJson)))
        val result = QishuiProvider({ session }, client { seo().toString() }).resolvePlayback(track, AudioQualityTier.High)
        assertEquals(30000L, assertIs<PlaybackResolution.Preview>(result).durationMs)
    }
    @Test fun authenticatedFullAudioPreservesActualQualityWithoutCdnCookies() = runBlocking {
        val provider = QishuiProvider({ session }, client { request ->
            assertEquals("sessionid=session-test", request.header("Cookie"))
            assertEquals("/luna/media-player", request.url.encodedPath)
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            assertEquals(trackId, JSONObject(buffer.readUtf8()).getString("media_id"))
            JSONObject().put("player_infos", org.json.JSONArray().put(seo(297.24).getJSONObject("track_player").put("media_id", trackId))).toString()
        })
        val result = assertIs<PlaybackResolution.Playable>(provider.resolvePlayback(assertNotNull(QishuiParsing.track(JSONObject(trackJson))), AudioQualityTier.Lossless))
        assertNull(result.requestHeaders["Cookie"]); assertEquals(AudioQualityTier.Standard, result.actualQuality)
        assertEquals(1793000000000L, result.expiresAtEpochMs)
        assertFalse(MusicCapability.Favorites in provider.capabilities)
    }
    @Test fun encryptedAndUntrustedUrlsDoNotResolve() {
        assertNull(QishuiParsing.audio(seo(encrypted = true), 297240L, AudioQualityTier.Standard))
        for (url in listOf("https://douyinvod.com.attacker.invalid/a.m4a", "http://audio.douyinvod.com/a.m4a", "https://user:secret@audio.douyinvod.com/a.m4a"))
            assertFalse(QishuiParsing.isAudioUrl(url))
    }
    @Test fun searchFollowsOpaqueCursorInsteadOfAnAssumedOffset() = runBlocking {
        val api = QishuiApiClient({ session }, client { request ->
            val cursor = request.url.queryParameter("cursor"); assertTrue(cursor == "0" || cursor == "next-token")
            """{"result_groups":[{"data":[{"entity":{"track":$trackJson}}]}],"has_more":${cursor == "0"},"next_cursor":"next-token"}"""
        })
        assertTrue(api.search("测试", 1, 30).hasMore)
        assertFalse(api.search("测试", 2, 30).hasMore)
    }
    @Test fun playlistCursorContinuesBeyondFirstReportedCount() = runBlocking {
        val provider = QishuiProvider({ session }, client { request ->
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8()); val second = body.optString("cursor") == "opaque-next"
            val track = JSONObject(trackJson).put("id", if (second) "7079108541549643813" else trackId)
            """{"playlist":{"id":"p1","title":"歌单","count_tracks":1},"media_resources":[{"track":$track}],"has_more":${!second},"next_cursor":"${if (second) "" else "opaque-next"}"}"""
        })
        val detail = provider.loadAllPlaylistTracks(MusicPlaylistSummary(MusicResourceId(MusicSource.Qishui, "p1"), "歌单"))
        assertEquals(2, detail.tracks.size); assertEquals(false, detail.hasMore)
    }
    @Test fun signedOutLibraryDoesNotSendAnAccountRequest() = runBlocking<Unit> {
        val api = QishuiApiClient({ QishuiSession() }, client { error("No signed-out library request expected") })
        assertFailsWith<java.io.IOException> { api.userPlaylists(1, 30) }
    }
    @Test fun qrCreationCookiesAreIsolatedAndConfirmedSessionIsExtracted() = runBlocking<Unit> {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val creating = chain.request().url.encodedPath.endsWith("get_qrcode/")
            if (creating) assertNull(chain.request().header("Cookie")) else assertEquals("csrf=qr-csrf", chain.request().header("Cookie"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .addHeader("Set-Cookie", if (creating) "csrf=qr-csrf; Path=/; Secure" else "sessionid=confirmed-session; Path=/; Secure")
                .body((if (creating) """{"data":{"token":"qr-token","qrcode":"data:image/png;base64,AA=="}}""" else """{"data":{"status":"confirmed"}}""").toResponseBody()).build()
        }.build()
        val api = QishuiApiClient({ session }, http); val qr = api.createQr()
        assertEquals("confirmed-session", api.pollQr(qr.token).sessionId)
        assertFailsWith<IllegalArgumentException> { api.pollQr("stale-token") }
    }

    @Test fun passportRedirectPreservesFullCookieAcrossPollsAndProfileVerification() = runBlocking {
        var poll = 0
        val requests = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); val path = request.url.encodedPath
            requests += path
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("fixture")
            when {
                path.endsWith("get_qrcode/") -> {
                    assertNull(request.header("Cookie"))
                    response.code(200).addHeader("Set-Cookie", "csrf=initial; Domain=.douyin.com; Secure")
                        .addHeader("Set-Cookie", "sessionid=handshake; Domain=.douyin.com; Secure")
                        .body("""{"data":{"token":"redirect-qr","qrcode":"data:image/png;base64,AA=="}}""".toResponseBody())
                }
                path.endsWith("check_qrconnect/") -> {
                    poll++
                    assertEquals(if (poll == 1) "csrf=initial; sessionid=handshake" else "csrf=rotated; sessionid=handshake", request.header("Cookie"))
                    if (poll == 1) response.code(200).addHeader("Set-Cookie", "csrf=rotated; Domain=.douyin.com; Secure")
                        .body("""{"data":{"status":"scanned"}}""".toResponseBody())
                    else response.code(302).addHeader("Location", "https://example.invalid/must-not-follow")
                        .addHeader("Set-Cookie", "sessionid=confirmed; Domain=.douyin.com; Secure; HttpOnly")
                        .addHeader("Set-Cookie", "sid_tt=extra; Domain=.douyin.com; Secure")
                        .body("".toResponseBody())
                }
                else -> {
                    assertEquals("/luna/pc/me", path)
                    assertEquals("csrf=rotated; sessionid=confirmed; sid_tt=extra", request.header("Cookie"))
                    response.code(200).body("""{"my_info":{"id":"real-profile-id","nickname":"Verified"}}""".toResponseBody())
                }
            }.build()
        }.build()
        val api = QishuiApiClient({ session }, http)
        val qr = api.createQr()
        assertEquals("", api.pollQr(qr.token).sessionId)
        val confirmed = api.pollQr(qr.token)
        assertEquals("confirmed", confirmed.sessionId)
        assertEquals("csrf=rotated; sessionid=confirmed; sid_tt=extra", confirmed.cookie)
        val profile = QishuiApiClient({ session.copy(sessionId = confirmed.sessionId, cookie = confirmed.cookie) }, http).profile()
        assertEquals("real-profile-id", profile?.id)
        assertEquals(4, requests.size)
        api.createQr()
        assertEquals(5, requests.size) // refreshed attempt sends no old QR/account Cookie
    }

    @Test fun redirectsAreNotAcceptedAsSuccessfulNonPassportRequests() = runBlocking<Unit> {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).message("fixture").code(302)
                .addHeader("Location", "https://example.invalid/must-not-follow").body("".toResponseBody()).build()
        }.build()
        assertFailsWith<java.io.IOException> { QishuiApiClient({ session }, http).profile() }
    }
}
