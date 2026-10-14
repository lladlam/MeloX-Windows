package melox.diagnostics

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.win32.StdCallLibrary
import java.io.File

// PROCESS_MEMORY_COUNTERS_EX from psapi.h. SIZE_T must follow process bitness;
// the working set (resident) and PrivateUsage (private commit) are distinct.
@Structure.FieldOrder("cb", "pageFaultCount", "peakWorkingSetSize", "workingSetSize",
    "quotaPeakPagedPoolUsage", "quotaPagedPoolUsage", "quotaPeakNonPagedPoolUsage",
    "quotaNonPagedPoolUsage", "pagefileUsage", "peakPagefileUsage", "privateUsage")
internal class ProcessMemoryCounters : Structure() {
    @JvmField var cb: Int = 0
    @JvmField var pageFaultCount: Int = 0
    @JvmField var peakWorkingSetSize = SIZE_T()
    @JvmField var workingSetSize = SIZE_T()
    @JvmField var quotaPeakPagedPoolUsage = SIZE_T()
    @JvmField var quotaPagedPoolUsage = SIZE_T()
    @JvmField var quotaPeakNonPagedPoolUsage = SIZE_T()
    @JvmField var quotaNonPagedPoolUsage = SIZE_T()
    @JvmField var pagefileUsage = SIZE_T()
    @JvmField var peakPagefileUsage = SIZE_T()
    @JvmField var privateUsage = SIZE_T()
}

internal interface ProcessMemoryApi : StdCallLibrary {
    fun GetProcessMemoryInfo(process: HANDLE, counters: ProcessMemoryCounters, size: Int): Boolean
}

private val windowsApi by lazy { Native.load("psapi", ProcessMemoryApi::class.java) }

internal actual fun processMemoryBytes(): Map<String, Long> {
    val os = System.getProperty("os.name", "")
    if (os.startsWith("Windows", ignoreCase = true)) {
        val counters = ProcessMemoryCounters()
        counters.cb = counters.size()
        if (!windowsApi.GetProcessMemoryInfo(Kernel32.INSTANCE.GetCurrentProcess(), counters, counters.cb)) return emptyMap()
        return mapOf("resident_bytes" to counters.workingSetSize.toLong(),
            "private_commit_bytes" to counters.privateUsage.toLong())
    }
    if (os.equals("Linux", ignoreCase = true)) {
        val status = File("/proc/self/status").readLines()
        val rss = status.firstOrNull { it.startsWith("VmRSS:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
        return rss?.let { mapOf("resident_bytes" to it * 1024L) }.orEmpty()
    }
    return emptyMap()
}
