package melox.remoteconfig

import melox.network.MeloXGitHubRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MeloXRemoteConfigRuntime {
    /** Protocol compatibility, not the Windows distribution's version or provider identity.
     * Android 3f877ea uses app versionCode 20 for the same signed policy schema.
     */
    const val PolicyCompatibilityVersionCode = 20
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val mutableStatus = MutableStateFlow(MeloXRemoteConfigStatus())
    val status: StateFlow<MeloXRemoteConfigStatus> = mutableStatus.asStateFlow()
    private val mutableEnabled = MutableStateFlow(MeloXRemoteConfigConsent.enabled())
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    @Volatile
    private var client: MeloXRemoteConfigClient? = null

    @Volatile
    private var versionCode: Int = PolicyCompatibilityVersionCode

    @Volatile
    private var periodicRefreshJob: Job? = null
    @Volatile private var startupRefreshJob: Job? = null
    @Volatile private var activeRequestJob: Job? = null
    @Volatile private var foreground = true
    @Volatile private var running = false

    fun initializeAndRefresh(versionCode: Int = PolicyCompatibilityVersionCode, force: Boolean = false) {
        require(versionCode > 0)
        synchronized(this) {
            this.versionCode = versionCode
            running = true
            if (client == null) {
                client = MeloXRemoteConfigClient(
                    store = MeloXRemoteConfigStore(),
                    verifier = MeloXRemoteConfigVerifier(),
                    routing = MeloXGitHubRouting(),
                )
            }
            periodicRefreshJob?.cancel()
            startupRefreshJob?.cancel()
            mutableEnabled.value = MeloXRemoteConfigConsent.enabled()
            if (!mutableEnabled.value) {
                mutableStatus.value = MeloXRemoteConfigStatus()
                return
            }
            periodicRefreshJob = scope.launch {
                while (isActive && MeloXRemoteConfigConsent.enabled()) {
                    delay(MeloXRemoteConfigRefreshIntervalMs)
                    if (foreground && MeloXRemoteConfigConsent.enabled()) {
                        refresh(force = true)
                    }
                }
            }
        }
        startupRefreshJob = scope.launch {
            refreshMutex.withLock {
                if (MeloXRemoteConfigConsent.enabled()) mutableStatus.value = client?.load(versionCode) ?: return@launch
            }
            refresh(force = force)
            if (mutableStatus.value.error != null) {
                delay(1_500L)
                refresh(force = true)
            }
        }
    }

    suspend fun refresh(force: Boolean = true) = refreshMutex.withLock {
        if (!running || !MeloXRemoteConfigConsent.enabled()) return@withLock
        val active = client ?: return@withLock
        val owner = currentCoroutineContext().job
        activeRequestJob = owner
        mutableStatus.value = mutableStatus.value.copy(refreshing = true, error = null)
        try {
            val result = active.refresh(versionCode, force)
            if (MeloXRemoteConfigConsent.enabled()) mutableStatus.value = result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableStatus.value = mutableStatus.value.copy(error = failure.message ?: "配置检查失败") }
        finally {
            if (activeRequestJob === owner) activeRequestJob = null
            mutableStatus.value = if (MeloXRemoteConfigConsent.enabled()) mutableStatus.value.copy(refreshing = false) else MeloXRemoteConfigStatus()
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            MeloXRemoteConfigConsent.accept()
            initializeAndRefresh(force = true)
        } else {
            MeloXRemoteConfigConsent.reject()
            mutableEnabled.value = false
            stop()
            clearCache()
        }
    }

    fun setForeground(value: Boolean) {
        val resumed = !foreground && value
        foreground = value
        if (resumed && running && MeloXRemoteConfigConsent.enabled()) scope.launch { refresh(force = true) }
    }

    fun stop() {
        running = false
        periodicRefreshJob?.cancel(); periodicRefreshJob = null
        startupRefreshJob?.cancel(); startupRefreshJob = null
        activeRequestJob?.cancel()
    }

    suspend fun clearCache() {
        activeRequestJob?.takeUnless { it === currentCoroutineContext().job }?.cancel()
        refreshMutex.withLock {
            if (!MeloXRemoteConfigConsent.enabled()) {
                periodicRefreshJob?.cancel()
                periodicRefreshJob = null
            }
            val active = client
            mutableStatus.value = when {
                active != null -> active.clear(versionCode)
                else -> { MeloXRemoteConfigStore().clearEnvelope(); MeloXRemoteConfigStatus() }
            }
        }
    }
}

internal const val MeloXRemoteConfigRefreshIntervalMs = 2L * 60L * 60L * 1_000L
