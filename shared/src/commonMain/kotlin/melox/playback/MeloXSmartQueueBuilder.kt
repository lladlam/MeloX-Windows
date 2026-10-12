package melox.playback

/** Pinned Android candidates stay outside the visible queue and are consumed one match at a time. */
internal class MeloXSmartQueueBuilder {
    data class Summary(val bpm: Double, val confidence: Double)
    data class Snapshot(val generation: Long, val items: List<QueueEntry>)
    private val lock = Any()
    private var generation = 0L
    private val candidates = mutableListOf<QueueEntry>()
    private val consumed = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private val analysis = mutableMapOf<String, Summary>()
    fun begin(items: List<QueueEntry>, selectedIndex: Int): QueueEntry = synchronized(lock) {
        reset(); val selected = items[selectedIndex.coerceIn(items.indices)]
        consumed += selected.entryId; candidates += items.filterNot { it.entryId == selected.entryId }; selected
    }
    fun snapshot() = synchronized(lock) { Snapshot(generation, candidates.filterNot { it.entryId in consumed || it.entryId in failed }) }
    fun record(token: Long, entry: QueueEntry, result: MeloXAutoMixTrackAnalysis) = synchronized(lock) {
        if (token == generation) analysis[entry.entryId] = Summary(result.bpm, result.confidence)
    }
    fun failure(token: Long, entry: QueueEntry) = synchronized(lock) { if (token == generation) failed.add(entry.entryId) }
    fun best(token: Long, source: QueueEntry): QueueEntry? = synchronized(lock) {
        if (token != generation) return@synchronized null
        val current = analysis[source.entryId]
        if (current == null && source.entryId !in failed) return@synchronized null
        val remaining = candidates.filterNot { it.entryId in consumed || it.entryId in failed }
        val analyzed = remaining.mapNotNull { entry -> analysis[entry.entryId]?.let { entry to it } }
        analyzed.filter { (_, result) -> current != null && distance(current.bpm, result.bpm) <= 8 }
            .minByOrNull { (_, result) -> distance(current!!.bpm, result.bpm) + (1 - result.confidence) * 4 }?.first
            ?: if (analyzed.size == remaining.size) analyzed.maxByOrNull { it.second.confidence }?.first else null
    }
    fun consume(token: Long, entry: QueueEntry): QueueEntry? = synchronized(lock) {
        if (token != generation || !consumed.add(entry.entryId)) return@synchronized null
        val index = candidates.indexOfFirst { it.entryId == entry.entryId }
        if (index >= 0) candidates.removeAt(index) else null
    }
    fun emergency(token: Long): QueueEntry? = synchronized(lock) {
        if (token != generation) null else candidates.filterNot { it.entryId in consumed || it.entryId in failed }
            .maxByOrNull { analysis[it.entryId]?.confidence ?: -1.0 }
    }
    /** Analysis failure removes eligibility, not the user's unconsumed queue entry. */
    fun takeRemaining(): List<QueueEntry> = synchronized(lock) {
        val remaining = candidates.filterNot { it.entryId in consumed }
        reset()
        remaining
    }
    fun reset() = synchronized(lock) { generation++; candidates.clear(); consumed.clear(); failed.clear(); analysis.clear() }
    companion object { fun distance(a: Double, b: Double) = listOf(b / 2, b, b * 2).minOf { kotlin.math.abs(a - it) } }
}
