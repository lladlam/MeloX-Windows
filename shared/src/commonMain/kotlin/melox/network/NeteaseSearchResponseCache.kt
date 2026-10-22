package melox.network

import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Bounded, memory-only search cache; never caches errors, credentials or mutations. */
internal class NeteaseSearchResponseCache(
    private val ttlMs: Long = 120_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Entry(val at: Long, val json: String)
    private data class Key(val transport: Any?, val digest: String)
    private val entries = LinkedHashMap<Key, Entry>(64, .75f, true)
    private val locks = List(16) { ReentrantLock() }

    fun get(uri: String, data: JSONObject, cookie: String, owner: Job?, transport: Any? = null, load: () -> JSONObject): JSONObject {
        if (uri != "/api/search/get" && uri != "/api/search/suggest/keyword") return load()
        val params = data.keys().asSequence().sorted().joinToString { "$it=${data.get(it)}" }
        val key = Key(transport, MessageDigest.getInstance("SHA-256")
            .digest("$cookie\u0000$uri\u0000$params".toByteArray()).joinToString("") { "%02x".format(it) })
        val lock = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]
        while (true) {
            owner?.ensureActive()
            if (lock.tryLock(50, TimeUnit.MILLISECONDS)) break
        }
        try {
            owner?.ensureActive()
            synchronized(entries) {
                entries[key]?.takeIf { now() - it.at < ttlMs }?.let { return JSONObject(it.json) }
            }
            val result = load()
            owner?.ensureActive()
            if (result.optInt("code", 200) in 200..299) synchronized(entries) {
                entries[key] = Entry(now(), result.toString())
                while (entries.size > 64) entries.remove(entries.keys.first())
            }
            return result
        } finally { lock.unlock() }
    }

    companion object { val shared = NeteaseSearchResponseCache() }
}
