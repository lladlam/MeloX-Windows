package melox.update

import melox.network.MeloXGitHubRouting
import melox.network.MeloXHttpClient
import melox.platform.logDebug
import melox.platform.logError
import melox.platform.logInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 桌面自动更新检查器。
 * 检查 GitHub Release 是否有新版本。
 */
object UpdateChecker {
    private const val CHECK_URL = "https://raw.githubusercontent.com/lladlam/MeloX-Windows/main/version.json"
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    private var lastCheckTime = 0L
    private val checkIntervalMs = 6 * 60 * 60 * 1000L // 6小时

    suspend fun checkForUpdates(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < checkIntervalMs) {
            return@withContext UpdateResult.NoUpdate("检查间隔未到")
        }
        lastCheckTime = now

        try {
            val request = Request.Builder()
                .url(CHECK_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "MeloX-Windows/$currentVersion")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateResult.NoUpdate("HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return@withContext UpdateResult.NoUpdate("空响应")
            val json = org.json.JSONObject(body)

            val latestVersion = json.optString("version", "")
            val downloadUrl = json.optString("downloadUrl", "")
            val releaseNotes = json.optString("releaseNotes", "")
            val minVersion = json.optString("minVersion", "")

            if (latestVersion.isBlank()) {
                return@withContext UpdateResult.NoUpdate("无版本信息")
            }

            if (isNewerVersion(latestVersion, currentVersion)) {
                UpdateResult.HasUpdate(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes,
                    isBreaking = isNewerVersion(latestVersion, minVersion),
                )
            } else {
                UpdateResult.NoUpdate("已是最新版本")
            }
        } catch (e: Exception) {
            logError("UpdateChecker", "检查更新失败: ${e.message}")
            UpdateResult.NoUpdate("检查失败: ${e.message}")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxSize) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun downloadUpdate(downloadUrl: String, onProgress: (Float) -> Unit = {}): File? {
        return try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val tempFile = File.createTempFile("MeloX-Update", ".tmp")
            tempFile.deleteOnExit()

            val contentLength = response.body?.contentLength() ?: 0L
            var bytesRead = 0L

            response.body?.byteStream()?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            onProgress(bytesRead.toFloat() / contentLength)
                        }
                    }
                }
            }

            tempFile
        } catch (e: Exception) {
            logError("UpdateChecker", "下载更新失败: ${e.message}")
            null
        }
    }
}

sealed class UpdateResult {
    data class HasUpdate(
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isBreaking: Boolean,
    ) : UpdateResult()

    data class NoUpdate(val reason: String) : UpdateResult()
}
