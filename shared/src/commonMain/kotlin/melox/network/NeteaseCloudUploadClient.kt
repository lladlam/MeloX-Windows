package melox.network

import kotlinx.coroutines.*
import melox.account.NeteaseSessionStore
import melox.player.FfmpegLocator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class MeloXCloudUploadProgress(val stage: String, val uploadedBytes: Long = 0, val totalBytes: Long = 0)

/** Android 3f877ea cloud upload protocol: snapshot/hash/check/token/LBS/NOS/info/publish.
 * Production callers supply the existing authenticated EAPI and WEAPI transports.
 */
@OptIn(InternalCoroutinesApi::class)
internal class NeteaseCloudUploadClient(
    private val cookieProvider: () -> String,
    client: OkHttpClient,
    private val eapi: (String, JSONObject) -> JSONObject,
    private val weapi: (String, JSONObject) -> JSONObject,
) {
    private val uploadClient = client.newBuilder().readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

    suspend fun upload(file: File, progress: (MeloXCloudUploadProgress) -> Unit): Long = withContext(Dispatchers.IO) {
        val cookie = cookieProvider()
        if (!NeteaseSessionStore.containsMusicU(cookie)) throw IOException("请先登录网易云音乐")
        if (!file.isFile || !file.canRead()) throw IOException("无法读取所选音频文件")
        val extension = file.extension.lowercase().ifBlank { "mp3" }
        val mime = when (extension) {
            "flac" -> "audio/flac"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "oga", "opus" -> "audio/ogg"
            else -> "audio/mpeg"
        }
        val owner = currentCoroutineContext().job
        val snapshotDirectory = File(melox.platform.meloXCacheDir(), "cloud-upload").apply { mkdirs() }
        val temporary = File.createTempFile("melox-cloud-", ".$extension", snapshotDirectory)
        try {
            progress(MeloXCloudUploadProgress("准备音频", totalBytes = file.length()))
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(1_048_576)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count); digest.update(buffer, 0, count)
                }
            } }
            if (temporary.length() == 0L) throw IOException("所选音频文件为空")
            val md5 = digest.digest().joinToString("") { "%02x".format(it) }
            val metadata = try {
                withTimeout(10_000) { withContext(Dispatchers.IO) { metadata(temporary, currentCoroutineContext().job) } }
            } catch (_: TimeoutCancellationException) { throw IOException("读取音频元数据超时") }
            val stem = file.nameWithoutExtension.filterNot(Char::isWhitespace).replace('.', '_').ifBlank { "music" }
            val title = clean(metadata.title, stem, 200)
            val artist = clean(metadata.artist, "未知艺术家", 200)
            val album = clean(metadata.album, "未知专辑", 200)
            val filename = clean(file.name, "music.$extension", 255)
            val bitrate = metadata.bitrate.takeIf { it > 0 } ?: 128000
            progress(MeloXCloudUploadProgress("检查云端文件", totalBytes = temporary.length()))
            val checked = eapi("/api/cloud/upload/check", JSONObject().put("bitrate", bitrate).put("ext", extension)
                .put("length", temporary.length()).put("md5", md5).put("songId", "0").put("version", 1))
            ensureActive()
            var songId = checked.optString("songId").takeIf(String::isNotBlank)
                ?: checked.optJSONObject("data")?.optString("songId").orEmpty()
            if (songId.isBlank() || songId == "0") throw IOException("云盘上传检查未返回歌曲标识")
            val tokenResponse = eapi("/api/nos/token/alloc", JSONObject().put("bucket", "jd-musicrep-private")
                .put("ext", extension).put("filename", stem).put("local", false).put("md5", md5)
                .put("product", 3).put("type", "audio"))
            val tokenData = tokenResponse.optJSONObject("result") ?: tokenResponse.optJSONObject("data") ?: tokenResponse
            val bucket = tokenData.optString("bucket").ifBlank { "jd-musicrep-private" }
            val objectKey = tokenData.optString("objectKey").ifBlank { tokenData.optString("objectName") }
            val token = tokenData.optString("token")
            val resourceId = tokenData.optString("resourceId").ifBlank { tokenData.optString("resId") }
            if (objectKey.isBlank() || token.isBlank() || resourceId.isBlank()) throw IOException("云盘上传凭据不完整")

            if (checked.optBoolean("needUpload", true)) {
                progress(MeloXCloudUploadProgress("上传音频", totalBytes = temporary.length()))
                val lbs = Request.Builder().url("https://wanproxy.127.net/lbs?version=1.0&bucketname=${encode(bucket)}")
                    .header("User-Agent", "Mozilla/5.0").header("Referer", "https://music.163.com").header("Cookie", cookie).build()
                val host = uploadClient.executeOwned(lbs, owner) { response ->
                    if (!response.isSuccessful) throw IOException("获取云盘上传地址失败：HTTP ${response.code}")
                    val body = parseNeteaseResponseJson(response.body?.string().orEmpty())
                    (body.optJSONArray("upload")?.optString(0) ?: body.optJSONArray("uploadHosts")?.optString(0))
                        ?.takeIf(String::isNotBlank) ?: throw IOException("云盘上传地址为空")
                }.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }.trimEnd('/')
                val payload = object : RequestBody() {
                    override fun contentType() = mime.toMediaType()
                    override fun contentLength() = temporary.length()
                    override fun writeTo(sink: BufferedSink) {
                        temporary.inputStream().use { input ->
                            val buffer = ByteArray(65536); var uploaded = 0L; var lastPercent = -1L
                            while (true) {
                                owner.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                sink.write(buffer, 0, count); uploaded += count
                                val percent = uploaded * 100 / temporary.length()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    progress(MeloXCloudUploadProgress("上传音频", uploaded, temporary.length()))
                                }
                            }
                        }
                    }
                }
                val path = objectKey.split('/').joinToString("/") { encode(it) }
                val request = Request.Builder().url("$host/${encode(bucket)}/$path?offset=0&complete=true&version=1.0")
                    .header("x-nos-token", token).header("Content-MD5", md5).header("User-Agent", "Mozilla/5.0").put(payload).build()
                uploadClient.executeOwned(request, owner) { response ->
                    if (!response.isSuccessful) throw IOException("上传音频失败：HTTP ${response.code}")
                    response.body?.string()
                }
            }
            progress(MeloXCloudUploadProgress("登记云盘歌曲", temporary.length(), temporary.length()))
            var info: JSONObject? = null
            var lastFailure: Exception? = null
            outer@ for (candidateBitrate in listOf(bitrate, 128000, 999000).distinct()) {
                for (encodedFilename in listOf(filename, encode(filename)).distinct()) {
                    val payload = JSONObject().put("md5", md5).put("songid", songId).put("filename", encodedFilename)
                        .put("song", title).put("album", album).put("artist", artist).put("bitrate", candidateBitrate).put("resourceId", resourceId)
                    for (transport in listOf(eapi, weapi)) {
                        owner.ensureActive()
                        try { info = transport("/api/upload/cloud/info/v2", payload); break@outer }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { owner.ensureActive(); lastFailure = failure }
                    }
                }
            }
            val uploadedInfo = info ?: throw IOException("上传云盘信息失败：${lastFailure?.message ?: "参数被拒绝"}")
            songId = uploadedInfo.optString("songId").takeIf(String::isNotBlank)
                ?: uploadedInfo.optJSONObject("data")?.optString("songId")?.takeIf(String::isNotBlank) ?: songId
            val id = songId.toLongOrNull()?.takeIf { it > 0 } ?: throw IOException("云盘歌曲标识无效")
            eapi("/api/cloud/pub/v2", JSONObject().put("songid", songId))
            ensureActive()
            progress(MeloXCloudUploadProgress("上传完成", temporary.length(), temporary.length()))
            id
        } finally { temporary.delete() }
    }

    private data class Metadata(val title: String, val artist: String, val album: String, val bitrate: Int)
    private fun metadata(file: File, owner: Job): Metadata {
        val process = ProcessBuilder(FfmpegLocator.command() + listOf("-hide_banner", "-nostdin", "-i", file.path, "-f", "ffmetadata", "pipe:1")).start()
        val stderr = StringBuilder()
        val reader = thread(name = "melox-cloud-metadata", isDaemon = true) {
            runCatching { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                synchronized(stderr) { if (stderr.length < 16384) stderr.append(line.take(2048)).append('\n') }
            } } }
        }
        val cancellation = owner.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { process.destroyForcibly() }
        try {
            val tags = process.inputStream.use { String(it.readNBytes(65536), Charsets.UTF_8) }
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw IOException("读取音频元数据超时")
            reader.join(1000)
            owner.ensureActive()
            val details = synchronized(stderr) { stderr.toString() }
            if (process.exitValue() != 0 || !details.contains("Audio:")) throw IOException("所选文件不是可读取的音频")
            val fields = tags.lines().mapNotNull { line ->
                if (line.startsWith(';') || '=' !in line) null else line.substringBefore('=').lowercase() to
                    line.substringAfter('=').replace(Regex("\\\\([\\\\=;#])"), "$1")
            }.toMap()
            val bitrate = Regex("bitrate: (\\d+) kb/s").find(details)?.groupValues?.get(1)?.toIntOrNull()?.times(1000) ?: 0
            return Metadata(fields["title"].orEmpty(), fields["artist"].orEmpty(), fields["album"].orEmpty(), bitrate)
        } finally {
            cancellation.dispose(); process.destroyForcibly()
            runCatching { process.inputStream.close(); process.errorStream.close() }
            process.waitFor(2, TimeUnit.SECONDS); reader.join(1000)
        }
    }
    private fun clean(value: String, fallback: String, limit: Int) = value.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
        .replace(Regex("\\s+"), " ").trim().take(limit).ifBlank { fallback }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
