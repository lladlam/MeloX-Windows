package melox.diagnostics

import kotlin.test.Test
import kotlin.test.assertTrue

class MemoryDiagnosticsTest {
    @Test fun currentProcessHeapAndOperatingSystemMemoryAreRealPositiveMeasurements() {
        val sample = MeloXMemoryDiagnostics.snapshot()
        assertTrue(sample.getValue("heap_used_bytes") > 0)
        assertTrue(sample.getValue("heap_committed_bytes") >= sample.getValue("heap_used_bytes"))
        assertTrue(sample.getValue("uptime_ms") > 0)
        val os = System.getProperty("os.name")
        if (os.startsWith("Windows") || os == "Linux") assertTrue(sample.getValue("resident_bytes") > 0)
        if (os.startsWith("Windows")) assertTrue(sample.getValue("private_commit_bytes") > 0)
    }
}
