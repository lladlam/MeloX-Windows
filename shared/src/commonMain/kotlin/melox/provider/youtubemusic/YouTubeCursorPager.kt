package melox.provider.youtubemusic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import melox.music.model.MusicPage

/** Numbered UI pages over opaque cursors; null identities represent distinct, unkeyed entries. */
internal class YouTubeCursorPager<T>(private val identity: (T) -> String?) {
    data class Batch<T>(val items: List<T>, val continuation: String?)
    private class Entry<T>(val items: MutableList<T>, var cursor: String?) {
        val visited = mutableSetOf<String>()
    }
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, Entry<T>>()

    suspend fun page(key: String, page: Int, pageSize: Int,
                     first: suspend () -> Batch<T>, next: suspend (String) -> Batch<T>): MusicPage<T> = mutex.withLock {
        require(page in 1..100 && pageSize in 1..1000)
        val entry = if (page == 1 || key !in entries) {
            val batch = first()
            val known = hashSetOf<String>()
            Entry(batch.items.filter { item -> identity(item)?.let(known::add) ?: true }.toMutableList(), batch.continuation).also {
                entries.remove(key)
                entries[key] = it
                if (entries.size > 24) entries.remove(entries.keys.first())
            }
        } else entries.getValue(key)
        val offset = (page - 1) * pageSize
        val end = offset + pageSize
        while (entry.items.size < end) {
            val cursor = entry.cursor ?: break
            check(cursor !in entry.visited && entry.visited.size < 100) { "YouTube Music 重复返回分页游标，结果尚未完整读取" }
            val batch = next(cursor)
            entry.visited += cursor
            val known = entry.items.mapNotNullTo(hashSetOf(), identity)
            entry.items += batch.items.filter { item -> identity(item)?.let(known::add) ?: true }
            entry.cursor = batch.continuation
        }
        MusicPage(entry.items.drop(offset).take(pageSize), page, pageSize,
            entry.items.size.toLong().takeIf { entry.cursor == null }, entry.items.size > end || entry.cursor != null)
    }
}
