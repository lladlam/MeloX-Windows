package melox.remoteconfig

import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import melox.network.executeOwned
import melox.platform.logWarn
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Request

internal class MeloXRemoteConfigClient(
    private val store: MeloXRemoteConfigStore,
    private val verifier: MeloXRemoteConfigVerifier,
    private val routing: MeloXGitHubRouting,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun load(versionCode: Int): MeloXRemoteConfigStatus = statusFrom(store.read(), versionCode)

    suspend fun refresh(versionCode: Int, force: Boolean): MeloXRemoteConfigStatus =
        withContext(Dispatchers.IO) { refreshOnIo(versionCode, force) }

    private suspend fun refreshOnIo(versionCode: Int, force: Boolean): MeloXRemoteConfigStatus {
        val persisted = store.read()
        // A corrupt cached envelope must not keep sending an ETag that locks the
        // client into 304 responses. Preserve the rollback floor while refetching.
        val current = if (persisted.envelope != null && statusFrom(persisted, versionCode).source == MeloXRemoteConfigSource.BuiltIn) {
            persisted.copy(envelope = null, etag = null)
        } else persisted
        val timestamp = now()
        if (!force && timestamp - current.lastCheckedAtEpochMs in 0 until MeloXRemoteConfigRefreshIntervalMs) {
            return statusFrom(current, versionCode)
        }
        val errors = mutableListOf<String>()
        for (source in routing.candidates()) {
            try { return fetch(source, current, timestamp, versionCode, currentCoroutineContext().job) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val description = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
                errors += "${source.label}: $description"
                logWarn("MeloXRemoteConfig", "Remote config failed through ${source.label}: ${error.message}")
            }
        }
        currentCoroutineContext().ensureActive()
        val failed = current.copy(lastCheckedAtEpochMs = timestamp)
        store.write(failed)
        return statusFrom(failed, versionCode).copy(
            error = errors.takeIf(List<String>::isNotEmpty)?.joinToString("；") ?: "所有配置源均不可用",
        )
    }

    fun clear(versionCode: Int): MeloXRemoteConfigStatus {
        store.clearEnvelope()
        return load(versionCode)
    }

    private fun fetch(
        source: MeloXGitHubSource,
        current: MeloXStoredRemoteConfig,
        timestamp: Long,
        versionCode: Int,
        owner: Job,
    ): MeloXRemoteConfigStatus {
        val request = Request.Builder().url(routing.routedUrl(source, MeloXGitHubRouting.RemoteConfigUrl))
            .header("Accept", "application/json").header("User-Agent", "MeloX-Desktop").apply {
            current.etag?.let { header("If-None-Match", it) }
        }.build()
        return routing.client(source).newBuilder()
            .cache(null)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
            .executeOwned(request, owner) { response ->
            if (response.code == 304 && current.envelope != null) {
                val updated = current.copy(lastCheckedAtEpochMs = timestamp)
                owner.ensureActive()
                store.write(updated)
                return@executeOwned statusFrom(updated, versionCode)
            }
            if (!response.isSuccessful) throw IOException("${source.label} HTTP ${response.code}")
            val body = response.body
            if (body.contentLength() > MeloXRemoteConfigVerifier.MaxEnvelopeBytes) {
                throw IOException("Remote config response is too large")
            }
            val bytes = body.byteStream().use { it.readNBytes(MeloXRemoteConfigVerifier.MaxEnvelopeBytes + 1) }
            if (bytes.size > MeloXRemoteConfigVerifier.MaxEnvelopeBytes) throw IOException("Remote config response is too large")
            owner.ensureActive()
            val envelope = String(bytes, Charsets.UTF_8)
            val verified = verifier.verify(envelope)
            require(verified.config.configVersion >= current.highestConfigVersion) {
                "Remote config rollback rejected"
            }
            val stored = MeloXStoredRemoteConfig(
                envelope = envelope,
                etag = response.header("ETag"),
                lastCheckedAtEpochMs = timestamp,
                lastUpdatedAtEpochMs = timestamp,
                highestConfigVersion = maxOf(current.highestConfigVersion, verified.config.configVersion),
            )
            owner.ensureActive()
            store.write(stored)
            statusFrom(stored, versionCode)
        }
    }

    private fun statusFrom(stored: MeloXStoredRemoteConfig, versionCode: Int): MeloXRemoteConfigStatus {
        val verified = stored.envelope?.let { runCatching { verifier.verify(it) }.getOrNull() }
            ?.takeIf { it.config.configVersion >= stored.highestConfigVersion }
        val config = verified?.config ?: MeloXRemoteConfigDefaults.Config
        val source = when {
            verified == null -> MeloXRemoteConfigSource.BuiltIn
            config.appliesTo(versionCode) -> MeloXRemoteConfigSource.VerifiedRemote
            else -> MeloXRemoteConfigSource.VersionInapplicable
        }
        return MeloXRemoteConfigStatus(
            source = source,
            config = if (source == MeloXRemoteConfigSource.VerifiedRemote) config else MeloXRemoteConfigDefaults.Config,
            keyId = verified?.keyId,
            lastCheckedAtEpochMs = stored.lastCheckedAtEpochMs,
            lastUpdatedAtEpochMs = stored.lastUpdatedAtEpochMs,
        )
    }
}
