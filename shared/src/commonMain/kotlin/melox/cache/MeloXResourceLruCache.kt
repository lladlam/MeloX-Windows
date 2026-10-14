package melox.cache

import java.util.concurrent.atomic.AtomicBoolean

/** Native-resource LRU. Cache and each visible consumer own separate references.
 * Last release disposes immediately, even when the JVM heap is too small to
 * trigger GC in response to a large native pixel allocation.
 */
class MeloXResourceLruCache<K, V : Any>(
    maxEntries: Int,
    maxWeight: Long,
    weightOf: (V) -> Long,
    private val dispose: (V) -> Unit,
) {
    class Lease<V> internal constructor(val value: V, private val release: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }

    private class Resource<V>(val value: V, var references: Int = 1)
    private val cache = MeloXWeightedLruCache<K, Resource<V>>(maxEntries, maxWeight,
        { release(it) }, { weightOf(it.value) })

    @Synchronized
    private fun release(resource: Resource<V>) {
        check(resource.references > 0)
        resource.references--
        if (resource.references == 0) dispose(resource.value)
    }

    private fun lease(resource: Resource<V>) = Lease(resource.value) { release(resource) }

    @Synchronized
    fun acquire(key: K): Lease<V>? = cache.get(key)?.let {
        it.references++
        lease(it)
    }

    /** Takes ownership of [value] only on success, including duplicate disposal. */
    @Synchronized
    fun publishAndAcquire(key: K, value: V): Lease<V> {
        cache.get(key)?.let {
            if (it.value !== value) dispose(value)
            it.references++
            return lease(it)
        }
        val resource = Resource(value)
        cache.putIfAbsent(key, resource)
        if (cache.get(key) === resource) resource.references++ // Cache + new visible owner.
        // An oversized uncached resource has only the visible owner's reference.
        return lease(resource)
    }

    @Synchronized
    fun clear() = cache.clear()

    @Synchronized
    fun stats() = cache.stats()
}
