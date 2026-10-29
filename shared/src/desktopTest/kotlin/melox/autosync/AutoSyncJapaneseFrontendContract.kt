package melox.autosync

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Run the actual JNA frontend against Android/Python golden readings. */
object AutoSyncJapaneseFrontendContract {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "Expected library, dictionary directory, and golden vector paths" }
        val report = System.getProperty("melox.autosync.ja.report")?.let(::File)
        report?.writeText("started", Charsets.UTF_8)
        val vectors = JSONObject(File(args[2]).readText(Charsets.UTF_8)).getJSONArray("vectors")
        report?.appendText("\nvectors loaded", Charsets.UTF_8)
        val library = if (args[0] == "bundled") AutoSyncJapaneseNative.library() else File(args[0])
        report?.appendText("\nlibrary extracted", Charsets.UTF_8)
        JaOpenJtalk(library, File(args[1])).use { frontend ->
            val mapper = JapaneseG2pMapper(frontend)
            for (i in 0 until vectors.length()) {
                val vector = vectors.getJSONObject(i)
                val text = vector.getString("text")
                val result = checkNotNull(mapper.map(text)) { "No mapping for $text" }
                check(result.reconstruct() == text && result.reconstruct() == vector.getString("reconstruct")) { "reconstruction: $text" }
                check(result.phonemes == vector.getJSONArray("phonemes").strings()) { "phonemes: $text" }
                val readings = vector.getJSONArray("readings")
                check(readings.length() == result.readings.size) { "reading count: $text" }
                for (j in 0 until readings.length()) {
                    val expected = readings.getJSONObject(j)
                    val actual = result.readings[j]
                    check(expected.getString("char") == actual.char && expected.getString("kana") == actual.kana &&
                        expected.getJSONArray("phonemes").strings() == actual.phonemes &&
                        expected.getBoolean("uncertain") == actual.uncertain) { "reading: $text[$j] $actual" }
                }
            }
            check(mapper.map("あ".repeat(257)) == null)
            check(mapper.map("あ\u0000い") == null)
            check(frontend.phonemes("a".repeat(4097)) == null)
        }
        val message = "PASS ${vectors.length()} Android/Python golden vectors through native OpenJTalk + JNA; bounded input"
        report?.appendText("\n$message\n", Charsets.UTF_8)
        println(message)
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
