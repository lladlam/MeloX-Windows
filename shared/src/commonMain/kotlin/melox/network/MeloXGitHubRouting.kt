package melox.network

import melox.platform.getPreferences
import melox.platform.logWarn
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

enum class MeloXGitHubSource(val label: String) {
    Auto("自动选择"),
    GitHubDoh("GitHub DoH"),
    GhFast("GhFast"),
    GhProxy("GhProxy"),
    GhProxyOrg("GhProxy.org"),
}

data class MeloXGitHubRouteResult(
    val source: MeloXGitHubSource,
    val latencyMs: Long,
)

class MeloXGitHubRouting(
    private val baseClient: OkHttpClient = MeloXHttpClient.shared,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = getPreferences(PreferencesName)
    private val legacyPreferences = getPreferences(LegacyPreferencesName)
    private val dohClient: OkHttpClient by lazy {
        baseClient.newBuilder().cache(null).build()
    }

    fun selectedSource(): MeloXGitHubSource = parseSource(
        preferences.getString(SelectedSourceKey, null),
        legacyPreferences.getString(LegacySelectedSourceKey, null),
    )

    fun selectSource(source: MeloXGitHubSource) {
        preferences.apply {
            putString(SelectedSourceKey, source.name)
            remove(LegacySelectedSourceKey)
            remove(AutoSourceKey)
            remove(AutoLatencyKey)
            remove(AutoCheckedAtKey)
        }
        legacyPreferences.remove(LegacySelectedSourceKey)
    }

    fun effectiveRoute(): MeloXGitHubRouteResult? {
        val source = preferences.getString(AutoSourceKey, null)
            ?.let { runCatching { MeloXGitHubSource.valueOf(it) }.getOrNull() }
            ?.takeUnless { it == MeloXGitHubSource.Auto }
            ?: return null
        return MeloXGitHubRouteResult(source, preferences.getLong(AutoLatencyKey, 0L))
    }

    suspend fun candidates(forceBenchmark: Boolean = false): List<MeloXGitHubSource> {
        val selected = selectedSource()
        if (selected != MeloXGitHubSource.Auto) return listOf(selected)
        return BenchmarkMutex.withLock {
            val timestamp = now()
            val cached = effectiveRoute()
            val checkedAt = preferences.getLong(AutoCheckedAtKey, 0L)
            if (!forceBenchmark && cached != null && timestamp - checkedAt in 0 until BenchmarkTtlMs) {
                return@withLock listOf(cached.source) + ConcreteSources.filterNot { it == cached.source }
            }
            val results = coroutineScope {
                ConcreteSources.map { source ->
                    async(Dispatchers.IO) { benchmark(source) }
                }.awaitAll().filterNotNull().sortedBy(MeloXGitHubRouteResult::latencyMs)
            }
            val winner = results.firstOrNull()
            if (winner != null) {
                preferences.apply {
                    putString(AutoSourceKey, winner.source.name)
                    putLong(AutoLatencyKey, winner.latencyMs)
                    putLong(AutoCheckedAtKey, timestamp)
                }
            }
            results.map(MeloXGitHubRouteResult::source) +
                ConcreteSources.filterNot { source -> results.any { it.source == source } }
        }
    }

    fun routedUrl(source: MeloXGitHubSource, originalUrl: String): String {
        return routedUrlFor(source, originalUrl)
    }

    fun client(source: MeloXGitHubSource): OkHttpClient = when (source) {
        MeloXGitHubSource.GitHubDoh -> dohClient
        else -> baseClient
    }

    private fun benchmark(source: MeloXGitHubSource): MeloXGitHubRouteResult? {
        val client = client(source).newBuilder()
            .cache(null)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(routedUrl(source, UpdateManifestUrl))
            .header("Accept", "application/json")
            .header("Range", "bytes=0-4095")
            .header("User-Agent", "MeloX-Desktop")
            .build()
        val startedAt = System.nanoTime()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                if (!body.contains("\"version\"")) return@use null
                MeloXGitHubRouteResult(source, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
            }
        }.getOrNull()
    }

    companion object {
        const val UpdateManifestUrl =
            "https://raw.githubusercontent.com/lladlam/MeloX-Remote-Config-Public/main/v1/update.json"
        const val RemoteConfigUrl =
            "https://raw.githubusercontent.com/lladlam/MeloX-Remote-Config-Public/main/v1/latest.json"

        private const val PreferencesName = "melox_github_routing"
        private const val LegacyPreferencesName = "melox_app_settings"
        private const val SelectedSourceKey = "selected_source"
        private const val AutoSourceKey = "auto_source"
        private const val AutoLatencyKey = "auto_latency_ms"
        private const val AutoCheckedAtKey = "auto_checked_at_ms"
        private const val LegacySelectedSourceKey = "update_download_source"
        private const val BenchmarkTtlMs = 6L * 60L * 60L * 1_000L
        private val BenchmarkMutex = Mutex()
        private val ConcreteSources = MeloXGitHubSource.entries.filterNot { it == MeloXGitHubSource.Auto }

        internal fun routedUrlFor(source: MeloXGitHubSource, originalUrl: String): String {
            require(source != MeloXGitHubSource.Auto) { "Auto is not a concrete GitHub source" }
            return when (source) {
                MeloXGitHubSource.GitHubDoh -> originalUrl
                MeloXGitHubSource.GhFast -> "https://ghfast.top/$originalUrl"
                MeloXGitHubSource.GhProxy -> "https://ghproxy.net/$originalUrl"
                MeloXGitHubSource.GhProxyOrg -> "https://gh-proxy.org/$originalUrl"
                MeloXGitHubSource.Auto -> error("Auto is not a concrete GitHub source")
            }
        }

        internal fun parseSource(value: String?, legacyValue: String? = null): MeloXGitHubSource {
            runCatching { value?.let(MeloXGitHubSource::valueOf) }.getOrNull()?.let { return it }
            return when (legacyValue) {
                "GitHub" -> MeloXGitHubSource.GitHubDoh
                else -> MeloXGitHubSource.Auto
            }
        }
    }
}
