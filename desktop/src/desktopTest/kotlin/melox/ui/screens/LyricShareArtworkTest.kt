package melox.ui.screens

import com.sun.net.httpserver.HttpServer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import melox.provider.jellyfin.JellyfinSession

class LyricShareArtworkTest {
    @Test fun privateArtworkUsesScopedTokenAndDoesNotFollowRedirects() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val token = AtomicReference<String?>()
        val otherToken = AtomicReference<String?>()
        val redirected = AtomicInteger()
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        server.createContext("/jellyfin/Items/cover/Images/Primary") { exchange ->
            token.set(exchange.requestHeaders.getFirst("X-Emby-Token"))
            exchange.sendResponseHeaders(200, png.size.toLong())
            exchange.responseBody.use { it.write(png) }
        }
        server.createContext("/public") { exchange ->
            otherToken.set(exchange.requestHeaders.getFirst("X-Emby-Token"))
            exchange.sendResponseHeaders(200, png.size.toLong())
            exchange.responseBody.use { it.write(png) }
        }
        server.createContext("/jellyfin/Items/redirect/Images/Primary") { exchange ->
            exchange.responseHeaders.add("Location", "/redirect-target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/redirect-target") { exchange ->
            redirected.incrementAndGet()
            exchange.sendResponseHeaders(200, png.size.toLong())
            exchange.responseBody.use { it.write(png) }
        }
        server.start()
        try {
            val host = "http://127.0.0.1:${server.address.port}"
            val session = JellyfinSession("$host/jellyfin", "fixture-token", "user")
            assertNotNull(loadLyricShareArtwork("$host/jellyfin/Items/cover/Images/Primary", session))
            assertEquals("fixture-token", token.get())
            assertNotNull(loadLyricShareArtwork("$host/public", session))
            assertNull(otherToken.get())
            runCatching { loadLyricShareArtwork("$host/jellyfin/Items/redirect/Images/Primary", session) }
            assertEquals(0, redirected.get())
        } finally { server.stop(0) }
    }
}
