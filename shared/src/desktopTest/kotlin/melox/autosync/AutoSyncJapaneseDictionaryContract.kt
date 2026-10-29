package melox.autosync

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source

/** Opt-in integration with the real pinned release archive; no developer data is read or modified. */
object AutoSyncJapaneseDictionaryContract {
    @JvmStatic fun main(args: Array<String>) = runBlocking<Unit> {
        require(args.size == 1 || args.size == 3)
        val archive = File(args[0]).canonicalFile
        require(archive.isFile && archive.length() == AutoSyncJapaneseDictionary.RELEASE.archiveBytes)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(archive.source().buffer().asResponseBody(contentLength = archive.length())).build()
        }.build()
        val routing = MeloXGitHubRouting(client)
        val previous = routing.selectedSource()
        routing.selectSource(MeloXGitHubSource.GitHubDoh)
        val root = Files.createTempDirectory("melox-辞書-").toFile()
        try {
            val repo = AutoSyncJapaneseDictionary(root, routing)
            repo.install()
            check(repo.installed())
            val sizes = checkNotNull(repo.withInstalled { directory ->
                check(File(directory, "COPYING").readText().contains("Copyright", ignoreCase = true))
                if (args.size == 3) AutoSyncJapaneseFrontendContract.main(arrayOf(args[1], directory.absolutePath, args[2]))
                listOf("sys.dic", "matrix.bin", "char.bin", "unk.dic").map { File(directory, it).length() }
            })
            check(sizes.all { it > 0 })
            check(repo.remove() && !repo.installed())
            println("PASS: published NAIST archive SHA-256, bounded extraction, file verification, license and removal: $sizes")
        } finally { routing.selectSource(previous); root.deleteRecursively() }
    }
}
