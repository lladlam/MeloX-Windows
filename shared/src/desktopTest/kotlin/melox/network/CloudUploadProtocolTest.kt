package melox.network

import kotlinx.coroutines.*
import melox.player.FfmpegLocator
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONObject
import java.io.File
import java.net.Proxy
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.*
import kotlin.test.*

/** Real files/native metadata/EAPI crypto/HTTP byte upload with a local protocol fixture.
 * This proves client behavior, not successful authenticated mutation of a real cloud account.
 */
class CloudUploadProtocolTest {
    private val root = Files.createTempDirectory("melox-cloud-protocol").toFile()
    private val server = MockWebServer()
    private val paths = CopyOnWriteArrayList<String>()
    private val payloads = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private val progress = CopyOnWriteArrayList<MeloXCloudUploadProgress>()
    private val reachedUpload = CompletableDeferred<Unit>()
    private var needUpload = true
    private var rejectUpload = false
    private var rejectEapiInfo = false
    private var blockUpload = false
    private var receivedPcmFile: ByteArray? = null
    private var nosCookie: String? = null
    private var nosToken: String? = null
    private var nosMd5: String? = null
    private var published = false
    private val originalTemps = temporaryFiles()

    @BeforeTest fun start() {
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.pathSegments?.joinToString("/", prefix = "/").orEmpty()
                paths += path
                if (path.startsWith("/eapi/")) {
                    val decoded = decodeEapi(request.body.readUtf8())
                    payloads += decoded
                }
                return when (path) {
                    "/eapi/cloud/upload/check" -> json("""{"code":200,"songId":"123","needUpload":$needUpload}""")
                    "/eapi/nos/token/alloc" -> json("""{"code":200,"result":{"bucket":"jd-musicrep-private","objectKey":"audio/folder/test +#.wav","token":"synthetic-nos-token","resourceId":"987"}}""")
                    "/lbs" -> json("""{"upload":["http://nos-fixture.invalid"]}""")
                    "/jd-musicrep-private/audio/folder/test +#.wav" -> {
                        receivedPcmFile = request.body.readByteArray()
                        nosCookie = request.getHeader("Cookie"); nosToken = request.getHeader("x-nos-token"); nosMd5 = request.getHeader("Content-MD5")
                        reachedUpload.complete(Unit)
                        when {
                            blockUpload -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                            rejectUpload -> json("""{"error":"upload failed"}""").setResponseCode(503)
                            else -> json("{}")
                        }
                    }
                    "/eapi/upload/cloud/info/v2" -> if (rejectEapiInfo) json("""{"code":400,"message":"try WEAPI"}""") else json("""{"code":200,"songId":"456"}""")
                    "/weapi/upload/cloud/info/v2" -> {
                        val form = form(request.body.readUtf8())
                        if (form["params"].isNullOrBlank() || form["encSecKey"].isNullOrBlank()) json("""{"code":400}""")
                        else json("""{"code":200,"songId":"456"}""")
                    }
                    "/eapi/cloud/pub/v2" -> { published = true; json("""{"code":200}""") }
                    "/eapi/v1/cloud/get" -> if (published) json("""{"code":200,"count":1,"data":[{"songId":456,"simpleSong":{"id":456,"name":"generated cloud","ar":[{"name":"generated artist"}],"al":{"name":"generated album"}}}]}""")
                        else json("""{"code":200,"count":0,"data":[]}""")
                    else -> json("""{"code":404,"message":"unexpected test route"}""").setResponseCode(404)
                }
            }
        }
    }
    @AfterTest fun cleanup() { server.shutdown(); root.deleteRecursively(); assertEquals(originalTemps, temporaryFiles(), "Upload snapshot must be removed on every path") }
    private fun temporaryFiles() = File(melox.platform.meloXCacheDir(), "cloud-upload").listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("melox-cloud-") }.map { it.name }.toSet()
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun client(cookie: String = "MUSIC_U=synthetic-local-test; __csrf=synthetic-csrf") = NeteaseUniversalSearchClient({ cookie },
        OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
            val actual = chain.request()
            chain.proceed(actual.newBuilder().url(server.url(actual.url.encodedPath + (actual.url.encodedQuery?.let { "?$it" } ?: ""))).build())
        }.build())
    private fun wave(): File {
        val frames = 11025
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(frames * 4 + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16).put("data".toByteArray()).putInt(frames * 4)
        repeat(frames) { index -> val value = (6000 * sin(2 * PI * 1000 * index / 44100)).roundToInt().toShort(); bytes.putShort(value); bytes.putShort(value) }
        return File(root, "合法 + 云盘.wav").apply { writeBytes(bytes.array()) }
    }
    private fun form(body: String) = body.split('&').associate { entry ->
        URLDecoder.decode(entry.substringBefore('='), "UTF-8") to URLDecoder.decode(entry.substringAfter('='), "UTF-8")
    }
    private fun decodeEapi(body: String): Pair<String, JSONObject> {
        val encoded = form(body).getValue("params")
        val encrypted = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val clear = Cipher.getInstance("AES/ECB/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES")); String(doFinal(encrypted), Charsets.UTF_8)
        }.split("-36cd479b6b5-")
        assertEquals(3, clear.size)
        assertEquals(md5("nobody${clear[0]}use${clear[1]}md5forencrypt".toByteArray()), clear[2])
        return clear[0] to JSONObject(clear[1])
    }
    private fun md5(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun encryptedProtocolUploadsExactRealBytesPublishesAndRefreshesOnlyAfterSuccess() = runBlocking<Unit> {
        val file = wave(); val client = client()
        assertEquals(456L, withTimeout(15000) { client.uploadCloudSong(file, progress::add) })
        assertContentEquals(file.readBytes(), receivedPcmFile)
        assertNull(nosCookie, "Account Cookie must not be sent to NOS")
        assertEquals("synthetic-nos-token", nosToken)
        assertEquals(md5(file.readBytes()), nosMd5)
        val check = payloads.first { it.first == "/api/cloud/upload/check" }.second
        assertEquals(file.length(), check.getLong("length")); assertEquals(md5(file.readBytes()), check.getString("md5"))
        assertTrue(check.getInt("bitrate") in 1400000..1430000, "Native WAV bitrate must not be a fixed upload value")
        val info = payloads.first { it.first == "/api/upload/cloud/info/v2" }.second
        assertEquals(file.name, info.getString("filename")); assertEquals("合法+云盘", info.getString("song"))
        assertEquals(456L, client.cloudSongs().values.single().id)
        assertTrue(paths.indexOf("/eapi/cloud/pub/v2") < paths.indexOf("/eapi/v1/cloud/get"))
        assertTrue(progress.any { it.stage == "上传音频" && it.uploadedBytes == file.length() })
        assertEquals("上传完成", progress.last().stage)
        println("Cloud protocol: real native WAV metadata, exact ${file.length()} upload bytes/MD5, encrypted EAPI, NOS token without Cookie, publish then cloud-list refresh")
    }
    @Test fun nativeTagsAndUnicodeFilenameSurviveCloudRegistration() = runBlocking<Unit> {
        val input = wave(); val file = File(root, "标题 + #.flac")
        val process = ProcessBuilder(FfmpegLocator.command() + listOf("-nostdin", "-y", "-i", input.path, "-metadata", "title=真实 = 标题 #", "-metadata", "artist=歌手 + &", "-metadata", "album=专辑 ;", file.path))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue())
        client().uploadCloudSong(file)
        val info = payloads.first { it.first == "/api/upload/cloud/info/v2" }.second
        assertEquals("真实 = 标题 #", info.getString("song")); assertEquals("歌手 + &", info.getString("artist")); assertEquals("专辑 ;", info.getString("album"))
        assertEquals(file.name, info.getString("filename")); assertContentEquals(file.readBytes(), receivedPcmFile)
    }
    @Test fun existingRemoteFileSkipsNosAndRealWeapiFallbackCanRegisterIt() = runBlocking<Unit> {
        needUpload = false; rejectEapiInfo = true
        assertEquals(456L, client().uploadCloudSong(wave(), progress::add))
        assertFalse(paths.contains("/lbs")); assertNull(receivedPcmFile)
        assertTrue(paths.contains("/weapi/upload/cloud/info/v2")); assertTrue(published)
    }
    @Test fun rejectedNosUploadNeverRegistersPublishesOrReportsCompletion() = runBlocking<Unit> {
        rejectUpload = true; val client = client()
        val error = assertFailsWith<java.io.IOException> { client.uploadCloudSong(wave(), progress::add) }
        assertTrue(error.message.orEmpty().contains("503"), "Unexpected rejected-upload failure: ${error.message}; reached routes=$paths")
        assertFalse(paths.contains("/eapi/upload/cloud/info/v2")); assertFalse(published)
        assertFalse(progress.any { it.stage == "上传完成" })
        assertTrue(client.cloudSongs().values.isEmpty())
    }
    @Test fun cancellingBlockedNosResponsePromptlyReleasesSnapshotWithoutPublishing() = runBlocking<Unit> {
        blockUpload = true
        val upload = async(Dispatchers.IO) { client().uploadCloudSong(wave(), progress::add) }
        withTimeout(15000) { reachedUpload.await() }
        withTimeout(2000) { upload.cancelAndJoin() }
        assertTrue(upload.isCancelled); assertFalse(published)
        assertFalse(progress.any { it.stage == "上传完成" })
    }
    @Test fun absentAccountEmptyAndInvalidAudioMakeNoCloudRequests() = runBlocking<Unit> {
        assertFailsWith<java.io.IOException> { client("").uploadCloudSong(wave()) }
        assertFailsWith<java.io.IOException> { client().uploadCloudSong(File(root, "empty.mp3").apply { writeBytes(byteArrayOf()) }) }
        assertFailsWith<java.io.IOException> { client().uploadCloudSong(File(root, "invalid.mp3").apply { writeText("not audio") }) }
        assertTrue(paths.isEmpty())
    }
}
