package melox.provider.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.platform.meloXDataDir
import java.io.File
import java.security.MessageDigest

class LocalMediaScanner(
    private val repository: LocalMusicRepository = LocalMusicRepository(),
) {
    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma")

    suspend fun scanAll(): List<LocalTrackRecord> = withContext(Dispatchers.IO) {
        val roots = repository.scanRoots().mapNotNull { root ->
            File(root.uri.removePrefix("file://")).takeIf(File::isDirectory)
        } + listOf(defaultMusicDirectory())
        val records = roots.flatMap { scanDirectory(it) }.distinctBy(LocalTrackRecord::fileKey)
        repository.replaceTracks(records)
        records
    }

    private fun defaultMusicDirectory(): File =
        File(meloXDataDir(), "music").takeIf(File::isDirectory) ?: File(System.getProperty("user.home"), "Music")

    private fun scanDirectory(directory: File): List<LocalTrackRecord> = buildList {
        val children = runCatching { directory.listFiles()?.toList() }.getOrNull().orEmpty()
        children.forEach { child ->
            when {
                child.isDirectory -> addAll(scanDirectory(child))
                child.isFile && child.extension.lowercase() in audioExtensions -> {
                    runCatching { scanFile(child) }.getOrNull()?.let(::add)
                }
            }
        }
    }

    private fun scanFile(file: File): LocalTrackRecord {
        val baseName = file.nameWithoutExtension
        val (artist, title) = splitFileName(baseName)
        return LocalTrackRecord(
            fileKey = fileKey(file),
            contentUri = file.toURI().toString(),
            displayName = file.name,
            title = title,
            artist = artist,
            album = file.parentFile?.name.orEmpty(),
            durationMs = 0L,
            mimeType = "audio/${file.extension.lowercase()}",
            sizeBytes = file.length(),
            lastModifiedMs = file.lastModified(),
            sourceRootUri = file.toURI().toString(),
        )
    }

    private fun splitFileName(baseName: String): Pair<String, String> {
        val dash = baseName.indexOf(" - ")
        return if (dash > 0) {
            baseName.substring(0, dash).trim() to baseName.substring(dash + 3).trim()
        } else {
            "" to baseName.trim()
        }
    }

    private fun fileKey(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest("${file.absolutePath}:${file.length()}".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(24)
}
