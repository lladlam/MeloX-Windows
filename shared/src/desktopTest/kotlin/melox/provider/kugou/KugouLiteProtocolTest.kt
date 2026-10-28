package melox.provider.kugou

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import melox.music.model.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import kotlin.test.*

/** HTTP fixtures exercise protocol behaviour; they are not evidence of a real login or entitlement. */
class KugouLiteProtocolTest {
    private fun session(platform: KugouPlatform = KugouPlatform.Lite, token: String = "lite-fixture", id: Long = 42) =
        KugouSession(token, id, "", 0, "device-fixture", "mid-fixture", "guid-fixture", "dev-fixture", "", "", platform)

    private fun client(handler: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body(handler(request).toResponseBody()).build()
    }.build()

    @Test fun conceptSearchUsesItsOwnIdentityAndRetainsNativeTrackMetadata() = runBlocking {
        val provider = KugouLiteProvider({ session() }, client { request ->
            assertEquals("/v2/search/song", request.url.encodedPath)
            assertEquals("3116", request.url.queryParameter("appid"))
            assertEquals("201", request.url.queryParameter("clientver"))
            assertEquals("lite-fixture", request.url.queryParameter("token"))
            assertTrue(request.header("Cookie").orEmpty().contains("token=lite-fixture"))
            assertEquals(KugouPlatform.Lite.userAgent, request.header("User-Agent"))
            val params = request.url.queryParameterNames.filterNot { it == "signature" }.associateWith { request.url.queryParameter(it).orEmpty() }
            val signed = params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
            val raw = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA${signed}LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
            val digest = java.security.MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
            assertEquals(digest, request.url.queryParameter("signature"))
            """{"status":1,"data":{"total":1,"lists":[{"FileHash":"abcdef","SongName":"歌曲","SingerName":"甲、乙","AlbumID":"17","AlbumName":"专辑","MixSongID":"88","Duration":213,"Image":"http://imge.kugou.com/{size}/cover.jpg"}]}}"""
        })
        val track = provider.searchSongs("歌曲").items.single()
        assertEquals(MusicResourceId(MusicSource.KugouLite, "ABCDEF"), track.id)
        assertEquals("甲 / 乙", track.artistText)
        assertEquals(213_000L, track.durationMs)
        assertEquals(88L, (track.providerMetadata as ProviderTrackMetadata.Kugou).albumAudioId)
        assertEquals(MusicSource.KugouLite, track.album?.id?.source)
    }

    @Test fun conceptQrUsesConceptAppIdAndDoesNotChangeStandardQr() = runBlocking {
        for (platform in KugouPlatform.entries) {
            val requests = mutableListOf<Request>()
            val client = KugouLoginClient({ session(platform) }, client { request ->
                requests += request
                if (request.url.encodedPath == "/v2/qrcode") """{"status":1,"data":{"qrcode":"fixture-qr"}}"""
                else """{"status":1,"data":{"status":4,"token":"fixture-token","userid":99}}"""
            }, platform)
            val qr = client.createQrSession()
            assertIs<KugouQrLoginState.Authorized>(client.checkQrSession(qr.key))
            assertTrue(requests[0].url.queryParameter("qrcode_txt").orEmpty().contains("appid=${platform.appId}"))
            assertEquals(platform.appId.toString(), requests[1].url.queryParameter("appid"))
            assertEquals(platform.clientVersion.toString(), requests[1].url.queryParameter("clientver"))
        }
    }

    @Test fun standardCredentialsCannotBeSentByConceptProvider() = runBlocking<Unit> {
        var requests = 0
        val provider = KugouLiteProvider({ session(KugouPlatform.Standard) }, client { requests++; "{}" })
        assertFailsWith<IllegalArgumentException> { provider.searchSongs("song") }
        assertEquals(0, requests)
    }

    @Test fun separateSourcesRejectCrossClientCollectionsAndRespectPreview() = runBlocking<Unit> {
        val provider = KugouLiteProvider({ session(token = "", id = 0) }, client { request ->
            when (request.url.encodedPath) {
                "/v2/get_res_privilege/lite" -> """{"status":1,"data":[{"hash":"abcdef","relate_goods":[{"quality":"128","level":2,"privilege":0,"status":1,"info":{"bitrate":128,"extname":"mp3"}}]}]}"""
                "/v5/url" -> """{"status":1,"data":{"is_full_audio":0,"url":["https://example.invalid/preview.mp3"]}}"""
                else -> error("unexpected fixture path ${request.url.encodedPath}")
            }
        })
        val track = MusicTrack(MusicResourceId(MusicSource.KugouLite, "ABCDEF"), "song", emptyList(),
            providerMetadata = ProviderTrackMetadata.Kugou("ABCDEF"))
        assertIs<PlaybackResolution.Preview>(provider.resolvePlayback(track, AudioQualityTier.Standard))
        val standard = track.copy(id = MusicResourceId(MusicSource.Kugou, "ABCDEF"))
        assertFailsWith<IllegalArgumentException> { provider.resolvePlayback(standard, AudioQualityTier.Standard) }
        assertFailsWith<IllegalArgumentException> {
            provider.playlistDetail(MusicPlaylistSummary(MusicResourceId(MusicSource.Kugou, "p"), "p"))
        }
    }

    @Test fun rewardRequestsRecognizeAlreadyClaimedAndRetryOnlyUnfinishedDays() = runBlocking {
        val paths = mutableListOf<String>()
        val provider = KugouLiteProvider({ session() }, client { request ->
            paths += request.url.encodedPath
            assertEquals("3116", request.url.queryParameter("appid"))
            assertEquals("lite-fixture", request.url.queryParameter("token"))
            when (request.url.encodedPath) {
                "/youth/v1/recharge/receive_vip_listen_song" -> {
                    assertEquals("2026-10-28", request.url.queryParameter("receive_day"))
                    assertEquals("application/x-www-form-urlencoded", request.body?.contentType().toString().substringBefore(';'))
                    """{"status":0,"error_code":131001,"error_msg":"already claimed"}"""
                }
                "/youth/v1/listen_song/upgrade_vip_reward" -> """{"status":0,"error_code":20028,"error_msg":"restricted"}"""
                else -> error("unexpected fixture")
            }
        })
        val result = KugouLiteDailyCheckIn.claimRewards(provider, LocalDate.parse("2026-10-28")) {}
        assertTrue(result.claimed); assertFalse(result.upgraded)
        assertEquals(2, paths.size)
        val state = KugouLiteCheckInState(successDay = result.day, attemptDay = result.day, attemptAtMs = 1000)
        paths.clear()
        val retried = KugouLiteDailyCheckIn.claimRewards(provider, LocalDate.parse(result.day), state) {}
        assertTrue(retried.claimed); assertFalse(retried.upgraded)
        assertEquals(listOf("/youth/v1/listen_song/upgrade_vip_reward"), paths)
        assertFalse(KugouLiteDailyCheckIn.shouldRun(state, true, result.day, 2000, false, true))
        assertTrue(KugouLiteDailyCheckIn.shouldRun(state, true, result.day, 601_001, false, true))
        assertFalse(KugouLiteDailyCheckIn.shouldRun(state.copy(upgradedDay = result.day), true, result.day, 601_001, false, true))
        assertTrue(KugouLiteDailyCheckIn.shouldRun(state, true, result.day, 2000, true, false))
        assertFalse(KugouLiteDailyCheckIn.shouldRun(state.copy(autoEnabled = false), true, result.day, 601_001, false, true))
    }

    @Test fun rewardStopsBeforeSecondRequestWhenAccountChanges() = runBlocking {
        var unchanged = true
        val paths = mutableListOf<String>()
        val provider = KugouLiteProvider({ session() }, client { request ->
            paths += request.url.encodedPath
            unchanged = false
            """{"status":1}"""
        })
        assertFailsWith<IllegalStateException> {
            KugouLiteDailyCheckIn.claimRewards(provider, LocalDate.parse("2026-10-28")) { check(unchanged) }
        }
        assertEquals(listOf("/youth/v1/recharge/receive_vip_listen_song"), paths)
    }

    @Test fun vipCacheIsAccountSpecificAndRejectsLateAccountResults() = runBlocking<Unit> {
        var current = session(id = 42)
        var requests = 0
        var swapInFlight = false
        val provider = KugouLiteProvider({ current }, client { _ ->
            requests++
            if (swapInFlight) current = session(token = "third-fixture", id = 44)
            """{"status":1,"data":{"vip_type":${if (requests == 1) 6 else 2},"is_vip":1}}"""
        })
        assertTrue(provider.vipStatus().superVip)
        assertTrue(provider.vipStatus().superVip)
        assertEquals(1, requests)
        current = session(token = "second-fixture", id = 43)
        assertFalse(provider.vipStatus().superVip)
        assertEquals(2, requests)
        swapInFlight = true
        assertFailsWith<IllegalStateException> { provider.vipStatus(force = true) }
    }

    @Test fun accountStoresKeepIdentitiesAndCredentialsIndependent() {
        try {
            val standard = KugouSessionStore.updateLogin("standard-fixture", 21)
            val concept = KugouSessionStore.updateLiteLogin("concept-fixture", 22)
            KugouSessionStore.updateLiteEntitlements(auth = "cached-fixture", vipActive = true)
            assertEquals("standard-fixture", KugouSessionStore.read().token)
            assertEquals("concept-fixture", KugouSessionStore.readLite().token)
            assertNotEquals(standard.guid, concept.guid)
            KugouSessionStore.updateLiteLogin("next-fixture", 23)
            assertEquals("", KugouSessionStore.readLite().auth)
            assertFalse(KugouSessionStore.readLite().vipActive)
            assertFalse(KugouSessionStore.updateLiteEntitlements(expected = concept, auth = "late-fixture", vipActive = true))
            assertFalse(KugouSessionStore.updateLiteDfid("late-dfid", expected = concept))
            assertEquals("", KugouSessionStore.readLite().auth)
            assertFalse(KugouSessionStore.readLite().vipActive)
            KugouSessionStore.clearLiteLogin()
            assertEquals("standard-fixture", KugouSessionStore.read().token)
            assertFalse(KugouSessionStore.readLite().isLoggedIn)
        } finally {
            KugouSessionStore.clearLogin()
            KugouSessionStore.clearLiteLogin()
        }
    }
}
