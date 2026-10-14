package melox.diagnostics

import java.lang.management.ManagementFactory

internal expect fun processMemoryBytes(): Map<String, Long>

/** Aggregate measurements only: no URLs, file paths, track names or accounts. */
object MeloXMemoryDiagnostics {
    fun snapshot(): Map<String, Long> {
        val memory = ManagementFactory.getMemoryMXBean()
        return linkedMapOf(
            "uptime_ms" to ManagementFactory.getRuntimeMXBean().uptime,
            "heap_used_bytes" to memory.heapMemoryUsage.used,
            "heap_committed_bytes" to memory.heapMemoryUsage.committed,
            "heap_max_bytes" to memory.heapMemoryUsage.max,
            "non_heap_used_bytes" to memory.nonHeapMemoryUsage.used,
            "threads" to ManagementFactory.getThreadMXBean().threadCount.toLong(),
            "direct_buffer_bytes" to ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean::class.java)
                .filter { it.name == "direct" }.sumOf { it.memoryUsed },
        ).apply { putAll(runCatching { processMemoryBytes() }.getOrDefault(emptyMap())) }
    }

    fun describe(): String = snapshot().entries.joinToString(" ") { "${it.key}=${it.value}" }
}
