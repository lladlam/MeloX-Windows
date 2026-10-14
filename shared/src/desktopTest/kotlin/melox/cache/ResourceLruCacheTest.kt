package melox.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class ResourceLruCacheTest {
    private class Pixels(val bytes: Long = 4) { var closes = 0 }
    private fun cache() = MeloXResourceLruCache<String, Pixels>(2, 8, { it.bytes }, { it.closes++ })

    @Test fun evictionAndClearDoNotCloseVisiblePixelsUntilLastLeaseEnds() {
        val cache = cache()
        val pixels = Pixels()
        val first = cache.publishAndAcquire("visible", pixels)
        val second = cache.acquire("visible")!!
        cache.publishAndAcquire("b", Pixels()).close()
        cache.publishAndAcquire("c", Pixels()).close()
        assertNull(cache.acquire("visible"))
        assertEquals(0, pixels.closes)
        cache.clear()
        first.close()
        assertEquals(0, pixels.closes)
        second.close()
        assertEquals(1, pixels.closes)
        second.close()
        assertEquals(1, pixels.closes)
    }

    @Test fun duplicatePublicationDisposesOnlyUnusedDecodeAndReusesAccountKey() {
        val cache = cache()
        val pixels = Pixels()
        val first = cache.publishAndAcquire("provider-account-url", pixels)
        val duplicate = Pixels()
        val second = cache.publishAndAcquire("provider-account-url", duplicate)
        assertSame(first.value, second.value)
        assertEquals(1, duplicate.closes)
        first.close()
        second.close()
        assertEquals(0, pixels.closes)
        cache.clear()
        assertEquals(1, pixels.closes)
    }

    @Test fun oversizedUncachedImageClosesImmediatelyWhenItsUiReleasesIt() {
        val cache = cache()
        val huge = Pixels(80)
        val owner = cache.publishAndAcquire("huge", huge)
        assertNull(cache.acquire("huge"))
        assertEquals(0L, cache.stats().retainedWeight)
        assertEquals(0, huge.closes)
        owner.close()
        assertEquals(1, huge.closes)
    }

    @Test fun continuousNativeAllocationDisposesOldPixelsWithoutRelyingOnGc() {
        val cache = cache()
        val decoded = ArrayList<Pixels>()
        repeat(10_000) {
            val pixels = Pixels()
            decoded += pixels // Retain wrappers deliberately; GC cannot rescue this test.
            cache.publishAndAcquire("image-$it", pixels).close()
        }
        assertEquals(9998, decoded.count { it.closes == 1 })
        assertFalse(decoded.any { it.closes > 1 })
        cache.clear()
        assertEquals(10_000, decoded.count { it.closes == 1 })
    }
}
