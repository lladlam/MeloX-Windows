package melox.provider.local

import java.io.File
import java.net.URI
import melox.lyrics.LyricFileParser
import melox.lyrics.LyricsDocument

/** Resolve same-directory, same-basename sidecars without modifying user files. */
internal object LocalLyricsFiles {
    private const val MaxFileBytes = 4 * 1024 * 1024
    private val extensions = setOf("lrc", "krc", "qrc", "ttml", "lys", "lyl", "txt")

    private fun files(location: String): List<File> {
        val audio = runCatching {
            if (location.startsWith("file:", true)) File(URI(location))
            else if (location.contains("://")) return emptyList() else File(location)
        }.getOrNull() ?: return emptyList()
        return audio.parentFile?.listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.extension.lowercase() in extensions && it.nameWithoutExtension.equals(audio.nameWithoutExtension, true) }
            .sortedBy { it.name.lowercase() }.toList()
    }

    fun read(location: String): LyricsDocument? = files(location).mapNotNull { file ->
        runCatching {
            if (file.length() > MaxFileBytes) return@runCatching null
            val bytes = file.inputStream().use { it.readNBytes(MaxFileBytes + 1) }
            if (bytes.size > MaxFileBytes) return@runCatching null
            LyricFileParser.parse(bytes).takeIf { it.lines.isNotEmpty() }
        }.getOrNull()
    }.maxByOrNull { document -> document.lines.count { it.syllables.isNotEmpty() } * 100 + document.lines.size }
}
