package melox.cli

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCliServerTest {
    private fun token(metadata: JSONObject): String {
        val protected = Base64.getDecoder().decode(metadata.getString("token"))
        val plain = if (Platform.isWindows()) Crypt32Util.cryptUnprotectData(protected, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN) else protected
        return Base64.getEncoder().encodeToString(plain)
    }

    @Test fun localEndpointAuthenticatesAndClosesWithoutRunningUnauthorizedCommands() {
        val root = Files.createTempDirectory("melox-cli-test-")
        val calls = AtomicInteger()
        val server = DesktopCliServer.start(root) { request ->
            calls.incrementAndGet()
            JSONObject().put("command", request.getString("command"))
        }
        val endpoint = root.resolve("cli-endpoint.json")
        val metadata = JSONObject(Files.readString(endpoint))
        val valid = token(metadata)
        val port = metadata.getInt("port")
        fun request(token: String): JSONObject = Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().write((JSONObject().put("command", "status").put("token", token).toString() + "\n").toByteArray())
            JSONObject(socket.getInputStream().bufferedReader().readLine())
        }
        try {
            assertFalse(request(Base64.getEncoder().encodeToString(ByteArray(32))).getBoolean("ok"))
            assertEquals(0, calls.get())
            assertTrue(request(valid).getBoolean("ok"))
            assertEquals(1, calls.get())
            assertFails { DesktopCliServer.start(root) { JSONObject() } }
        } finally { server.close() }
        assertFalse(Files.exists(endpoint))
        assertFails { request(valid) }
        DesktopCliServer.start(root) { JSONObject() }.close()
    }

    @Test fun disablingInterruptsAnInFlightCommand() {
        val root = Files.createTempDirectory("melox-cli-cancel-")
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val server = DesktopCliServer.start(root) {
            started.countDown()
            try { awaitCancellation() } finally { finished.countDown() }
        }
        val metadata = JSONObject(Files.readString(root.resolve("cli-endpoint.json")))
        val socket = Socket(InetAddress.getByName("127.0.0.1"), metadata.getInt("port"))
        try {
            socket.getOutputStream().write((JSONObject().put("token", token(metadata)).toString() + "\n").toByteArray())
            assertTrue(started.await(5, TimeUnit.SECONDS))
            server.close()
            assertTrue(finished.await(5, TimeUnit.SECONDS), "CLI worker continued after disable")
        } finally { socket.close(); server.close() }
    }
}
