package melox.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WeightedLruCacheTest {
    private data class Image(val bytes: Long)

    @Test fun decodedByteBudgetEvictsLeastRecentlyUsedAndLeavesVisibleOwnerValid() {
        val cache = MeloXWeightedLruCache<String, Image>(100, 10) { it.bytes }
        val visible = Image(4)
        cache.putIfAbsent("a", visible)
        cache.putIfAbsent("b", Image(4))
        assertSame(visible, cache.get("a")) // a is newer than b now.
        cache.putIfAbsent("c", Image(4))
        assertNull(cache.get("b"))
        assertSame(visible, cache.get("a"))
        assertEquals(4L, visible.bytes) // Eviction must not dispose a UI-owned image.
        assertEquals(8L, cache.stats().retainedWeight)
    }

    @Test fun thousandsOfDistinctSmallImagesRespectCountAndByteLimits() {
        val cache = MeloXWeightedLruCache<Int, Image>(32, 1000) { it.bytes }
        repeat(10_000) {
            cache.putIfAbsent(it, Image(1))
            assertEquals(minOf(it + 1, 32), cache.stats().entries)
        }
        assertNull(cache.get(0))
        assertEquals(32L, cache.stats().retainedWeight)
        cache.clear()
        assertEquals(0, cache.stats().entries)
        assertEquals(0L, cache.stats().retainedWeight)
    }

    @Test fun duplicateAndOversizedDecodesDoNotExpandRetentionOrFlushUsefulImages() {
        val cache = MeloXWeightedLruCache<String, Image>(10, 8) { it.bytes }
        val original = Image(4)
        cache.putIfAbsent("same-provider-account-url", original)
        assertSame(original, cache.putIfAbsent("same-provider-account-url", Image(4)))
        val large = Image(80)
        assertSame(large, cache.putIfAbsent("large", large))
        assertNull(cache.get("large"))
        assertSame(original, cache.get("same-provider-account-url"))
        assertEquals(4L, cache.stats().retainedWeight)
        cache.putIfAbsent("other-account-same-url", Image(4))
        assertEquals(2, cache.stats().entries)
    }
}
