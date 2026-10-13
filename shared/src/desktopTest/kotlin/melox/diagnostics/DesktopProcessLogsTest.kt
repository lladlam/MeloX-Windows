package melox.diagnostics

import java.nio.file.Files
import kotlin.test.*

class DesktopProcessLogsTest {
    @Test fun credentialsAndSignedUrlPathsAreRedactedBeforeEitherBufferOrDisk() {
        val secret = "private-diagnostic-test-secret"
        val message = "Authorization: Bearer $secret\nCookie: MUSIC_U=$secret\napiKey=$secret\nFFmpeg HTTPS failure https://user:$secret@cdn.example/audio/$secret?token=$secret\nAudio output: unavailable"
        val returned = MeloXProcessLogs.append("ERROR", "validation", message)
        assertFalse(returned.contains(secret))
        assertTrue(returned.contains("cdn.example/<redacted>"))
        assertTrue(returned.contains("Audio output: unavailable"))
        assertFalse(MeloXProcessLogs.snapshot().last().contains(secret))
        assertFalse(MeloXProcessLogs.file.readText().contains(secret))
    }

    @Test fun logRotationPreservesUtf8LatestAndPreviousRecordsAtTheActualByteLimit() {
        val root = Files.createTempDirectory("melox-log-rotation").toFile()
        try {
            val file = java.io.File(root, "debug.log")
            val log = MeloXRotatingLog(file, 32)
            log.append("声音设备 initialized")
            assertEquals("声音设备 initialized\n", file.readText())
            log.append("下一次输出失败")
            assertEquals("下一次输出失败\n", file.readText())
            assertEquals("声音设备 initialized\n", java.io.File(file.path + ".1").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun diagnosticExporterIncludesRealCurrentProcessLinesInsteadOfAnEmptyStub() {
        val root = Files.createTempDirectory("melox-log-export").toFile()
        try {
            MeloXProcessLogs.append("INFO", "validation", "genuine-export-test-marker")
            val file = java.io.File(root, "export.txt")
            val result = MeloXLogExporter.exportRecentLogs(file, MeloXLogDeviceInfo("JVM", "validation", "test", emptyList()))
            assertTrue(result.lineCount > 0)
            assertTrue(file.readText().contains("genuine-export-test-marker"))
            assertFalse(file.readText().contains("当前没有可读取的应用日志"))
        } finally { root.deleteRecursively() }
    }
}
