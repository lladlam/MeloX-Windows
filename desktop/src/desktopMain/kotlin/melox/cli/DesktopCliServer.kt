package melox.cli

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/** Local-only CLI transport. Windows publishes its random capability encrypted with current-user DPAPI. */
internal class DesktopCliServer private constructor(
    private val endpoint: Path,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val listener: ServerSocket,
    private val token: ByteArray,
    private val execute: suspend (JSONObject) -> JSONObject,
) : AutoCloseable {
    private val active = AtomicBoolean(true)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(16),
        { runnable -> Thread(runnable, "MeloX-CLI-client").apply { isDaemon = true } })

    private fun startAccepting() {
        thread(name = "MeloX-CLI-accept", isDaemon = true) {
            while (active.get()) {
                val client = try { listener.accept() } catch (_: Exception) { break }
                if (!client.inetAddress.isLoopbackAddress || !active.get()) { client.close(); continue }
                clients.add(client)
                try { workers.execute { handle(client) } }
                catch (_: RejectedExecutionException) { clients.remove(client); client.close() }
            }
        }
    }

    private fun handle(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = 3_000
                val input = socket.getInputStream()
                val bytes = java.io.ByteArrayOutputStream()
                while (true) {
                    val value = input.read()
                    require(value >= 0) { "CLI 请求未完整发送" }
                    if (value == '\n'.code) break
                    require(bytes.size() < 16_384) { "CLI 请求过大" }
                    bytes.write(value)
                }
                val reply = try {
                    val request = JSONObject(bytes.toString(Charsets.UTF_8.name()))
                    val supplied = runCatching { Base64.getDecoder().decode(request.optString("token")) }.getOrDefault(ByteArray(0))
                    check(active.get() && MessageDigest.isEqual(token, supplied)) { "CLI 未授权或已关闭" }
                    request.remove("token")
                    runBlocking { withTimeout(8_000) { execute(request) } }.put("ok", true)
                } catch (error: Exception) {
                    JSONObject().put("ok", false).put("error", error.message ?: error.javaClass.simpleName)
                }
                socket.getOutputStream().write((reply.toString() + "\n").toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // A client may close while its response is being sent.
        } finally { clients.remove(client) }
    }

    override fun close() {
        if (!active.compareAndSet(true, false)) return
        runCatching { listener.close() }
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        runCatching { Files.deleteIfExists(endpoint) }
        token.fill(0)
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun start(root: Path, execute: suspend (JSONObject) -> JSONObject): DesktopCliServer {
            Files.createDirectories(root)
            val channel = FileChannel.open(root.resolve("cli.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            var lock: FileLock? = null
            var listener: ServerSocket? = null
            try {
                lock = channel.tryLock() ?: error("已有 MeloX 实例启用 CLI 模式")
                listener = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)) }
                val token = ByteArray(32).also(SecureRandom()::nextBytes)
                val protected = if (Platform.isWindows()) Crypt32Util.cryptProtectData(token, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN) else token
                val endpoint = root.resolve("cli-endpoint.json")
                val payload = JSONObject().put("version", 1).put("port", listener.localPort)
                    .put("protection", if (Platform.isWindows()) "dpapi" else "owner-only")
                    .put("token", Base64.getEncoder().encodeToString(protected)).toString().toByteArray(Charsets.UTF_8)
                val temporary = Files.createTempFile(root, ".melox-cli-", ".tmp")
                try {
                    if (!Platform.isWindows()) Files.setPosixFilePermissions(temporary,
                        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
                    Files.write(temporary, payload, StandardOpenOption.TRUNCATE_EXISTING)
                    try { Files.move(temporary, endpoint, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                    catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, endpoint, StandardCopyOption.REPLACE_EXISTING) }
                } finally { Files.deleteIfExists(temporary) }
                return DesktopCliServer(endpoint, channel, lock, listener, token, execute).also { it.startAccepting() }
            } catch (error: Exception) {
                runCatching { listener?.close() }
                runCatching { lock?.release() }
                runCatching { channel.close() }
                throw error
            }
        }
    }
}
