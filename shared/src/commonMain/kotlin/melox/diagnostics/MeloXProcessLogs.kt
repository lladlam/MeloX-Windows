package melox.diagnostics

/** Bounded current-process diagnostics. Credentials and signed URLs never enter the buffer. */
object MeloXProcessLogs {
    private const val Capacity = 400
    private val entries = ArrayDeque<String>()
    private val secrets = Regex("(?i)cookie|authorization|music_u|api[-_ ]?key|access[-_ ]?token|refresh[-_ ]?token|password|client[-_ ]?secret")
    private val urls = Regex("https?://[^\\s\\[\\]\\\"<>]+")
    val file: java.io.File by lazy { java.io.File(melox.platform.meloXDataDir(), "logs/melox-debug.log") }
    private val disk by lazy { MeloXRotatingLog(file) }
    private var diskFailureReported = false

    fun redact(message: String): String = message.lineSequence().joinToString("\n") { line ->
        if (secrets.containsMatchIn(line)) "[sensitive diagnostic omitted]" else urls.replace(line) { match ->
            runCatching { java.net.URI(match.value).let { "${it.scheme}://${it.host ?: "redacted"}/<redacted>" } }
                .getOrDefault("<redacted URL>")
        }
    }.replace(System.getProperty("user.home").orEmpty().takeIf(String::isNotBlank) ?: "\u0000", "~")

    fun append(level: String, tag: String, message: String): String {
        val safe = redact(message).take(4000)
        val entry = "${System.currentTimeMillis()} [$level $tag] $safe"
        synchronized(entries) {
            while (entries.size >= Capacity) entries.removeFirst()
            entries.addLast(entry)
            try { disk.append(entry) } catch (error: Exception) {
                // A read-only/full data directory must not turn a diagnostic into a playback failure.
                if (!diskFailureReported) {
                    diskFailureReported = true
                    System.err.println("MeloX debug log could not be written: ${redact(error.message.orEmpty())}")
                }
            }
        }
        return "[$level $tag] $safe"
    }

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }

    fun describe(error: Throwable): String {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        return generateSequence(error) { it.cause }.takeWhile { seen.add(it) }.take(5)
            .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
    }
}

internal class MeloXRotatingLog(private val file: java.io.File, private val maximumBytes: Long = 1_048_576L) {
    @Synchronized fun append(entry: String) {
        val bytes = (entry + "\n").toByteArray(Charsets.UTF_8)
        file.parentFile?.mkdirs()
        if (file.isFile && file.length() + bytes.size > maximumBytes) {
            java.nio.file.Files.move(file.toPath(), java.io.File(file.path + ".1").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        java.nio.file.Files.write(file.toPath(), bytes, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
    }
}
