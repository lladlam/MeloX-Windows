package melox.update

import melox.network.MeloXGitHubRouting
import melox.network.MeloXHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MeloXRelease(
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val publishedAt: String,
)

class MeloXUpdateClient(
    private val httpClient: OkHttpClient = MeloXHttpClient.shared,
    routing: MeloXGitHubRouting? = null,
) {
    private val routing = routing ?: MeloXGitHubRouting(httpClient)

    suspend fun latestStableRelease(forceSourceBenchmark: Boolean = false): MeloXRelease = withContext(Dispatchers.IO) {
        val router = routing
        var lastError: Throwable? = null
        val sources = router.candidates(forceSourceBenchmark)
        for (source in sources) {
            val request = Request.Builder()
                .url(router.routedUrl(source, GitHubReleasesUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MeloX-Desktop")
                .build()
            val result = runCatching {
                router.client(source).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("${source.label} HTTP ${response.code}")
                    parseReleases(response.body?.string() ?: throw IOException("空响应"))
                        ?: throw IOException("${source.label} 没有可用的 Release")
                }
            }
            result.getOrNull()?.let { return@withContext it }
            lastError = result.exceptionOrNull()
        }
        for (source in sources) {
            val request = Request.Builder()
                .url(router.routedUrl(source, MeloXGitHubRouting.UpdateManifestUrl))
                .header("Accept", "application/json")
                .header("User-Agent", "MeloX-Desktop")
                .build()
            val result = runCatching {
                router.client(source).newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("${source.label} HTTP ${response.code}")
                    parseManifest(response.body?.string() ?: throw IOException("空响应"))
                }
            }
            result.getOrNull()?.let { return@withContext it }
            lastError = result.exceptionOrNull()
        }
        throw IOException("更新检查失败：${lastError?.message ?: "所有 GitHub 源均不可用"}")
    }

    suspend fun downloadUrl(release: MeloXRelease): String? = withContext(Dispatchers.IO) {
        val original = release.apkUrl ?: return@withContext null
        val source = routing.candidates().firstOrNull() ?: return@withContext original
        routing.routedUrl(source, original)
    }

    internal fun parseManifest(body: String): MeloXRelease {
        val value = JSONObject(body)
        val version = (value.opt("version") as? String)?.trim().orEmpty()
        val pageUrl = (value.opt("pageUrl") as? String)?.trim().orEmpty()
        require(version.isNotBlank() && pageUrl.startsWith("https://github.com/lladlam/MeloX-Android/releases/")) {
            "更新清单格式错误"
        }
        val apkUrl = (value.opt("apkUrl") as? String)?.trim()?.takeIf(String::isNotBlank)
        require(apkUrl == null || apkUrl.startsWith("https://github.com/lladlam/MeloX-Android/releases/download/")) {
            "更新下载地址不受信任"
        }
        return MeloXRelease(
            version = version,
            name = (value.opt("name") as? String)?.ifBlank { version } ?: version,
            notes = (value.opt("notes") as? String).orEmpty(),
            pageUrl = pageUrl,
            apkUrl = apkUrl,
            apkName = (value.opt("apkName") as? String)?.trim()?.takeIf(String::isNotBlank),
            publishedAt = (value.opt("publishedAt") as? String).orEmpty(),
        )
    }

    internal fun parseReleases(body: String): MeloXRelease? {
        val releases = JSONArray(body)
        return (0 until releases.length())
            .asSequence()
            .mapNotNull(releases::optJSONObject)
            .filterNot { it.optBoolean("draft", false) }
            .mapNotNull { value ->
                val tag = (value.opt("tag_name") as? String)?.trim().orEmpty()
                val parts = versionParts(tag) ?: return@mapNotNull null
                val pageUrl = (value.opt("html_url") as? String)?.trim().orEmpty()
                if (!pageUrl.startsWith(ReleasePagePrefix)) return@mapNotNull null
                val asset = value.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).asSequence()
                        .mapNotNull(assets::optJSONObject)
                        .firstOrNull { (it.opt("name") as? String)?.endsWith(".apk", ignoreCase = true) == true }
                }
                val apkUrl = (asset?.opt("browser_download_url") as? String)?.trim()?.takeIf {
                    it.startsWith(ReleaseDownloadPrefix)
                }
                parts to MeloXRelease(
                    version = tag,
                    name = (value.opt("name") as? String)?.ifBlank { tag } ?: tag,
                    notes = (value.opt("body") as? String).orEmpty(),
                    pageUrl = pageUrl,
                    apkUrl = apkUrl,
                    apkName = (asset?.opt("name") as? String)?.trim()?.takeIf(String::isNotBlank),
                    publishedAt = (value.opt("published_at") as? String).orEmpty(),
                )
            }
            .maxWithOrNull(compareBy<Pair<List<Int>, MeloXRelease>>(
                { it.first[0] },
                { it.first[1] },
                { it.first[2] },
                { it.second.publishedAt },
            ))
            ?.second
    }

    fun isNewer(latest: String, current: String): Boolean {
        val left = versionParts(latest) ?: return false
        val right = versionParts(current) ?: return false
        for (index in left.indices) {
            val difference = left[index].compareTo(right[index])
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun versionParts(value: String): List<Int>? {
        val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    private companion object {
        const val GitHubReleasesUrl = "https://api.github.com/repos/lladlam/MeloX-Android/releases?per_page=100"
        const val ReleasePagePrefix = "https://github.com/lladlam/MeloX-Android/releases/"
        const val ReleaseDownloadPrefix = "https://github.com/lladlam/MeloX-Android/releases/download/"
        val VERSION_PATTERN = Regex(
            pattern = "^(?:android-)?v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?$",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
