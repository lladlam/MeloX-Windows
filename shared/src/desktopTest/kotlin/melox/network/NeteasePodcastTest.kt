package melox.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeteasePodcastTest {
    private fun client(response: String, paths: MutableList<String> = mutableListOf()) =
        NeteaseUniversalSearchClient(httpClient = OkHttpClient.Builder().addInterceptor { chain ->
            paths.add(chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(response.toResponseBody()).build()
        }.build())

    @Test fun programPagingHonorsServerMoreAndRetainsPlayableIdentity() = runBlocking {
        val paths = mutableListOf<String>()
        val program = """{"id":123,"name":"节目标题","description":"节目简介","coverUrl":"http://img.test/cover","duration":0,"dj":{"nickname":"主播"},"radio":{"id":99,"name":"播客"},"mainSong":{"id":456,"name":"原始音频","duration":7000}}"""
        val result = client("""{"code":200,"more":true,"programs":[$program]}""", paths).podcastPrograms(99)
        assertTrue(result.hasMore)
        val episode = result.values.single()
        assertEquals(123, episode.id)
        assertEquals("节目简介", episode.description)
        assertEquals(456, episode.playbackSong?.id)
        assertEquals("节目标题", episode.playbackSong?.name)
        assertEquals("主播", episode.playbackSong?.artists)
        assertEquals("播客", episode.playbackSong?.album)
        assertEquals("https://img.test/cover", episode.playbackSong?.artworkUrl)
        assertEquals(7000, episode.durationMs)
        assertEquals(listOf("/eapi/dj/program/byradio"), paths)
        assertFalse(client("""{"code":200,"more":false,"count":100,"programs":[$program]}""").podcastPrograms(99).hasMore)
        assertTrue(client("""{"code":200,"count":2,"programs":[$program]}""").podcastPrograms(99).hasMore)
    }

    @Test fun subscribedPagingHonorsServerHasMore() = runBlocking {
        val radio = """{"id":99,"name":"播客"}"""
        assertTrue(client("""{"code":200,"hasMore":true,"djRadios":[$radio]}""").subscribedPodcasts(0, 1).hasMore)
        assertFalse(client("""{"code":200,"hasMore":false,"count":100,"djRadios":[$radio]}""").subscribedPodcasts(0, 1).hasMore)
        assertTrue(client("""{"code":200,"count":2,"djRadios":[$radio]}""").subscribedPodcasts(0, 1).hasMore)
    }

    @Test fun detailAcceptsBothResponseShapes() = runBlocking {
        val paths = mutableListOf<String>()
        val program = """{"id":123,"name":"节目","radio":{"id":99,"name":"播客"}}"""
        assertEquals(123, client("""{"code":200,"program":$program}""", paths).podcastProgramDetail(123)?.id)
        assertEquals(99, client("""{"code":200,"data":$program}""").podcastProgramDetail(123)?.radioId)
        assertEquals(listOf("/eapi/dj/program/detail"), paths)
    }
}
