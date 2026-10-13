package melox.provider.lxuser

import melox.music.model.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ChkszProtocolTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
        val url = chain.request().url
        chain.proceed(chain.request().newBuilder().url(server.url(url.encodedPath + "?" + url.encodedQuery)).build())
    }.build()
    @BeforeTest fun start() { server.start() }
    @AfterTest fun stop() { server.shutdown(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    private fun request() = assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun blankKeyMakesNoRequestAndNeteaseUsesPinnedEndpointLevelsAndIdentity() {
        assertNull(ChkszApiClient({ " " }, http).resolveNetease(139774, AudioQualityTier.Standard))
        assertEquals(0, server.requestCount)
        val client = ChkszApiClient({ "fixture-api-key" }, http)
        for ((quality, level) in AudioQualityTier.entries.zip(listOf("standard", "exhigh", "lossless", "hires", "jyeffect", "jymaster"))) {
            server.enqueue(response("""{"data":[{"play_url":"https://audio.invalid/full.flac"}]}"""))
            assertEquals("https://audio.invalid/full.flac", client.resolveNetease(139774, quality)?.url)
            val request = request()
            assertEquals("/api/163_music", request.requestUrl!!.encodedPath)
            assertEquals("139774", request.requestUrl!!.queryParameter("id"))
            assertEquals(level, request.requestUrl!!.queryParameter("level"))
            assertEquals("fixture-api-key", request.requestUrl!!.queryParameter("apikey"))
            assertEquals("MeloX/0.4.5", request.getHeader("User-Agent"))
        }
    }

    @Test fun qqAndKugouUseNativeMetadataAndPinnedSizeMapping() {
        val client = ChkszApiClient({ "fixture-api-key" }, http)
        val qq = MusicTrack(MusicResourceId(MusicSource.QQMusic, "ui-id"), "Title", emptyList(), providerMetadata = ProviderTrackMetadata.QQMusic("native + & mid"))
        val kg = qq.copy(id = MusicResourceId(MusicSource.Kugou, "ui-id"), providerMetadata = ProviderTrackMetadata.Kugou("native-hash"))
        data class RequestCase(val track: MusicTrack, val endpoint: String, val parameter: String, val nativeId: String)
        for ((track, endpoint, parameter, nativeId) in listOf(
            RequestCase(qq, "qq_music", "mid", "native + & mid"), RequestCase(kg, "kugou_music", "id", "native-hash"),
        )) {
            server.enqueue(response("""{"data":{"audioUrl":"https://audio.invalid/full.flac"}}"""))
            assertNotNull(client.resolveTrack(track, AudioQualityTier.Lossless))
            val request = request()
            assertEquals("/api/$endpoint", request.requestUrl!!.encodedPath)
            assertEquals(nativeId, request.requestUrl!!.queryParameter(parameter))
            assertEquals("flac", request.requestUrl!!.queryParameter("size"))
        }
        assertNull(client.resolveTrack(qq.copy(providerMetadata = ProviderTrackMetadata.Empty), AudioQualityTier.Standard))
        assertEquals(2, server.requestCount)
    }

    @Test fun failedMalformedAndNonHttpResponsesRemainUnresolved() {
        val client = ChkszApiClient({ "fixture-api-key" }, http)
        for (response in listOf(MockResponse().setResponseCode(403), response("invalid json"), response("""{"url":"file:///not-a-stream","data":null}"""))) {
            server.enqueue(response)
            assertNull(client.resolveNetease(139774, AudioQualityTier.Standard))
        }
    }
}
