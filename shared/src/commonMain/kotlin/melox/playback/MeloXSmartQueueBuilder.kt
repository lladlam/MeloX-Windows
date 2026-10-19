package melox.playback

/** Android v0.6.5 candidate bookkeeping; the player retains the complete visible timeline. */
internal class MeloXSmartQueueBuilder {
    data class Summary(val bpm: Double, val confidence: Double)
    data class Snapshot(val generation: Long, val items: List<QueueEntry>, val futureItems: List<QueueEntry>)
    private val lock = Any()
    private var generation = 0L
    private val candidates = mutableListOf<QueueEntry>()
    private val consumed = linkedSetOf<String>()
    private val futureIds = mutableSetOf<String>()
    private var analysisTargets: Set<String>? = null
    private val failed = mutableSetOf<String>()
    private val analysis = mutableMapOf<String, Summary>()
    fun begin(items: List<QueueEntry>, selectedIndex: Int): QueueEntry = synchronized(lock) {
        reset(); val index = selectedIndex.coerceIn(items.indices); val selected = items[index]
        futureIds += items.drop(index + 1).map { it.entryId }
        consumed += selected.entryId; candidates += items.filterNot { it.entryId == selected.entryId }; selected
    }
    fun snapshot() = synchronized(lock) {
        val remaining = candidates.filterNot { it.entryId in consumed || it.entryId in failed }
        Snapshot(generation, remaining, remaining.filter { it.entryId in futureIds })
    }
    fun tailEntryId(): String? = synchronized(lock) { consumed.lastOrNull() }
    fun analyzedCount(): Int = synchronized(lock) { analysis.size }
    fun setAnalysisTargets(token: Long, entries: Collection<QueueEntry>) = synchronized(lock) {
        if (token == generation) analysisTargets = entries.mapTo(mutableSetOf()) { it.entryId }
    }
    fun record(token: Long, entry: QueueEntry, result: MeloXAutoMixTrackAnalysis) = synchronized(lock) {
        if (token == generation) analysis[entry.entryId] = Summary(result.bpm, result.confidence)
    }
    fun failure(token: Long, entry: QueueEntry) = synchronized(lock) { if (token == generation) failed.add(entry.entryId) }
    fun best(token: Long, source: QueueEntry, allowedIds: Set<String>? = null): QueueEntry? = synchronized(lock) {
        if (token != generation) return@synchronized null
        val current = analysis[source.entryId]
        if (current == null && source.entryId !in failed) return@synchronized null
        val remaining = candidates.filter {
            it.entryId !in consumed && it.entryId !in failed && it.entryId in futureIds &&
                (analysisTargets?.contains(it.entryId) != false) && (allowedIds?.contains(it.entryId) != false)
        }
        val analyzed = remaining.mapNotNull { entry -> analysis[entry.entryId]?.let { entry to it } }
        analyzed.filter { (_, result) -> current != null && distance(current.bpm, result.bpm) <= 8 }
            .minByOrNull { (_, result) -> distance(current!!.bpm, result.bpm) + (1 - result.confidence) * 4 }?.first
            ?: if (analyzed.size == remaining.size) analyzed.maxByOrNull { it.second.confidence }?.first else null
    }
    fun consume(token: Long, entry: QueueEntry): QueueEntry? = synchronized(lock) {
        if (token != generation || entry.entryId in consumed) return@synchronized null
        val index = candidates.indexOfFirst { it.entryId == entry.entryId }
        if (index >= 0) { consumed += entry.entryId; candidates.removeAt(index) } else null
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
    fun reset() = synchronized(lock) {
        generation++; candidates.clear(); consumed.clear(); failed.clear(); analysis.clear()
        futureIds.clear(); analysisTargets = null
    }
    companion object { fun distance(a: Double, b: Double) = listOf(b / 2, b, b * 2).minOf { kotlin.math.abs(a - it) } }
}
