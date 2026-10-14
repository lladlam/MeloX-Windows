package melox.cache

/** Bounds retained references by both entry count and decoded weight, not compressed file size.
 * Eviction does not close values: a visible composable may still own the same image.
 */
class MeloXWeightedLruCache<K, V : Any>(
    private val maxEntries: Int,
    private val maxWeight: Long,
    private val onEvicted: (V) -> Unit = {},
    private val weightOf: (V) -> Long,
) {
    init { require(maxEntries > 0 && maxWeight > 0) }

    data class Stats(val entries: Int, val retainedWeight: Long, val maxWeight: Long)
    private data class Entry<V>(val value: V, val weight: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    private var retainedWeight = 0L

    @Synchronized
    fun get(key: K): V? = entries[key]?.value

    /** Concurrent decodes reuse the already-published image instead of retaining duplicates. */
    @Synchronized
    fun putIfAbsent(key: K, value: V): V {
        entries[key]?.let { return it.value }
        val weight = weightOf(value)
        require(weight >= 0)
        // Oversized images can be displayed, but must not evict the entire cache
        // or remain retained after their last visible owner goes away.
        if (weight > maxWeight) return value
        val iterator = entries.entries.iterator()
        while (entries.size >= maxEntries || retainedWeight > maxWeight - weight) {
            val eldest = iterator.next()
            retainedWeight -= eldest.value.weight
            iterator.remove()
            onEvicted(eldest.value.value)
        }
        entries[key] = Entry(value, weight)
        retainedWeight += weight
        return value
    }

    @Synchronized
    fun clear() {
        val removed = entries.values.toList()
        entries.clear()
        retainedWeight = 0
        removed.forEach { onEvicted(it.value) }
    }

    @Synchronized
    fun stats() = Stats(entries.size, retainedWeight, maxWeight)
}
