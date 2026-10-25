package melox.provider.bilibili

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.music.provider.MusicCapability
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.*

class BilibiliProviderProtocolTest {
    private fun client(handler: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("protocol fixture")
            .body(handler(chain.request()).toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val nav = """{"code":0,"data":{"mid":9,"uname":"protocol user","wbi_img":{"img_url":"https://key.example/abcdefghijklmnopqrstuvwxyz012345.png","sub_url":"https://key.example/678901abcdefghijklmnopqrstuvwxyz.png"}}}"""
    private val session = BilibiliSession("SESSDATA=protocol-only", "9")

    @Test fun searchSignsWbiAndPreservesNativeVideoPageIdentity() = runBlocking<Unit> {
        val requests = mutableListOf<Request>()
        val http = client { request ->
            requests += request
            when (request.url.encodedPath) {
                "/x/web-interface/nav" -> nav
                "/x/web-interface/wbi/search/type" -> {
                    assertEquals("a & b", request.url.queryParameter("keyword"))
                    assertEquals(32, request.url.queryParameter("w_rid")?.length)
                    """{"code":0,"data":{"numResults":1,"result":[{"bvid":"BVtest","aid":11,"title":"<em>A</em> & B","author":"UP","pic":"//images.example/a.png","duration":"2:03"}]}}"""
                }
                "/x/web-interface/view" -> """{"code":0,"data":{"cid":45}}"""
                else -> error("Unexpected endpoint ${request.url}")
            }
        }
        val provider = BilibiliProvider({ session }, http, apiCache = null)
        assertTrue(MusicCapability.Search in provider.capabilities)
        val track = provider.searchSongs(" A & B ", 1, 10).items.single()
        assertEquals(MusicResourceId(MusicSource.Bilibili, "BVtest:45"), track.id)
        assertEquals(ProviderTrackMetadata.Bilibili("BVtest", 45, 11, 1), track.providerMetadata)
        assertEquals("A & B", track.title)
        assertEquals(123_000L, track.durationMs)
        assertEquals("https://images.example/a.png", track.artworkUrl)
        assertTrue(requests.all { it.header("Cookie") == session.cookie && it.header("User-Agent") == BilibiliProvider.UserAgent })
    }

    @Test fun dashRetries403WithNewKeyAndUsesReplacementOnlyForPhysicalPlayback() = runBlocking<Unit> {
        var navCalls = 0
        var playbackCalls = 0
        val http = client { request ->
            when (request.url.encodedPath) {
                "/x/web-interface/nav" -> { navCalls++; nav }
                "/x/player/wbi/playurl" -> {
                    assertEquals("BVreplacement", request.url.queryParameter("bvid"))
                    assertEquals("66", request.url.queryParameter("cid"))
                    playbackCalls++
                    if (playbackCalls == 1) """{"code":-403,"message":"expired key"}"""
                    else """{"code":0,"data":{"dash":{"audio":[{"id":30251,"base_url":"https://media.example/unsafe.flac"},{"id":30280,"baseUrl":"https://media.example/audio?deadline=1999999999","bandwidth":192000,"mimeType":"audio/mp4"}]}}}"""
                }
                else -> error("Unexpected endpoint ${request.url}")
            }
        }
        val provider = BilibiliProvider({ session }, http, associationProvider = { bvid, cid ->
            BilibiliPlaybackAssociation(bvid, cid, "BVreplacement", 66, "Replacement", 123_000, 123_000)
        }, apiCache = null)
        val track = MusicTrack(MusicResourceId(MusicSource.Bilibili, "BVoriginal:45"), "Original", emptyList())
        val playable = assertIs<PlaybackResolution.Playable>(provider.resolvePlayback(track, AudioQualityTier.Master))
        assertEquals(2, navCalls); assertEquals(2, playbackCalls)
        assertEquals("BVoriginal:45", track.id.value)
        assertEquals(AudioQualityTier.Lossless, playable.actualQuality)
        assertEquals(session.cookie, playable.requestHeaders["Cookie"])
        assertEquals("https://www.bilibili.com/video/BVreplacement", playable.requestHeaders["Referer"])
        assertEquals(1_999_999_969_000L, playable.expiresAtEpochMs)
    }

    @Test fun favoriteFolderExpandsMultiPageVideoAndDoesNotTurnApiErrorIntoEmptyData() = runBlocking<Unit> {
        val http = client { request -> when (request.url.encodedPath) {
            "/x/v3/fav/folder/created/list-all" -> """{"code":0,"data":{"list":[{"id":7,"title":"Folder","cover":"https://images.example/cover","media_count":1}]}}"""
            "/x/v3/fav/folder/collected/list" -> """{"code":0,"data":{"list":[]}}"""
            "/x/v3/fav/resource/list" -> {
                assertEquals("2", request.url.queryParameter("pn"))
                assertEquals("20", request.url.queryParameter("ps"))
                """{"code":0,"data":{"info":{"title":"Folder","media_count":1},"medias":[{"bvid":"BVtest","title":"Video"}]}}"""
            }
            "/x/web-interface/view" -> """{"code":0,"data":{"aid":11,"owner":{"name":"UP"},"pages":[{"cid":41,"page":1,"part":"one","duration":5},{"cid":42,"page":2,"part":"two","duration":6}]}}"""
            else -> error("Unexpected endpoint ${request.url}")
        } }
        val provider = BilibiliProvider({ session }, http, apiCache = null)
        val folder = provider.userPlaylists(1, 20).items.single()
        val detail = provider.playlistDetail(folder, 2, 100)
        assertEquals(listOf("BVtest:41", "BVtest:42"), detail.tracks.map { it.id.value })
        assertEquals(listOf(5_000L, 6_000L), detail.tracks.map { it.durationMs })
        assertIs<PlaybackResolution.LoginRequired>(BilibiliProvider(httpClient = http).resolvePlayback(detail.tracks.first(), AudioQualityTier.Standard))
        val failing = BilibiliProvider({ session }, client { """{"code":-101,"message":"login expired"}""" }, apiCache = null)
        assertFailsWith<java.io.IOException> { failing.userPlaylists(1, 20) }
    }

    @Test fun collectedFoldersAndSeasonsPreserveDistinctIdentitiesAndPageTheLibrary() = runBlocking<Unit> {
        val pages = mutableListOf<String?>()
        val http = client { request -> when (request.url.encodedPath) {
            "/x/v3/fav/folder/created/list-all" -> """{"code":0,"data":{"list":[{"id":7,"title":"Created","cover":"https://images.example/7"}]}}"""
            "/x/v3/fav/folder/collected/list" -> {
                pages += request.url.queryParameter("pn")
                assertEquals("9", request.url.queryParameter("up_mid"))
                val items = org.json.JSONArray()
                if (request.url.queryParameter("pn") == "1") {
                    repeat(40) { items.put(org.json.JSONObject().put("id", 100 + it).put("title", "Collected $it").put("cover", "https://images.example/$it")) }
                } else {
                    items.put(org.json.JSONObject("""{"id":7,"title":"Duplicate folder","cover":"https://images.example/7"}"""))
                    items.put(org.json.JSONObject("""{"id":7,"type":21,"mid":99,"title":"Season"}"""))
                }
                org.json.JSONObject().put("code", 0).put("data", org.json.JSONObject().put("list", items)).toString()
            }
            else -> error("Unexpected endpoint ${request.url}")
        } }
        val provider = BilibiliProvider({ session }, http, apiCache = null)
        val first = provider.userPlaylists(1, 40)
        assertEquals(42L, first.total); assertTrue(first.hasMore)
        val last = provider.userPlaylists(2, 40)
        assertEquals(listOf("139", "season:99:7"), last.items.map { it.id.value })
        assertFalse(MusicCapability.PlaylistWrite in provider.capabilities); assertFalse(last.hasMore)
        assertEquals(listOf<String?>("1", "2", "1", "2"), pages)
    }

    @Test fun seasonUsesItsOwnEndpointAndPaginationWithNativeVideoIds() = runBlocking<Unit> {
        val http = client { request ->
            assertEquals("/x/polymer/web-space/seasons_archives_list", request.url.encodedPath)
            assertEquals("99", request.url.queryParameter("mid")); assertEquals("7", request.url.queryParameter("season_id"))
            assertEquals("2", request.url.queryParameter("page_num")); assertEquals("20", request.url.queryParameter("page_size"))
            """{"code":0,"data":{"meta":{"name":"Native season","cover":"//images.example/season"},"page":{"total":45},"archives":[{"bvid":"BVseason","cid":72,"aid":12,"title":"Track","duration":180}]}}"""
        }
        val provider = BilibiliProvider({ session }, http, apiCache = null)
        val detail = provider.playlistDetail(MusicPlaylistSummary(MusicResourceId(MusicSource.Bilibili, "season:99:7"), "Season"), 2, 100)
        assertEquals("Native season", detail.summary.title); assertEquals(45L, detail.total); assertEquals(true, detail.hasMore)
        assertEquals("BVseason:72", detail.tracks.single().id.value); assertEquals(180000L, detail.tracks.single().durationMs)
    }

    @Test fun aDeletedVideoDoesNotDiscardOtherFavoritesOrMorePages() = runBlocking<Unit> {
        val http = client { request -> when (request.url.encodedPath) {
            "/x/v3/fav/resource/list" -> """{"code":0,"data":{"info":{"media_count":30},"has_more":true,"medias":[{"bvid":"BVgone","title":"Private"},{"bvid":"BVgood","ugc":{"first_cid":73},"title":"Good","duration":200},{"type":12,"bvid":"BVnotVideo"}]}}"""
            "/x/web-interface/view" -> """{"code":-404,"message":"deleted"}"""
            else -> error("Unexpected endpoint ${request.url}")
        } }
        val detail = BilibiliProvider({ session }, http, apiCache = null).playlistDetail(
            MusicPlaylistSummary(MusicResourceId(MusicSource.Bilibili, "7"), "Folder"), 1, 100)
        assertEquals(listOf("BVgood:73"), detail.tracks.map { it.id.value }); assertEquals(true, detail.hasMore)
    }

    @Test fun collectedEndpointFailureStillShowsCreatedFoldersButDetailFailureIsExplicit() = runBlocking<Unit> {
        val http = client { request -> when (request.url.encodedPath) {
            "/x/v3/fav/folder/created/list-all" -> """{"code":0,"data":{"list":[{"id":7,"title":"Created","cover":"https://images.example/7"}]}}"""
            else -> """{"code":-403,"message":"forbidden"}"""
        } }
        val provider = BilibiliProvider({ session }, http, apiCache = null)
        val folder = provider.userPlaylists(1, 20).items.single()
        assertFailsWith<java.io.IOException> { provider.playlistDetail(folder, 1, 20) }
        assertFailsWith<IllegalArgumentException> { provider.playlistDetail(folder.copy(id = MusicResourceId(MusicSource.Bilibili, "season:0:7")), 1, 20) }
    }
}
