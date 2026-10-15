package melox.provider.kuwo

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.music.provider.MusicCapability
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.*

class KuwoProviderProtocolTest {
    @Test fun providerActuallyDelegatesNativeSearchPlaybackAndAuthenticatedPlaylists() = runBlocking<Unit> {
        val requests = mutableListOf<Request>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            val body = when (request.url.host) {
                "search.kuwo.cn" -> """{"TOTAL":"1","abslist":[{"MUSICRID":"MUSIC_71","NAME":"Acoustic","ARTIST":"Native","DURATION":"180"}]}"""
                "antiserver.kuwo.cn" -> {
                    assertEquals("MUSIC_71", request.url.queryParameter("rid"))
                    """{"code":200,"url":"https://media.example/kuwo.mp3"}"""
                }
                "nplserver.kuwo.cn" -> {
                    assertEquals("token", request.url.queryParameter("loginSid"))
                    assertEquals("user", request.url.queryParameter("loginUid"))
                    """{"playlist":[{"id":"9","name":"Native list","songnum":1}]}"""
                }
                "mobilist.kuwo.cn" -> """{"total":1,"musiclist":[{"rid":"MUSIC_71","name":"Acoustic","artist":"Native"}]}"""
                else -> error("Unexpected request ${request.url}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("protocol fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val provider = KuwoProvider({ KuwoSession("token", "user") }, http)
        assertTrue(provider.capabilities.containsAll(setOf(MusicCapability.Search, MusicCapability.Playlists, MusicCapability.Lyrics)))
        val song = provider.searchSongs("Acoustic", 1, 20).items.single()
        assertEquals(MusicResourceId(MusicSource.Kuwo, "71"), song.id)
        val playable = assertIs<PlaybackResolution.Playable>(provider.resolvePlayback(song, AudioQualityTier.Standard))
        assertEquals("https://media.example/kuwo.mp3", playable.url)
        val playlist = provider.userPlaylists(1, 20).items.single()
        assertEquals(song.id, provider.playlistDetail(playlist, 1, 20).tracks.single().id)
        assertTrue(requests.all { it.header("User-Agent")?.startsWith("MeloX-Desktop/") == true })
        assertFailsWith<java.io.IOException> { KuwoProvider(httpClient = http).userPlaylists(1, 20) }
    }
}
