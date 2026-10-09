package melox.provider.spotify

import com.google.protobuf.ByteString
import com.spotify.Authentication
import com.spotify.connectstate.Connect
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import melox.platform.meloXCacheDir
import xyz.gianlu.librespot.audio.format.AudioQualityPicker
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.metadata.TrackId

/** JVM equivalent of Android's librespot path; OAuth credentials remain in DPAPI. */
class SpotifyLibrespotPlayback {
    private val lock = Mutex()
    private val cacheDir = File(meloXCacheDir(), "spotify")
    private var session: Session? = null
    private var sessionToken: String? = null
    private var generation = 0L
    private val deviceId = UUID.randomUUID().toString().replace("-", "").padEnd(40, '0')

    suspend fun resolveToFile(trackId: String, accessToken: String): File = withContext(Dispatchers.IO) {
        require(trackId.matches(Regex("[A-Za-z0-9]{22}"))) { "Invalid Spotify track ID" }
        require(accessToken.isNotBlank()) { "Spotify session 未登录" }
        lock.withLock {
            cacheDir.mkdirs()
            val current = synchronized(this@SpotifyLibrespotPlayback) {
                if (sessionToken == accessToken) session else null
            } ?: run {
                val before = synchronized(this@SpotifyLibrespotPlayback) { generation }
                val config = Session.Configuration.Builder()
                    .setCacheEnabled(true).setCacheDir(cacheDir)
                    .setStoreCredentials(false).build()
                val created = Session.Builder(config)
                    .setDeviceType(Connect.DeviceType.COMPUTER)
                    .setDeviceName("MeloX").setDeviceId(deviceId)
                    .credentials(Authentication.LoginCredentials.newBuilder()
                        .setTyp(Authentication.AuthenticationType.AUTHENTICATION_SPOTIFY_TOKEN)
                        .setAuthData(ByteString.copyFromUtf8(accessToken)).build())
                    .create()
                synchronized(this@SpotifyLibrespotPlayback) {
                    if (generation != before) {
                        created.close()
                        throw IOException("Spotify session 已退出")
                    }
                    runCatching { session?.close() }
                    session = created
                    sessionToken = accessToken
                }
                created
            }
            val destination = File(cacheDir, "$trackId.audio")
            if (destination.isFile && destination.length() > 0L) return@withLock destination
            val temporary = File.createTempFile("$trackId-", ".part", cacheDir)
            try {
                val loaded = current.contentFeeder().load(TrackId.fromUri("spotify:track:$trackId"),
                    AudioQualityPicker { files -> files.firstOrNull() }, false, null)
                temporary.outputStream().use { output -> loaded.`in`.stream().use { it.copyTo(output) } }
                if (temporary.length() == 0L) throw IOException("Spotify 返回空音频")
                java.nio.file.Files.move(temporary.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                destination
            } finally { temporary.delete() }
        }
    }

    @Synchronized fun disconnect() {
        generation++
        val previous = session
        session = null
        sessionToken = null
        runCatching { previous?.close() }
    }

    companion object { val shared by lazy { SpotifyLibrespotPlayback() } }
}
