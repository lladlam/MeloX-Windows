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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MeloXRemoteConfigRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val mutableStatus = MutableStateFlow(MeloXRemoteConfigStatus())
    val status: StateFlow<MeloXRemoteConfigStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var client: MeloXRemoteConfigClient? = null

    @Volatile
    private var versionCode: Int = 0

    @Volatile
    private var periodicRefreshJob: Job? = null

    fun initializeAndRefresh(versionCode: Int, force: Boolean = false) {
        synchronized(this) {
            this.versionCode = versionCode
            if (client == null) {
                client = MeloXRemoteConfigClient(
                    store = MeloXRemoteConfigStore(),
                    verifier = MeloXRemoteConfigVerifier(),
                    routing = MeloXGitHubRouting(),
                )
            }
            periodicRefreshJob?.cancel()
            periodicRefreshJob = scope.launch {
                while (isActive && MeloXRemoteConfigConsent.enabled()) {
                    delay(MeloXRemoteConfigRefreshIntervalMs)
                    if (MeloXRemoteConfigConsent.enabled()) {
                        refresh(force = true)
                    }
                }
            }
        }
        scope.launch {
            mutableStatus.value = client?.load(versionCode) ?: return@launch
            refresh(force = force)
            if (mutableStatus.value.error != null) {
                delay(1_500L)
                refresh(force = true)
            }
        }
    }

    suspend fun refresh(force: Boolean = true) = refreshMutex.withLock {
        val active = client ?: return@withLock
        mutableStatus.value = mutableStatus.value.copy(refreshing = true, error = null)
        mutableStatus.value = active.refresh(versionCode, force).copy(refreshing = false)
    }

    suspend fun clearCache() = refreshMutex.withLock {
        if (!MeloXRemoteConfigConsent.enabled()) {
            periodicRefreshJob?.cancel()
            periodicRefreshJob = null
        }
        val active = client
        mutableStatus.value = when {
            active != null -> active.clear(versionCode)
            else -> MeloXRemoteConfigStatus()
        }
    }
}

internal const val MeloXRemoteConfigRefreshIntervalMs = 2L * 60L * 60L * 1_000L
