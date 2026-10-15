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
}
