package melox.platform

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal interface MeloXDisplayExecutionApi : StdCallLibrary {
    fun SetThreadExecutionState(flags: Int): Int
}

data class MeloXDisplayAwakeSnapshot(val available: Boolean, val requested: Boolean = false,
    val active: Boolean = false, val nativeCalls: Int = 0, val previousFlags: Int = 0,
    val ownerThreadId: Long = 0, val error: String? = null)

/** Owns this thread's display/system idle hold; acquire/release must use the same thread. */
class MeloXDisplayAwakeController(initialForeground: Boolean = true) : AutoCloseable {
    private val available = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "MeloX-display-awake").apply { isDaemon = true } }
    private val lock = Any()
    private var release: Future<MeloXDisplayAwakeSnapshot>? = null
    // Owned by executor thread. Native window events gate even a delayed UI
    // composition, so minimizing cannot leave a stale foreground request alive.
    private var foreground = initialForeground
    private var requiredByUi = false
    private val api by lazy { Native.load("kernel32", MeloXDisplayExecutionApi::class.java) }
    @Volatile var snapshot = MeloXDisplayAwakeSnapshot(available)
        private set

    fun setRequired(required: Boolean): Future<MeloXDisplayAwakeSnapshot> = synchronized(lock) {
        // Child UI disposal may follow the window owner's disposal. A late
        // request can never resurrect a released native thread/request.
        release?.let { return@synchronized it }
        executor.submit<MeloXDisplayAwakeSnapshot> { requiredByUi = required; apply(requiredByUi && foreground) }
    }

    fun setForeground(value: Boolean): Future<MeloXDisplayAwakeSnapshot> = synchronized(lock) {
        release?.let { return@synchronized it }
        executor.submit<MeloXDisplayAwakeSnapshot> { foreground = value; apply(requiredByUi && foreground) }
    }

    private fun apply(required: Boolean): MeloXDisplayAwakeSnapshot {
        val previous = snapshot
        if (previous.requested == required && previous.active == required && previous.error == null) return previous
        if (!available) return previous.copy(requested = required, active = false).also { snapshot = it }
        // Keep a visible foreground lyric display awake, including its automatic
        // system idle timer. No away mode; explicit user sleep/lid actions win.
        val flags = Int.MIN_VALUE or if (required) 3 else 0 // CONTINUOUS | DISPLAY_REQUIRED | SYSTEM_REQUIRED
        val state = try {
            val oldFlags = api.SetThreadExecutionState(flags)
            if (oldFlags == 0) previous.copy(requested = required, nativeCalls = previous.nativeCalls + 1,
                error = "SetThreadExecutionState failed: ${Native.getLastError()}")
            else MeloXDisplayAwakeSnapshot(true, required, required, previous.nativeCalls + 1, oldFlags, Thread.currentThread().id)
        } catch (failure: Exception) {
            previous.copy(requested = required, error = "${failure.javaClass.simpleName}: ${failure.message}")
        } catch (failure: LinkageError) {
            previous.copy(requested = required, error = "${failure.javaClass.simpleName}: ${failure.message}")
        }
        snapshot = state
        if (state.error == null) logInfo("MeloXDisplayAwake", "requested=$required active=${state.active} owner_thread=${state.ownerThreadId}")
        else logError("MeloXDisplayAwake", state.error)
        return state
    }

    fun release(): Future<MeloXDisplayAwakeSnapshot> = synchronized(lock) {
        release ?: executor.submit<MeloXDisplayAwakeSnapshot> { apply(false) }.also {
            release = it
            executor.shutdown()
        }
    }

    override fun close() { release() }
}
