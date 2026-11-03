package melox.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NeteasePlaylistOperationsTest {
    @Test fun playlistActionsUseDistinctAuthenticatedWeapiRoutes() = runBlocking {
        val paths = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            assertTrue(request.header("Cookie").orEmpty().contains("MUSIC_U="))
            val form = request.body as FormBody
            val values = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertTrue(values["params"].orEmpty().isNotBlank())
            assertTrue(values["encSecKey"].orEmpty().isNotBlank())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":200}""".toResponseBody()).build()
        }.build()
        val client = NeteaseMusicOperationsClient({ "MUSIC_U=fixture" }, http)
        client.setPlaylistSubscribed(123, true)
        client.setPlaylistSubscribed(123, false)
        client.deletePlaylist(123)
        assertEquals(listOf("/weapi/playlist/subscribe", "/weapi/playlist/unsubscribe", "/weapi/playlist/delete"), paths)
    }

    @Test fun serverFailureIsNotReportedAsSuccess() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":501,"message":"fixture failure"}""".toResponseBody()).build()
        }.build()
        val failure = assertFailsWith<IOException> {
            NeteaseMusicOperationsClient({ "MUSIC_U=fixture" }, http).setPlaylistSubscribed(123, false)
        }
        assertEquals("fixture failure", failure.message)
    }
}
