package melox.ui.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.awt.ComposePanel
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFrame
import javax.swing.SwingUtilities
import melox.network.MeloXPodcast
import melox.network.NeteaseUniversalSearchClient
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Run under Xvfb: native wheel input drives the production LazyColumn and HTTP fixture. */
class DesktopPodcastPagingInputTest {
    @Test fun delayedPageSurvivesTailRemovalAndContinuesPaging() {
        assumeTrue("native input fixture requires an isolated display", System.getenv("MELOX_NATIVE_PAGING_TEST") == "1")
        check(!GraphicsEnvironment.isHeadless()) { "This test needs a real display or Xvfb" }
        val offsets = Collections.synchronizedList(mutableListOf<Int>())
        val cancelled = Collections.synchronizedList(mutableListOf<Int>())
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val response = if (chain.request().url.encodedPath.endsWith("/djradio/v2/get")) {
                """{"code":200,"data":{"id":998877,"name":"Paging fixture","programCount":61}}"""
            } else {
                val encrypted = (chain.request().body as FormBody).value(0).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
                val payload = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8).split("-36cd479b6b5-")[1])
                val offset = payload.getInt("offset")
                offsets.add(offset)
                if (offset == 30) {
                    Thread.sleep(1500)
                    if (chain.call().isCanceled()) cancelled.add(offset)
                }
                val count = if (offset < 60) 30 else 1
                val programs = (offset until offset + count).joinToString(",") { i ->
                    """{"id":${i + 1},"name":"Episode ${i + 1}","mainSong":{"id":${i + 1},"name":"Song"},"radio":{"id":998877,"name":"Paging fixture"}}"""
                }
                """{"code":200,"count":61,"more":${offset < 60},"programs":[$programs]}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody()).build()
        }.build()
        val client = NeteaseUniversalSearchClient(httpClient = http)
        var window: JFrame? = null
        try {
            SwingUtilities.invokeAndWait {
                val panel = ComposePanel()
                panel.setContent { MaterialTheme { MeloXDesktopPodcastDetail(client, MeloXPodcast(998877, "Paging fixture"), {}) } }
                window = JFrame("Podcast paging input fixture").apply {
                    contentPane.add(panel)
                    setSize(1000, 800)
                    setLocation(0, 0)
                    isVisible = true
                }
            }
            val robot = Robot().apply { autoDelay = 80; mouseMove(500, 600) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
            while (!offsets.contains(60) && System.nanoTime() < deadline) {
                robot.mouseWheel(7)
                Thread.sleep(180)
            }
            assertTrue(offsets.contains(60), "third page did not load after delayed second page: $offsets")
            assertEquals(listOf(0, 30, 60), offsets.toList())
            assertFalse(cancelled.contains(30), "tail removal cancelled the second request")
        } finally {
            SwingUtilities.invokeAndWait { window?.dispose() }
        }
    }
}
