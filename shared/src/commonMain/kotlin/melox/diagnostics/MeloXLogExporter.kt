package melox.diagnostics

import melox.MeloXBuildConfig
import melox.platform.meloXCacheDir
import melox.music.provider.ProviderAccountManager
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class MeloXLogExportResult(
    val lineCount: Int,
    val outputFile: File? = null,
)

data class MeloXLogDeviceInfo(
    val androidVersion: String,
    val phoneModel: String,
    val systemVersion: String,
    val loggedMusicSources: List<String>,
)

object MeloXLogExporter {
    private const val CommandTimeoutSeconds = 12L

    fun collectDeviceInfo(): MeloXLogDeviceInfo {
        val loggedSources = runCatching {
            val manager = ProviderAccountManager()
            manager.allStates()
                .filter { state: ProviderAccountManager.AccountState -> state.loggedIn }
                .map { it.source.displayName }
        }.getOrNull().orEmpty()
        return MeloXLogDeviceInfo(
            androidVersion = "${systemProperty("os.version")} (Desktop JVM)",
            phoneModel = "${systemProperty("os.name")} ${systemProperty("os.arch")}".trim(),
            systemVersion = detectSystemVersion(),
            loggedMusicSources = loggedSources,
        )
    }

    fun exportRecentLogs(
        outputFile: File,
        deviceInfo: MeloXLogDeviceInfo = collectDeviceInfo(),
    ): MeloXLogExportResult {
        val logcat = readProcessLogs()
        val content = buildString {
            appendLine("MeloX Desktop 日志")
            appendLine("日志范围：当前 MeloX 进程可读取的全部日志")
            appendLine("导出时间：${System.currentTimeMillis()}")
            appendLine("进程：${ProcessBuilder().command().firstOrNull() ?: "melox"}")
            appendLine("应用包名：melox")
            appendLine("应用版本：${systemProperty("melox.version") ?: "未知"}")
            appendLine("构建类型：${if (MeloXBuildConfig.DEBUG) "Debug" else "Release"}")
            appendLine("Android 版本：${deviceInfo.androidVersion}")
            appendLine("手机型号：${deviceInfo.phoneModel}")
            appendLine("系统版本：${deviceInfo.systemVersion}")
            appendLine("已登录音乐源：${deviceInfo.loggedMusicSources.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "无"}")
            appendLine("应用日志条数：${logcat.lineCount}")
            appendLine()
            appendLine("以下为当前 MeloX 进程日志，不包含其他应用进程日志：")
            appendLine()
            append(logcat.text.ifBlank { "（当前没有可读取的应用日志）\n" })
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(content, Charsets.UTF_8)
        return MeloXLogExportResult(logcat.lineCount, outputFile)
    }

    private fun readProcessLogs(): ProcessLogResult {
        // Desktop JVM has no logcat. Return empty logs; the export still produces
        // a valid file with device info and an explicit "no logs" notice.
        return ProcessLogResult(text = "", lineCount = 0)
    }

    private fun detectSystemVersion(): String {
        val hyperOsName = systemProperty("ro.mi.os.version.name")
        val hyperOsIncremental = systemProperty("ro.odm.build.version.incremental")
            .ifBlank { systemProperty("ro.mi.os.version.incremental") }
            .ifBlank { systemProperty("ro.build.version.incremental") }
        if (hyperOsName.isNotBlank() || hyperOsIncremental.startsWith("OS")) {
            val version = Regex("OS(\\d+(?:\\.\\d+){1,2})")
                .find(hyperOsIncremental)
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf(String::isNotBlank)
                ?: hyperOsName
            return "HyperOS ${version.ifBlank { "未知" }}"
        }

        val miui = systemProperty("ro.miui.ui.version.name")
        if (miui.isNotBlank()) return "MIUI $miui"

        val colorOs = systemProperty("ro.build.version.oplusrom")
        if (colorOs.isNotBlank()) return "ColorOS $colorOs"

        val originOs = systemProperty("ro.vivo.os.version")
        if (originOs.isNotBlank()) return "OriginOS $originOs"

        val harmonyOs = systemProperty("hw_sc.build.platform.version")
        if (harmonyOs.isNotBlank()) return "HarmonyOS $harmonyOs"

        val magicOs = systemProperty("ro.build.version.magic")
        if (magicOs.isNotBlank()) return "MagicOS $magicOs"

        val oneUi = systemProperty("ro.build.version.oneui")
        if (oneUi.isNotBlank()) return "One UI $oneUi"

        return systemProperty("os.name").takeIf(String::isNotBlank) ?: "未知"
    }

    private fun systemProperty(name: String): String = runCatching {
        System.getProperty(name).orEmpty().trim()
    }.getOrNull().orEmpty().trim()

    private data class ProcessLogResult(
        val text: String,
        val lineCount: Int,
    )
}