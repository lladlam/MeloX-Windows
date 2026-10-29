package melox.autosync

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import melox.lyrics.LyricQuality
import melox.lyrics.LyricSyllable
import melox.lyrics.LyricTimingKind
import melox.lyrics.LyricsDocument
import melox.platform.meloXDataDir
import org.json.JSONArray
import org.json.JSONObject

/** Private generated timelines are valid only for the exact source/audio/models. */
internal class AutoSyncTimelineStore(private val root: File = File(meloXDataDir(), "autosync/timelines")) {
    fun load(track: String, source: String, audio: String, models: String): Map<Int, List<AutoSyncUnit>>? = runCatching {
        val json = JSONObject(File(root, "${hash(track)}.json").readText(Charsets.UTF_8))
        require(json.getInt("version") == 1 && json.getString("source") == source &&
            json.getString("audio") == audio && json.getString("models") == models)
        val lines = json.getJSONArray("lines")
        require(lines.length() in 1..5_000)
        buildMap {
            for (i in 0 until lines.length()) {
                val line = lines.getJSONObject(i)
                val index = line.getInt("index")
                val raw = line.getJSONArray("units")
                require(index >= 0 && raw.length() in 1..4_096 && index !in this)
                val units = (0 until raw.length()).map { n -> raw.getJSONObject(n).let { item ->
                    AutoSyncUnit(item.getString("text"), item.getLong("start"), item.getLong("end"))
                } }
                require(units.all { it.text.isNotEmpty() && it.startMs >= 0 && it.endMs > it.startMs } &&
                    units.zipWithNext().all { it.first.endMs <= it.second.startMs })
                put(index, units)
            }
        }
    }.getOrNull()

    fun save(track: String, source: String, audio: String, models: String, lines: Map<Int, List<AutoSyncUnit>>) {
        require(lines.isNotEmpty())
        check(root.isDirectory || root.mkdirs()) { "Cannot create AutoSync timeline directory" }
        val target = File(root, "${hash(track)}.json")
        val staging = File.createTempFile(".timeline-", ".tmp", root)
        try {
            val values = JSONArray()
            lines.toSortedMap().forEach { (index, units) ->
                val array = JSONArray()
                units.forEach { array.put(JSONObject().put("text", it.text).put("start", it.startMs).put("end", it.endMs)) }
                values.put(JSONObject().put("index", index).put("units", array))
            }
            staging.writeText(JSONObject().put("version", 1).put("source", source).put("audio", audio)
                .put("models", models).put("lines", values).toString(), Charsets.UTF_8)
            try { Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { staging.delete() }
    }

    companion object {
        fun sourceHash(document: LyricsDocument): String {
            val lines = JSONArray()
            document.lines.forEach { line -> lines.put(JSONObject().put("time", line.timeMs).put("duration", line.durationMs)
                .put("text", line.text)) }
            return hash(lines.toString())
        }

        fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        fun merge(document: LyricsDocument, timings: Map<Int, List<AutoSyncUnit>>): LyricsDocument? {
            if (document.lines.any { it.syllables.isNotEmpty() }) return null
            var changed = false
            val lines = document.lines.mapIndexed { index, line ->
                val units = timings[index]?.takeIf { it.isNotEmpty() } ?: return@mapIndexed line
                changed = true
                line.copy(syllables = units.map { LyricSyllable(it.text, it.startMs, it.endMs) }, timingKind = LyricTimingKind.Precise)
            }
            return if (changed) document.copy(lines = lines, quality = LyricQuality.WordSynchronized, pseudoTimingAllowed = false) else null
        }
    }
}
