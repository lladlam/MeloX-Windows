package melox.library

/** Resolves a library removal against a freshly fetched account playlist list. */
enum class NeteasePlaylistRemoval { Delete, Unsubscribe }

fun neteasePlaylistRemoval(
    playlists: List<NeteasePlaylistSummary>,
    userId: Long,
    playlistId: Long,
): NeteasePlaylistRemoval {
    require(userId > 0 && playlistId > 0) { "账号或歌单 ID 无效" }
    val liked = playlists.firstOrNull() ?: error("无法确认系统歌单，请刷新后重试")
    check(liked.id > 0 && liked.creatorUserId == userId) { "无法确认系统歌单，请刷新后重试" }
    require(liked.id != playlistId) { "「我喜欢的音乐」是系统歌单，无法删除" }
    val target = playlists.singleOrNull { it.id == playlistId }
        ?: error("歌单不在当前资料库中，请刷新后重试")
    val creator = target.creatorUserId ?: error("无法确认歌单所有者，请刷新后重试")
    return if (creator == userId) NeteasePlaylistRemoval.Delete else NeteasePlaylistRemoval.Unsubscribe
}

/** Re-read ownership immediately before routing a removal; never infer it from a displayed card. */
suspend fun removeNeteaseLibraryPlaylist(
    userId: Long,
    playlistId: Long,
    loadPlaylists: suspend (Long) -> List<NeteasePlaylistSummary>,
    checkSession: () -> Unit,
    delete: suspend (Long) -> Unit,
    unsubscribe: suspend (Long) -> Unit,
) {
    checkSession()
    val action = neteasePlaylistRemoval(loadPlaylists(userId), userId, playlistId)
    checkSession()
    when (action) {
        NeteasePlaylistRemoval.Delete -> delete(playlistId)
        NeteasePlaylistRemoval.Unsubscribe -> unsubscribe(playlistId)
    }
}
