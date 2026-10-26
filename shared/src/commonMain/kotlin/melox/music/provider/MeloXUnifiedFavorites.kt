package melox.music.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.model.SearchSong
import melox.music.model.*
import melox.network.NeteaseSearchClient
import melox.platform.MeloXPreferences
import melox.platform.getPreferences
import melox.provider.local.LocalMusicRepository
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** App-owned likes never write to a source account. All mutations commit before notifying UI. */
open class UnifiedFavoritesStore(private val prefs: MeloXPreferences) {
    private val lock = Any()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private fun state() = prefs.getString("state", null)?.let(::JSONObject) ?: JSONObject()
    fun enabled(): Boolean = synchronized(lock) { state().let { it.optBoolean("initialized") && it.optBoolean("enabled") } }
    fun initialized(): Boolean = synchronized(lock) { state().optBoolean("initialized") }
    private fun decode(state: JSONObject): List<MusicTrack> {
        val array = state.optJSONArray("tracks") ?: return emptyList()
        return (0 until array.length()).map { index ->
            requireNotNull(MeloXTrackJson.decode(array.getJSONObject(index))) { "本地收藏包含无法读取的曲目，原始记录已保留" }
        }
    }
    fun tracks(): List<MusicTrack> = synchronized(lock) { decode(state()) }
    fun contains(track: MusicTrack): Boolean = tracks().any { sameSong(it, track) }
    private fun save(value: JSONObject) {
        prefs.putString("state", value.toString())
        mutableRevision.value++
    }
    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        val state = state()
        check(!enabled || state.optBoolean("initialized")) { "请先初始化本地收藏" }
        save(state.put("enabled", enabled))
    }
    fun set(track: MusicTrack, favorite: Boolean) = synchronized(lock) {
        val state = state(); check(state.optBoolean("initialized")) { "请先初始化本地收藏" }
        val current = decode(state).toMutableList()
        val index = current.indexOfFirst { sameSong(it, track) }
        if (!favorite) current.removeAll { sameSong(it, track) }
        else if (index < 0) current.add(track)
        else {
            val existing = current[index]
            current[index] = if (track.id.source != existing.id.source && qualityRank(track) < qualityRank(existing)) {
                existing.copy(title = track.title, artists = track.artists, album = track.album ?: existing.album,
                    artworkUrl = track.artworkUrl ?: existing.artworkUrl)
            } else track
        }
        save(state.put("tracks", JSONArray(current.distinctBy { it.id }.map(MeloXTrackJson::encode))))
    }
    fun reorder(track: MusicTrack, newIndex: Int) = synchronized(lock) {
        val state = state(); val tracks = decode(state).toMutableList()
        val index = tracks.indexOfFirst { sameSong(it, track) }
        if (index < 0) return@synchronized
        val moved = tracks.removeAt(index); tracks.add(newIndex.coerceIn(0, tracks.size), moved)
        save(state.put("tracks", JSONArray(tracks.map(MeloXTrackJson::encode))))
    }
    data class Failure(val source: MusicSource, val message: String)
    data class Report(val total: Int, val failures: List<Failure>)

    suspend fun initialize(sources: Set<MusicSource>, loader: suspend (MusicSource) -> List<MusicTrack>): Report = withContext(Dispatchers.IO) {
        val imported = mutableListOf<MusicTrack>(); val failures = mutableListOf<Failure>()
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            try {
                val loaded = loader(source)
                check(loaded.all { it.id.source == source }) { "来源返回了不匹配的曲目身份" }
                imported += loaded
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { failures += Failure(source, failure.message ?: "收藏读取失败") }
        }
        currentCoroutineContext().ensureActive()
        synchronized(lock) {
            // Read again after network IO, so simultaneous local adds are not overwritten.
            val state = state()
            val merged = TrackAggregation.aggregate(decode(state) + imported).mapNotNull { it.recommendation?.track }
            save(state.put("tracks", JSONArray(merged.map(MeloXTrackJson::encode))).put("initialized", true).put("enabled", true))
            Report(merged.size, failures)
        }
    }
    private fun qualityRank(track: MusicTrack) = (track.bestQuality ?: audioQualityForBitrate(track.bestBitrate) ?: AudioQualityTier.Standard).rank()
    private fun sameSong(a: MusicTrack, b: MusicTrack): Boolean = a.id == b.id ||
        (TrackAggregation.keyOf(a) == TrackAggregation.keyOf(b) &&
            (a.durationMs == null || b.durationMs == null || a.durationMs <= 0 || b.durationMs <= 0 || abs(a.durationMs - b.durationMs) <= 10_000))
}

object MeloXUnifiedFavorites : UnifiedFavoritesStore(getPreferences("melox_unified_favorites")) {
    const val PlaylistId = "melox_unified_favorites"
    fun fromSong(song: SearchSong): MusicTrack = song.providerTrack ?: MusicTrack(
        MusicResourceId(MusicSource.Netease, requireNotNull(song.neteaseSongId).toString()), song.name,
        song.artists.split(" / ").filter(String::isNotBlank).map { MusicArtistRef(name = it) },
        song.album.takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it, artworkUrl = song.artworkUrl) },
        song.artworkUrl, song.durationMs.takeIf { it > 0 }, TrackAvailability.Playable, ProviderTrackMetadata.Netease(song.id),
        song.bestQuality, song.bestBitrate,
    )

    suspend fun initialize(sources: Set<MusicSource>): UnifiedFavoritesStore.Report = initialize(sources, ::loadFavorites)

    private suspend fun loadFavorites(source: MusicSource): List<MusicTrack> {
        if (source == MusicSource.Netease) {
            val cookie = NeteaseSessionStore.readCookie()
            check(NeteaseSessionStore.containsMusicU(cookie)) { "请先登录网易云音乐" }
            val user = NeteaseSearchClient(cookieProvider = { cookie }).accountProfile().userId
            val client = NeteaseLibraryClient(cookieProvider = { cookie })
            val ids = client.likedSongIdsBlocking(user).distinct()
            val tracks = ids.chunked(100).flatMap { client.songDetailsBlocking(it) }.map(::fromSong)
            check(tracks.map { it.id.value.toLongOrNull() }.toSet().containsAll(ids)) { "部分收藏歌曲详情未返回，请稍后重试导入" }
            check(cookie == NeteaseSessionStore.readCookie()) { "账号已切换，请重新导入" }
            return tracks
        }
        if (source == MusicSource.Local) {
            val keys = LocalMusicRepository().tracks().filter { it.isFavorite }.map { it.fileKey }.toSet()
            val provider = MeloXMusicProviders.create().require(source) as LocalAggregationCapability
            return allPages { page -> provider.aggregationTracks(page, 100) }.filter { it.id.value in keys }
        }
        val provider = MeloXMusicProviders.create().require(source)
        val library = provider as? UserLibraryCapability ?: error("此音源不提供个人音乐库")
        val playlists = provider as? PlaylistCapability ?: error("此音源不提供歌单读取")
        val account = library.accountSummary() ?: error("请先登录 ${source.displayName}")
        val liked = allPages { page -> library.userPlaylists(page, 100) }.firstOrNull {
            it.title.trim().lowercase() in setOf("我喜欢", "我喜欢的音乐", "喜欢的音乐", "我的喜欢", "我的收藏", "liked songs", "liked", "favorites", "favourites", "my favorites", "favorite songs")
        } ?: error("未找到我喜欢或 Liked songs 歌单")
        val tracks = allPages { page ->
            val detail = playlists.playlistDetail(liked, page, 100)
            val total = detail.total ?: detail.summary.trackCount?.toLong()
            MusicPage(detail.tracks, page, 100, total, detail.hasMore ?: (total?.let { page * 100L < it } ?: (detail.tracks.size >= 100)))
        }
        check(library.accountSummary() == account) { "账号已切换，请重新导入" }
        return tracks
    }

    private suspend fun <T> allPages(load: suspend (Int) -> MusicPage<T>): List<T> {
        val result = mutableListOf<T>(); val signatures = mutableSetOf<List<T>>()
        for (page in 1..1_000) {
            currentCoroutineContext().ensureActive()
            val next = load(page)
            check(next.items.isEmpty() || signatures.add(next.items)) { "音源重复返回同一页，收藏未完整导入" }
            result += next.items
            if (!next.hasMore) {
                check(next.total == null || result.size >= next.total) { "收藏尚未完整读取" }
                return result
            }
            check(next.items.isNotEmpty()) { "音源提前返回空页，收藏未完整导入" }
        }
        error("收藏页数超出读取范围")
    }
}
