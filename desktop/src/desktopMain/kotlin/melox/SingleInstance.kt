package melox

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.win32.StdCallLibrary
import java.awt.EventQueue
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

/** Holds the user data directory lock until shutdown and delivers activation on the AWT thread. */
internal class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val listener: ServerSocket,
    private val token: String,
) : AutoCloseable {
    private var callback: (() -> Unit)? = null
    private var pending = false

    @Synchronized fun onActivate(action: () -> Unit) {
        callback = action
        if (pending) { pending = false; EventQueue.invokeLater(action) }
    }

    @Synchronized private fun activate() {
        val action = callback
        if (action == null) pending = true else EventQueue.invokeLater(action)
    }

    private fun listen() {
        thread(name = "MeloX-single-instance", isDaemon = true) {
            while (!listener.isClosed) {
                try {
                    listener.accept().use { socket ->
                        socket.soTimeout = 2000
                        val input = socket.getInputStream()
                        // Bound input even if a local client never sends a newline.
                        val supplied = ByteArray(token.length + 1)
                        var length = 0
                        while (length < supplied.size) {
                            val byte = input.read()
                            if (byte < 0 || byte == 10) break
                            supplied[length++] = byte.toByte()
                        }
                        if (length == token.length && java.security.MessageDigest.isEqual(supplied.copyOf(length), token.toByteArray())) {
                            activate()
                            socket.getOutputStream().write(1)
                        }
                    }
                } catch (_: java.io.IOException) { /* retry unless the listener was closed */ }
            }
        }
    }

    override fun close() {
        listener.close()
        lock.release()
        channel.close()
    }

    private interface ForegroundPermission : StdCallLibrary {
        fun AllowSetForegroundWindow(processId: Int): Boolean
    }

    companion object {
        /** Returns null after activating the primary; never starts a second instance on IPC failure. */
        fun acquire(root: Path): SingleInstance? {
            Files.createDirectories(root)
            val channel = FileChannel.open(root.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val endpoint = root.resolve("instance.endpoint")
            val deadline = System.nanoTime() + 15_000_000_000L
            try {
                while (true) {
                    val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                    if (lock != null) {
                        var listener: ServerSocket? = null
                        try {
                            listener = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)) }
                            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
                            val temporary = Files.createTempFile(root, ".instance-", ".tmp")
                            try {
                                if (!Platform.isWindows()) Files.setPosixFilePermissions(temporary, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
                                Files.writeString(temporary, "${listener.localPort} ${ProcessHandle.current().pid()} $token")
                                try { Files.move(temporary, endpoint, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, endpoint, StandardCopyOption.REPLACE_EXISTING) }
                            } finally { Files.deleteIfExists(temporary) }
                            return SingleInstance(channel, lock, listener, token).also { it.listen() }
                        } catch (e: Exception) {
                            listener?.close()
                            lock.release()
                            throw e
                        }
                    }
                    if (notifyPrimary(endpoint)) { channel.close(); return null }
                    if (System.nanoTime() >= deadline) error("已有 MeloX 实例，但无法唤醒其窗口")
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                channel.close()
                throw e
            }
        }

        private fun notifyPrimary(endpoint: Path): Boolean = try {
            val fields = Files.readString(endpoint).trim().split(' ')
            require(fields.size == 3)
            val port = fields[0].toInt()
            val pid = fields[1].toInt()
            val token = fields[2]
            require(port in 1..65535 && pid > 0 && token.length == 43)
            if (Platform.isWindows()) runCatching {
                Native.load("user32", ForegroundPermission::class.java).AllowSetForegroundWindow(pid)
            }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 500)
                socket.soTimeout = 1000
                socket.getOutputStream().write("$token\n".toByteArray())
                socket.getInputStream().read() == 1
            }
        } catch (_: Exception) { false }
    }
}
