package melox.provider.qqmusic

import java.util.Base64
import kotlinx.coroutines.runBlocking
import melox.lyrics.LyricSource
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QQMusicLyricsSourceTest {
    @Test fun plainAndTimedFallbackKeepQqSourceAndTimingSemantics() = runBlocking {
        val track = MusicTrack(MusicResourceId(MusicSource.QQMusic, "fixture"), "Fixture", emptyList(),
            providerMetadata = ProviderTrackMetadata.QQMusic("fixture"))
        var text = "Plain lyric\nAnother line"
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/lyric/fcgi-bin/fcg_query_lyric_new.fcg", chain.request().url.encodedPath)
            val body = JSONObject().put("lyric", Base64.getEncoder().encodeToString(text.toByteArray())).toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val client = QQMusicApiClient(httpClient = http)
        val plain = client.lyrics(track)
        assertEquals(LyricSource.QQMusic, plain.source)
        assertTrue(plain.isUnsynchronized)
        assertNull(plain.highlightedIndex(9000))
        text = "[00:01.00]Timed lyric"
        val timed = client.lyrics(track)
        assertEquals(LyricSource.QQMusic, timed.source)
        assertEquals(0, timed.highlightedIndex(1000))
        assertTrue(timed.lines.single().syllables.isEmpty())
    }
}
