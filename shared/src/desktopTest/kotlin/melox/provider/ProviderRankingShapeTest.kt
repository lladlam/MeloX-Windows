package melox.provider

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.provider.kugou.*
import melox.provider.qqmusic.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.*

class ProviderRankingShapeTest {
    @Test fun kmrV2JoinsNestedAudioIdentityAndMillisecondDurationToItsParentSong() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"status":1,"error_code":0,"total":100,"data":{"total":100,"songlist":[{
                  "songname":"真实结构曲目","author_name":"歌手","album_audio_id":947743300,"album_id":203333898,
                  "audio_info":{"hash_128":"ABC123","duration_128":149000},
                  "album_info":{"album_name":"专辑","sizable_cover":"http://imge.kugou.com/{size}/cover.jpg"},
                  "deprecated":{"hash":"ABC123","duration":149000}
                }]}}
            """.trimIndent()))
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
            }.build()
            val session = KugouSession("", 0L, "", 0, "-", "test-mid", "test-guid", "test-dev", "test-mac", "test-webgl")
            val result = KugouRankingClient({ session }, http).tracks(
                MusicRankingSummary(MusicResourceId(MusicSource.Kugou, "49223"), "90后热歌榜"))
            val track = result.items.single()
            assertEquals("ABC123", track.id.value)
            assertEquals("真实结构曲目", track.title)
            assertEquals(149_000L, track.durationMs)
            assertEquals("专辑", track.album?.name)
            assertEquals("https://imge.kugou.com/400/cover.jpg", track.artworkUrl)
            assertEquals(947743300L, (track.providerMetadata as ProviderTrackMetadata.Kugou).albumAudioId)
            assertEquals(100L, result.total)
        } finally { server.shutdown() }
    }

    @Test fun videoChartDoesNotConsumeTheSongRankingLimit() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody("""{"req_0":{"code":0,"data":{}}}""")) }
            server.enqueue(MockResponse().setBody("""{"req_0":{"code":0,"data":{"group":[{"toplist":[
                {"topId":201,"title":"MV榜"},{"topId":62,"title":"飙升榜"},{"topId":26,"title":"热歌榜"}
            ]}]}}}"""))
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(server.url("/musicu")).build())
            }.build()
            val feed = QQMusicApiClient({ QQMusicSession("", "", "") }, http).homeFeed(rankingLimit = 2)
            assertEquals(listOf("62", "26"), feed.rankings.map { it.id.value })
        } finally { server.shutdown() }
    }
}
