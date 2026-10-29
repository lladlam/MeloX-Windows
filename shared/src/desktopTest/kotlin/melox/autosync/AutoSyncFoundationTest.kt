package melox.autosync

import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

class AutoSyncFoundationTest {
    @Test fun ctcMatchesPublishedNonzeroBlankAndInfeasibleVectors() {
        val rows = arrayOf(
            doubleArrayOf(-6.186353,-0.30164,-6.989353,-2.361465,-7.318518),
            doubleArrayOf(-6.020472,-0.006972,-6.324819,-2.865053,-4.32693),
            doubleArrayOf(-4.374941,-4.040138,-6.462153,-1.35583,-7.283475),
            doubleArrayOf(-6.126536,-7.84007,-5.86586,-4.738689,-0.783486),
            doubleArrayOf(-4.967397,-7.090153,-5.933146,-0.067181,-7.495291),
            doubleArrayOf(-3.038656,-4.982359,-2.713255,-5.292491,-2.469595),
            doubleArrayOf(-4.019356,-2.802229,-0.789,-3.347708,-6.862896),
            doubleArrayOf(-7.485012,-0.431589,-4.090665,-6.449168,-0.431645),
            doubleArrayOf(-3.368306,-2.168468,-0.952323,-5.714774,-5.146426),
            doubleArrayOf(-0.975388,-6.920194,-1.885638,-7.219068,-2.47855),
            doubleArrayOf(-2.382837,-0.400002,-1.252056,-3.971029,-6.418815),
            doubleArrayOf(-6.798721,-3.770238,-3.921673,-7.428633,-0.774235),
            doubleArrayOf(-3.940662,-2.389642,-6.24036,-6.04891,-7.904671),
            doubleArrayOf(-5.25415,-5.865262,-4.612785,-4.984592,-1.32547),
            doubleArrayOf(-0.868258,-6.597992,-4.829888,-6.670547,-2.694629),
            doubleArrayOf(-0.201236,-6.385534,-1.868202,-5.599642,-7.894259),
            doubleArrayOf(-1.885615,-5.265473,-6.638744,-4.515142,-6.146246),
            doubleArrayOf(-4.719192,-4.431573,-4.656308,-3.29193,-5.690046),
        )
        val result = CtcForcedAligner.align(rows, listOf(0, 1, 3), 2)
        assertTrue(result.feasible)
        assertEquals(-56.805922, result.totalLogProb, 1e-6)
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 4), result.spans.map { it!!.startFrame to it.endFrame })
        assertEquals(3, CtcForcedAligner.minimumFrames(listOf(1, 1)))
        assertFalse(CtcForcedAligner.align(arrayOf(doubleArrayOf(-1.0, -2.0)), listOf(1, 1)).feasible)
        assertEquals(listOf("no frames (T=0)"), CtcForcedAligner.align(emptyArray(), listOf(1)).reasons)
    }

    @Test fun chineseCifMatchesPublishedModelReference() {
        val alphas = floatArrayOf(.342894f,.611977f,.614587f,.604785f,.291464f,.0094f,.135569f,.595918f,.614486f,.608702f,.463789f,.106431f)
        val peaks = floatArrayOf(.342894f,.954871f,1.569458f,1.174343f,.465907f,.475306f,.610876f,1.206794f,.82138f,1.430082f,.89397f,1.000401f)
        assertEquals(listOf(10L to 30L, 30L to 110L, 110L to 150L, 150L to 240L),
            ZhCifPostprocess.tokens(alphas, peaks, 4).map { it.startMs to it.endMs })
        assertEquals(listOf(-10L to 60L), ZhCifPostprocess.tokens(
            floatArrayOf(.915081f,.62604f,.45888f), floatArrayOf(.915081f,1.54112f,1.0001f), 1,
        ).map { it.startMs to it.endMs })
    }

    @Test fun tokenizersHandleBothPublishedRepresentations() {
        assertEquals(listOf("<pad>", "A", "0"), AutoSyncModelManifest.parseTokens("""{"<pad>":0,"A":1,"0":2}"""))
        assertEquals(listOf("<pad>", "7", "中"), AutoSyncModelManifest.parseTokens("""{"0":"<pad>","1":"7","2":"中"}"""))
        assertEquals(listOf("<pad>", "中"), AutoSyncModelManifest.parseTokens("""["<pad>","中"]"""))
    }

    @Test fun verifiedPackValidationAndFailedUpdatePreserveInstalledBytes() = runBlocking<Unit> {
        val model = "fixture-model".toByteArray()
        val vocab = """{"<pad>":0,"A":1}""".toByteArray()
        val expected = mapOf("en-model.onnx" to model, "en-vocab.json" to vocab)
        var corrupt = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val name = chain.request().url.pathSegments.last()
            val data = checkNotNull(expected[name])
            val bytes = if (corrupt && name.endsWith(".onnx")) ByteArray(data.size) else data
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody()).build()
        }.build()
        val routing = MeloXGitHubRouting(client)
        val oldRoute = routing.selectedSource()
        routing.selectSource(MeloXGitHubSource.GitHubDoh)
        val root = Files.createTempDirectory("melox-autosync-test-").toFile()
        try {
            val index = AutoSyncModelRepository.parseIndex(index(expected).toString())
            val repo = AutoSyncModelRepository(root, routing)
            repo.install(index.packs.getValue("en"))
            val installed = requireNotNull(repo.installed("en"))
            assertTrue(installed.verify())
            assertEquals("fixture-model", installed.model.readText())
            corrupt = true
            assertFailsWith<IllegalArgumentException> { repo.install(index.packs.getValue("en")) }
            assertTrue(requireNotNull(repo.installed("en")).verify())
            assertEquals("fixture-model", installed.model.readText())
            assertTrue(root.listFiles()!!.none { it.name.contains("-install-") || it.name.contains("-backup-") })
            corrupt = false
            assertTrue(java.io.File(installed.directory, "en-vocab.json").delete())
            assertEquals(null, repo.installed("en"), "A missing tokenizer makes a pack unusable")
            repo.install(index.packs.getValue("en"))
            assertTrue(requireNotNull(repo.installed("en")).verify(), "Reinstall repairs a partially missing owned pack")
            val unrelated = java.io.File(installed.directory, "personal-notes.txt").apply { writeText("keep") }
            assertFailsWith<IllegalArgumentException> { repo.install(index.packs.getValue("en")) }
            assertEquals("keep", unrelated.readText(), "Updating must not discard unrelated files")
            assertEquals("fixture-model", installed.model.readText())
            assertTrue(java.io.File(installed.directory, "en-vocab.json").delete())
            assertTrue(repo.remove("en"))
            assertFalse(installed.model.exists())
            assertEquals("keep", unrelated.readText(), "Removing an app-owned model must leave unrelated files")
            val invalid = index(expected)
            invalid.getJSONObject("languages").getJSONObject("en").getJSONArray("files").getJSONObject(0)
                .put("download_url", "https://example.invalid/model.onnx")
            assertFailsWith<IllegalArgumentException> { AutoSyncModelRepository.parseIndex(invalid.toString()) }
        } finally {
            root.deleteRecursively() // This test created its isolated root.
            routing.selectSource(oldRoute)
        }
    }

    private fun index(files: Map<String, ByteArray>): JSONObject {
        fun spec(name: String, role: String, data: ByteArray) = JSONObject().put("name", name).put("role", role)
            .put("download_url", AutoSyncModelRepository.RELEASE_PREFIX + name).put("bytes", data.size)
            .put("sha256", MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) })
        val languages = JSONObject()
        for (language in listOf("en", "zh", "ja")) {
            val modelName = "$language-model.onnx"
            val tokenizerName = "$language-vocab.json"
            val entries = JSONArray().put(spec(modelName, "model", files[modelName] ?: byteArrayOf(1)))
                .put(spec(tokenizerName, "tokenizer", files[tokenizerName] ?: byteArrayOf(2)))
            languages.put(language, JSONObject().put("language", language).put("sample_rate", 16_000)
                .put("engine", if (language == "zh") "monotonic-aligner-cif" else "ctc")
                .put("input", JSONObject().put("name", "input_values"))
                .put("outputs", JSONArray().put(JSONObject().put("name", "log_probs")))
                .put("text", JSONObject().put("tokens_file", tokenizerName))
                .put("files", entries))
        }
        return JSONObject().put("release_tag", AutoSyncModelRepository.RELEASE_TAG)
            .put("min_backend_protocol", 1).put("languages", languages)
    }
}
