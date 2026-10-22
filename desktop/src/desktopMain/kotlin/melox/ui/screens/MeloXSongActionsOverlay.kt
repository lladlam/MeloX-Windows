package melox.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import melox.ui.input.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.audio.MusicQualityPreferences
import melox.download.MeloXDownloadPlaylistRef
import melox.download.MeloXDownloadStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.*
import melox.music.provider.*
import melox.network.*
import melox.playback.MeloXSleepTimer
import melox.playback.PlaybackCommands
import melox.ui.foundation.*
import melox.ui.navigation.MeloXBackHandler
import melox.ui.navigation.songShareUrl
import melox.ui.legal.pinnedString
import melox.ui.theme.MeloXColors

private enum class SongActionPage(val title: String) {
    Main("歌曲操作"), Sleep("定时关闭"), AddToPlaylist("添加到歌单"), CreatePlaylist("创建歌单"),
    Comments("评论"), CommentReplies("评论回复"), ShareContacts("发送给网易云好友"),
    ListeningRank("我的听歌排行"), Wiki("歌曲百科"), ListenTogether("一起听"),
}
private val LocalSongActionGrouped = staticCompositionLocalOf { false }

@Composable
fun MeloXSongActionsOverlay(
    song: SearchSong,
    queue: List<SearchSong>,
    visible: Boolean,
    onDismiss: () -> Unit,
    sourcePlaylist: MeloXDownloadPlaylistRef? = null,
    sourceOwnedPlaylistId: Long? = null,
    sourceProviderPlaylist: MusicPlaylistSummary? = null,
    onSourcePlaylistChanged: (() -> Unit)? = null,
    fromPlayer: Boolean = false,
    desktopAnchor: androidx.compose.ui.unit.IntOffset? = null,
) {
    if (!visible) return
    if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) {
        melox.ui.desktop.MeloXDesktopSongMenu(song, onDismiss, desktopAnchor, sourcePlaylist,
            sourceOwnedPlaylistId, sourceProviderPlaylist, onSourcePlaylistChanged)
        return
    }
    // A different native identity owns different jobs/state, even when legacy ids collide.
    key(song) {
        SongActionsContent(song, queue, onDismiss, sourcePlaylist, sourceOwnedPlaylistId,
            sourceProviderPlaylist, onSourcePlaylistChanged, fromPlayer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongActionsContent(
    song: SearchSong, queue: List<SearchSong>, onDismiss: () -> Unit,
    sourcePlaylist: MeloXDownloadPlaylistRef?, sourceOwnedPlaylistId: Long?,
    sourceProviderPlaylist: MusicPlaylistSummary?, onSourcePlaylistChanged: (() -> Unit)?,
    fromPlayer: Boolean,
) {
    val scope = rememberCoroutineScope()
    val cookie = remember { { NeteaseSessionStore.readCookie() } }
    val ops = remember { NeteaseMusicOperationsClient(cookie) }
    val social = remember { NeteaseSocialExtrasClient(cookie) }
    val library = remember { NeteaseLibraryClient(cookie) }
    val account = remember { NeteaseSearchClient(cookieProvider = cookie) }
    val downloads = remember { MeloXDownloadStore.instance() }
    val track = song.providerTrack
    val neteaseId = song.neteaseSongId
    val provider = remember(track?.id?.source) { track?.let { MeloXMusicProviders.create()[it.id.source] } }
    val playlistWriter = provider as? PlaylistWriteCapability
    val playlistSync = provider as? PlaylistSyncCapability
    val favoriteReader = provider as? FavoriteStateCapability
    val favoriteWriter = provider as? FavoriteCapability
    val canFavorite = neteaseId != null || (favoriteReader != null && favoriteWriter != null)
    var page by remember { mutableStateOf(SongActionPage.Main) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var favoriteLoading by remember { mutableStateOf(false) }
    var favoriteError by remember { mutableStateOf<String?>(null) }
    var owned by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var providerPlaylists by remember { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var playlistPage by remember { mutableIntStateOf(0) }
    var morePlaylists by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var privatePlaylist by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<MeloXCommentsPage?>(null) }
    var selectedComment by remember { mutableStateOf<MeloXMusicComment?>(null) }
    var replies by remember { mutableStateOf<MeloXCommentRepliesPage?>(null) }
    var contacts by remember { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
    var period by remember { mutableStateOf(MeloXUserPlayRecordPeriod.Week) }
    var records by remember { mutableStateOf<List<MeloXUserPlayRecord>>(emptyList()) }
    var wiki by remember { mutableStateOf<List<MeloXWikiSection>>(emptyList()) }
    var invitation by remember { mutableStateOf("") }
    val together by melox.playback.MeloXListenTogetherCoordinator.state.collectAsState()
    val sleepEnd by MeloXSleepTimer.endEpochMs.collectAsState()
    val sheetFocus = remember { FocusRequester() }
    LaunchedEffect(page) { sheetFocus.requestFocus() }

    fun back() {
        message = null
        page = when (page) {
            SongActionPage.Main -> { onDismiss(); SongActionPage.Main }
            SongActionPage.CommentReplies -> SongActionPage.Comments
            SongActionPage.CreatePlaylist -> SongActionPage.AddToPlaylist
            else -> SongActionPage.Main
        }
    }
    MeloXBackHandler(enabled = true, priority = 100, onBack = ::back)

    fun launchOperation(fallback: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true // Set before launch: two pointer events cannot start duplicate writes.
        message = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: fallback }
            finally { busy = false }
        }
    }

    suspend fun loadFavorite() {
        if (!canFavorite) return
        favoriteLoading = true
        favoriteError = null
        try {
            liked = if (neteaseId != null) {
                val profile = account.accountProfile()
                withContext(Dispatchers.IO) { neteaseId in library.likedSongIdsBlocking(profile.userId) }
            } else requireNotNull(favoriteReader).isFavorite(requireNotNull(track))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { favoriteError = failure.message ?: "喜爱状态读取失败" }
        finally { favoriteLoading = false }
    }
    LaunchedEffect(neteaseId, track?.id) { loadFavorite() }

    fun loadPlaylists(append: Boolean = false) = launchOperation("歌单读取失败") {
        if (neteaseId != null) {
            val profile = account.accountProfile()
            owned = withContext(Dispatchers.IO) {
                library.userPlaylistsBlocking(profile.userId).filter { it.creatorUserId == profile.userId }
            }
        } else {
            val next = if (append) playlistPage + 1 else 1
            val loaded = requireNotNull(playlistWriter).writablePlaylists(next, 50)
            providerPlaylists = ((if (append) providerPlaylists else emptyList()) + loaded.items).distinctBy { it.id }
            playlistPage = next
            morePlaylists = loaded.hasMore
        }
    }
    fun loadComments(append: Boolean = false) = launchOperation("评论加载失败") {
        val previous = comments
        val loaded = social.songComments(requireNotNull(neteaseId),
            if (append) previous?.nextOffset ?: 0 else 0,
            if (append) previous?.beforeTime ?: 0 else 0)
        comments = if (append && previous != null) loaded.copy(
            hotComments = previous.hotComments,
            comments = (previous.comments + loaded.comments).distinctBy { it.id },
        ) else loaded
    }
    fun loadReplies(parent: MeloXMusicComment, append: Boolean = false) = launchOperation("评论回复加载失败") {
        val previous = replies
        val loaded = social.songCommentReplies(requireNotNull(neteaseId), parent.id,
            if (append) previous?.nextTime ?: -1L else -1L)
        replies = if (append && previous != null) loaded.copy(
            ownerComment = loaded.ownerComment ?: previous.ownerComment,
            replies = (previous.replies + loaded.replies).distinctBy { it.id },
            totalCount = maxOf(previous.totalCount, loaded.totalCount),
        ) else loaded
    }
    fun loadRecords(selected: MeloXUserPlayRecordPeriod) = launchOperation("听歌排行加载失败") {
        period = selected
        records = social.userPlayRecords(account.accountProfile().userId, selected)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        modifier = Modifier.testTag("song-actions").focusRequester(sheetFocus).onPreviewKeyEvent { event ->
            if (event.key == Key.Escape) {
                if (event.type == KeyEventType.KeyUp) back()
                true
            } else false
        }.focusable().semantics {
            stateDescription = "${page.name}-${if (busy) "busy" else "idle"}"
        },
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(58.dp, 4.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .24f), RoundedCornerShape(99.dp)))
            }
        },
    ) {
        AnimatedContent(page, transitionSpec = {
            (fadeIn(MeloXMotion.panelEnterSpec()) + scaleIn(MeloXMotion.panelEnterSpec(), initialScale = .96f)) togetherWith
                (fadeOut(MeloXMotion.panelExitSpec()) + scaleOut(MeloXMotion.panelExitSpec(), targetScale = .98f))
        }, modifier = Modifier.fillMaxWidth(), label = "song-action-page") { target ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text(target.title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(song.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
                message?.let { Text(it, color = Color(0xFFFF8A90), fontSize = 12.sp,
                    modifier = Modifier.testTag("song-actions-error").padding(bottom = 6.dp)) }
                when (target) {
                    SongActionPage.Main -> {
                        BilibiliOffset(song)
                        Text("更多操作", Modifier.padding(bottom = 10.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (fromPlayer) SongActionRow("一起听", MeloXSymbol.Message, neteaseId != null,
                            subtitle = if (neteaseId == null) "一起听仅支持网易云音乐原生曲目" else null) { page = SongActionPage.ListenTogether }
                        if (sourceOwnedPlaylistId != null && neteaseId != null) {
                            SongActionRow("从当前歌单移除", MeloXSymbol.Trash, !busy) {
                                launchOperation("移除歌曲失败") {
                                    ops.removeSongFromPlaylist(neteaseId, sourceOwnedPlaylistId)
                                    onSourcePlaylistChanged?.invoke(); onDismiss()
                                }
                            }
                        } else if (track != null && sourceProviderPlaylist != null && playlistSync != null) {
                            SongActionRow("从当前歌单移除", MeloXSymbol.Trash, !busy) {
                                launchOperation("移除歌曲失败") {
                                    playlistSync.removeTrackFromPlaylist(track, sourceProviderPlaylist)
                                    onSourcePlaylistChanged?.invoke(); onDismiss()
                                }
                            }
                        }
                        if (song.album.isNotBlank()) SongActionRow("前往专辑：${song.album}", MeloXSymbol.ListBullet) {
                            val id = (song.albumId ?: track?.album?.id?.value?.toLongOrNull())?.takeIf { neteaseId != null && it > 0L }
                            onDismiss()
                            if (id != null) melox.ui.navigation.MeloXCollectionLaunchBus.open(melox.ui.navigation.MeloXCollectionTarget.Album(id, song.album))
                            else melox.ui.navigation.MeloXSearchLaunchBus.post(song.album, MeloXSearchKind.Albums, track?.id?.source ?: MusicSource.Netease)
                        }
                        if (song.artists.isNotBlank()) SongActionRow("前往艺人：${song.artists}", MeloXSymbol.ListBullet) {
                            val id = (song.artistId ?: track?.artists?.firstOrNull()?.id?.value?.toLongOrNull())?.takeIf { neteaseId != null && it > 0L }
                            onDismiss()
                            if (id != null) melox.ui.navigation.MeloXCollectionLaunchBus.open(melox.ui.navigation.MeloXCollectionTarget.Artist(id))
                            else melox.ui.navigation.MeloXSearchLaunchBus.post(track?.artists?.firstOrNull()?.name ?: song.artists.substringBefore(" / "), MeloXSearchKind.Artists, track?.id?.source ?: MusicSource.Netease)
                        }
                        CompositionLocalProvider(LocalSongActionGrouped provides true) {
                        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            SongActionRow("定时关闭", MeloXSymbol.Moon) { page = SongActionPage.Sleep }
                            SongActionRow("下一首播放", MeloXSymbol.NextTrack, separator = true) { PlaybackCommands.playNext(song); onDismiss() }
                            SongActionRow("添加到播放队列", MeloXSymbol.Badge, separator = true) { PlaybackCommands.addToQueue(song); onDismiss() }
                            val downloaded = downloads.downloads.any { it.song.id == neteaseId }
                            val downloading = neteaseId != null && downloads.activeDownloads.containsKey(neteaseId)
                            SongActionRow(when { downloaded -> "删除下载"; downloading -> "取消下载"; else -> "下载歌曲" },
                                if (downloaded) MeloXSymbol.Trash else MeloXSymbol.Download,
                                enabled = neteaseId != null, separator = true,
                                subtitle = if (neteaseId == null) "当前下载存储仅支持网易云音乐" else null) {
                                when { downloaded -> downloads.remove(requireNotNull(neteaseId)); downloading -> downloads.cancel(requireNotNull(neteaseId))
                                    else -> downloads.start(song, MusicQualityPreferences.read(), sourcePlaylist) }
                            }
                            SongActionRow("添加到歌单", MeloXSymbol.Badge, !busy && (neteaseId != null || playlistWriter != null), separator = true) {
                                page = SongActionPage.AddToPlaylist; loadPlaylists()
                            }
                            SongActionRow(if (liked == true) "取消喜爱" else "喜爱", MeloXSymbol.Heart,
                                !busy && !favoriteLoading && canFavorite, separator = true,
                                subtitle = when { !canFavorite -> "当前来源尚未接入喜爱状态读取"; favoriteLoading -> "正在读取喜爱状态"
                                    favoriteError != null -> "$favoriteError · 点击重试"; else -> null },
                                state = when (liked) { true -> "liked"; false -> "unliked"; null -> "unknown" }) {
                                if (liked == null) scope.launch { loadFavorite() }
                                else launchOperation("喜爱失败") {
                                    val desired = liked != true
                                    if (neteaseId != null) ops.setSongLiked(neteaseId, desired)
                                    else requireNotNull(favoriteWriter).setFavorite(requireNotNull(track), desired)
                                    liked = desired
                                }
                            }
                            SongActionRow("系统分享", MeloXSymbol.Share, separator = true) {
                                // Desktop clipboard is the available OS share transport; never invent a foreign provider URL.
                                try {
                                    val url = songShareUrl(song) ?: error("当前来源没有可分享的公开链接")
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                        java.awt.datatransfer.StringSelection("${song.name} - ${song.artists}\n$url"), null)
                                    message = "歌曲链接已复制，可粘贴到其他应用分享"
                                } catch (failure: Exception) { message = failure.message ?: "系统分享失败" }
                            }
                            SongActionRow("发送给网易云好友", MeloXSymbol.Send, !busy && neteaseId != null, separator = true) {
                                page = SongActionPage.ShareContacts
                                launchOperation("联系人加载失败") { contacts = ops.messageContacts(account.accountProfile().userId) }
                            }
                            SongActionRow("分享到网易云动态", MeloXSymbol.Message, !busy && neteaseId != null, separator = true) {
                                launchOperation("动态分享失败") { social.shareSongToTimeline(requireNotNull(neteaseId)); message = "已分享到网易云动态" }
                            }
                            SongActionRow("查看评论", MeloXSymbol.Chat, !busy && neteaseId != null, separator = true) {
                                page = SongActionPage.Comments; loadComments()
                            }
                            SongActionRow("我的听歌排行", MeloXSymbol.Clock, !busy && neteaseId != null, separator = true) {
                                page = SongActionPage.ListeningRank; loadRecords(MeloXUserPlayRecordPeriod.Week)
                            }
                            SongActionRow("歌曲百科", MeloXSymbol.Book, !busy && neteaseId != null, separator = true) {
                                page = SongActionPage.Wiki
                                launchOperation("百科加载失败") { wiki = ops.songWiki(requireNotNull(neteaseId)) }
                            }
                        }
                        }
                    }
                    SongActionPage.Sleep -> {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            SongActionRow("$minutes 分钟后", MeloXSymbol.Clock) { MeloXSleepTimer.set(minutes); onDismiss() }
                        }
                        if (sleepEnd > 0L) SongActionRow("取消定时", MeloXSymbol.XMark) { MeloXSleepTimer.cancel(); onDismiss() }
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.AddToPlaylist -> {
                        if (busy) SongActionLoading("正在读取歌单")
                        SongActionRow("新建歌单", MeloXSymbol.Plus, !busy && (neteaseId != null || playlistSync != null)) {
                            message = null; page = SongActionPage.CreatePlaylist
                        }
                        owned.forEach { playlist ->
                            SongPlaylistRow(playlist.name, playlist.trackCount, playlist.coverUrl, !busy) {
                                launchOperation("添加歌曲失败") {
                                    ops.addSongToPlaylist(requireNotNull(neteaseId), playlist.id)
                                    onSourcePlaylistChanged?.invoke(); onDismiss()
                                }
                            }
                        }
                        providerPlaylists.forEach { playlist ->
                            SongPlaylistRow(playlist.title, playlist.trackCount?.toInt(), playlist.artworkUrl, !busy) {
                                launchOperation("添加歌曲失败") {
                                    requireNotNull(playlistWriter).addTrackToPlaylist(requireNotNull(track), playlist)
                                    onSourcePlaylistChanged?.invoke(); onDismiss()
                                }
                            }
                        }
                        if (morePlaylists) SongActionRow("加载更多歌单", MeloXSymbol.Plus, !busy) { loadPlaylists(true) }
                        if (!busy && owned.isEmpty() && providerPlaylists.isEmpty() && message == null) Text("没有可写入的自建歌单。", Modifier.padding(12.dp))
                        if (!busy && message != null) SongActionRow("重试", MeloXSymbol.Refresh) { loadPlaylists() }
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.CreatePlaylist -> {
                        Text("创建歌单", Modifier.padding(horizontal = 6.dp, vertical = 8.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (newName.isBlank()) Text("歌单名称", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp)
                            BasicTextField(newName, { newName = it.take(40) }, enabled = !busy, singleLine = true,
                                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp), modifier = Modifier.fillMaxWidth().testTag("song-new-playlist-name"))
                        }
                        if (neteaseId != null) SongActionRow(if (privatePlaylist) "私密歌单" else "公开歌单", if (privatePlaylist) MeloXSymbol.ListBullet else MeloXSymbol.Message, !busy) { privatePlaylist = !privatePlaylist }
                        SongActionRow("创建并添加歌曲", MeloXSymbol.Checkmark, !busy && newName.isNotBlank()) {
                            launchOperation("歌单创建失败") {
                                if (neteaseId != null) {
                                    val created = ops.createPlaylist(newName, privatePlaylist)
                                    // A failed add must not cause Retry to create another orphan playlist.
                                    owned = listOf(created) + owned; page = SongActionPage.AddToPlaylist
                                    ops.addSongToPlaylist(neteaseId, created.id)
                                } else {
                                    val created = requireNotNull(playlistSync).createPlaylist(newName)
                                    providerPlaylists = listOf(created) + providerPlaylists; page = SongActionPage.AddToPlaylist
                                    requireNotNull(playlistWriter).addTrackToPlaylist(requireNotNull(track), created)
                                }
                                onSourcePlaylistChanged?.invoke(); onDismiss()
                            }
                        }
                        SongActionRow("返回歌单列表", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.Comments -> {
                        if (busy && comments == null) SongActionLoading("正在读取评论")
                        LazyColumn(Modifier.fillMaxWidth().height(360.dp).testTag("song-comments")) {
                            if (!comments?.hotComments.isNullOrEmpty()) item { Text("热门评论", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
                            items(comments?.hotComments.orEmpty(), key = { "hot-${it.id}" }) { comment ->
                                Column(Modifier.fillMaxWidth().testTag("song-comment-hot-${comment.id}").clickable(enabled = !busy) {
                                    selectedComment = comment; replies = null; page = SongActionPage.CommentReplies; loadReplies(comment)
                                }.padding(vertical = 9.dp)) { SongCommentRow(comment) }
                            }
                            if (!comments?.comments.isNullOrEmpty()) item { Text("最新评论 · ${comments?.totalCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
                            items(comments?.comments.orEmpty(), key = { "latest-${it.id}" }) { comment ->
                                Column(Modifier.fillMaxWidth().testTag("song-comment-${comment.id}").clickable(enabled = !busy) {
                                    selectedComment = comment; replies = null; page = SongActionPage.CommentReplies; loadReplies(comment)
                                }.padding(vertical = 9.dp)) { SongCommentRow(comment) }
                            }
                        }
                        if (!busy && comments != null && comments?.hotComments.orEmpty().isEmpty() && comments?.comments.orEmpty().isEmpty()) Text("暂无评论", Modifier.padding(12.dp))
                        if (comments?.hasMore == true) SongActionRow("加载更多评论", MeloXSymbol.Plus, !busy) { loadComments(true) }
                        if (!busy && message != null) SongActionRow("重试", MeloXSymbol.Refresh) { loadComments() }
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.CommentReplies -> {
                        selectedComment?.let { Text("原评论", fontSize = 12.sp); SongCommentRow(replies?.ownerComment ?: it) }
                        if (busy && replies == null) SongActionLoading("正在读取回复")
                        LazyColumn(Modifier.fillMaxWidth().height(280.dp).testTag("song-comment-replies")) {
                            items(replies?.replies.orEmpty(), key = { it.id }) { SongCommentRow(it) }
                        }
                        replies?.let { loaded ->
                            Text(if (loaded.totalCount > 0) "全部回复 · ${loaded.totalCount}" else "暂无回复", fontSize = 12.sp)
                            if (loaded.hasMore) SongActionRow("加载更多回复", MeloXSymbol.Plus, !busy) { selectedComment?.let { loadReplies(it, true) } }
                        }
                        if (!busy && message != null) SongActionRow("重试", MeloXSymbol.Refresh) { selectedComment?.let { loadReplies(it) } }
                        SongActionRow("返回评论", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.ShareContacts -> {
                        if (busy && contacts.isEmpty()) SongActionLoading("正在读取联系人")
                        LazyColumn(Modifier.fillMaxWidth().height(350.dp)) {
                            items(contacts, key = { it.id }) { contact ->
                                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    launchOperation("发送失败") {
                                        social.sendSongToUser(requireNotNull(neteaseId), contact.id)
                                        message = "已发送给 ${contact.name}"; page = SongActionPage.Main
                                    }
                                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MeloXArtworkImage(contact.avatarUrl, fallbackColor = MeloXColors.Primary, modifier = Modifier.size(44.dp))
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(contact.name)
                                        if (contact.signature.isNotBlank()) Text(contact.signature, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (!busy && contacts.isEmpty() && message == null) Text("暂无联系人", Modifier.padding(12.dp))
                        if (!busy && message != null) SongActionRow("重试", MeloXSymbol.Refresh) {
                            launchOperation("联系人加载失败") { contacts = ops.messageContacts(account.accountProfile().userId) }
                        }
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.ListeningRank -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MeloXUserPlayRecordPeriod.Week to "最近一周", MeloXUserPlayRecordPeriod.AllTime to "所有时间").forEach { (value, title) ->
                                Box(Modifier.weight(1f).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (period == value) .18f else .07f), RoundedCornerShape(14.dp))
                                    .clickable(enabled = !busy) { loadRecords(value) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (busy) SongActionLoading("正在读取听歌排行")
                        LazyColumn(Modifier.fillMaxWidth().height(330.dp)) {
                            items(records, key = { it.song.id }) { record ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    PlaybackCommands.playQueue(records.map { it.song }, record.song.id); onDismiss()
                                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    MeloXArtworkImage(record.song.artworkUrl, fallbackColor = MeloXColors.Primary, modifier = Modifier.size(44.dp).padding(2.dp))
                                    Spacer(Modifier.size(10.dp))
                                    Column(Modifier.weight(1f)) { Text(record.song.name, maxLines = 1); Text(record.song.artists, fontSize = 12.sp, maxLines = 1) }
                                    Text("${record.playCount} 次", fontSize = 12.sp)
                                }
                            }
                        }
                        if (!busy && records.isEmpty() && message == null) Text("暂无听歌排行数据", Modifier.padding(12.dp))
                        if (!busy && message != null) SongActionRow("重试", MeloXSymbol.Refresh) { loadRecords(period) }
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.Wiki -> {
                        if (busy) SongActionLoading("正在读取百科")
                        LazyColumn(Modifier.fillMaxWidth().height(360.dp).testTag("song-wiki")) {
                            items(wiki) { section ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                                    Text(section.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    section.lines.forEach { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                                }
                            }
                        }
                        if (!busy && wiki.isEmpty() && message == null) Text("暂无百科资料", Modifier.padding(12.dp))
                        if (!busy && message != null) SongActionRow("重试", MeloXSymbol.Refresh) {
                            launchOperation("百科加载失败") { wiki = ops.songWiki(requireNotNull(neteaseId)) }
                        }
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                    SongActionPage.ListenTogether -> {
                        val room = together.room
                        if (room == null) {
                            SongActionRow("创建一起听房间", MeloXSymbol.Plus, !busy) {
                                launchOperation("创建房间失败") {
                                    val session = cookie()
                                    val created = ops.createListenTogetherRoom()
                                    check(session == cookie()) { "账号已切换，请重新连接" }
                                    melox.playback.MeloXListenTogetherCoordinator.adoptRoom(created)
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                if (invitation.isBlank()) Text("粘贴一起听邀请链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 14.sp)
                                BasicTextField(invitation, { invitation = it }, enabled = !busy, singleLine = true,
                                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                            }
                            SongActionRow("加入一起听房间", MeloXSymbol.Message, !busy) {
                                val parsed = parseNeteaseListenTogetherInvitation(invitation)
                                if (parsed == null) message = "邀请链接无效" else launchOperation("加入房间失败") {
                                    val session = cookie()
                                    val joined = ops.joinListenTogetherRoom(parsed.roomId, parsed.inviterId)
                                    check(session == cookie()) { "账号已切换，请重新连接" }
                                    melox.playback.MeloXListenTogetherCoordinator.adoptRoom(joined)
                                }
                            }
                        } else {
                            Text("房间 ${room.id} · ${room.users.size.coerceAtLeast(1)} 位成员", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f), fontSize = 13.sp, modifier = Modifier.padding(6.dp))
                            room.users.forEach { Text("• ${it.name}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
                            Text(when (together.phase) {
                                melox.playback.MeloXListenTogetherCoordinator.Phase.Reconnecting -> "正在重新连接 · ${together.consecutiveFailures} 次失败"
                                melox.playback.MeloXListenTogetherCoordinator.Phase.Connected -> "后台自动同步已启用 · 1 秒状态同步 · 5 秒心跳"
                                melox.playback.MeloXListenTogetherCoordinator.Phase.Idle -> "正在恢复房间状态"
                            }, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            SongActionRow("分享房间邀请", MeloXSymbol.Share) {
                                try {
                                    val inviter = room.creatorId.ifBlank { room.users.firstOrNull()?.id.orEmpty() }
                                    val url = "https://st.music.163.com/listen-together/share/?songId=$neteaseId&roomId=${java.net.URLEncoder.encode(room.id, "UTF-8")}&inviterId=${java.net.URLEncoder.encode(inviter, "UTF-8")}"
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null)
                                    message = "邀请链接已复制"
                                } catch (failure: Exception) { message = failure.message ?: "分享失败" }
                            }
                            SongActionRow("结束/退出房间", MeloXSymbol.XMark, !busy) {
                                launchOperation("结束房间失败") {
                                    val session = cookie()
                                    ops.endListenTogetherRoom(room.id)
                                    if (session == cookie()) melox.playback.MeloXListenTogetherCoordinator.clearRoom()
                                }
                            }
                        }
                        if (busy) SongActionLoading("正在连接一起听")
                        SongActionRow("返回", MeloXSymbol.ChevronLeft, onClick = ::back)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongActionRow(
    title: String, symbol: MeloXSymbol, enabled: Boolean = true, separator: Boolean = false,
    subtitle: String? = null, state: String? = null, onClick: () -> Unit,
) {
    val modifier = Modifier.testTag("song-action-$title").semantics {
            if (!enabled) disabled()
            if (state != null) stateDescription = state
        }
    val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    if (LocalSongActionGrouped.current) MeloXIosListRow(title = title, subtitle = subtitle,
        modifier = modifier,
        leading = { MeloXSymbolIcon(symbol, Modifier.size(22.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f), iconSize = 21) },
        showTopSeparator = separator, onClick = if (enabled) onClick else null)
    else Row(modifier.fillMaxWidth().height(46.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { MeloXSymbolIcon(symbol, Modifier.size(20.dp), foreground.copy(alpha = foreground.alpha * .86f), iconSize = 20) }
        Column(Modifier.weight(1f)) {
            Text(title, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = foreground.copy(alpha = foreground.alpha * .58f), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun SongCommentRow(comment: MeloXMusicComment) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp)) {
        Text(comment.user, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(comment.content, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
        if (comment.timeText.isNotBlank() || comment.likedCount > 0L) Text(
            listOf(comment.timeText.takeIf(String::isNotBlank), "♡ ${comment.likedCount}").filterNotNull().joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SongActionLoading(text: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(10.dp)); Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
    }
}

@Composable
private fun SongPlaylistRow(name: String, count: Int?, cover: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp).testTag("song-playlist-$name").clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        MeloXArtworkImage(cover, fallbackColor = MeloXColors.Primary, modifier = Modifier.size(46.dp).padding(2.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            count?.let { Text("$it 首歌曲", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)) }
        }
    }
}

@Composable
private fun BilibiliOffset(song: SearchSong) {
    val provider = song.providerTrack?.takeIf { it.id.source == MusicSource.Bilibili } ?: return
    val persisted by melox.provider.bilibili.BilibiliLyricOffsetStore.state(provider.id.value)
    var displayed by remember(provider.id, persisted) { mutableStateOf(persisted) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(pinnedString("player_lyric_offset"), fontWeight = FontWeight.SemiBold)
        Text(when { displayed == 0 -> pinnedString("player_lyric_sync"); displayed > 0 -> pinnedString("player_lyric_ahead", displayed)
            else -> pinnedString("player_lyric_behind", displayed) }, fontSize = 12.sp)
        melox.ui.glass.MeloXLiquidSlider(displayed.toFloat(),
            onTransientValueChange = { displayed = it.toInt() },
            onValueChange = { displayed = it.toInt(); melox.provider.bilibili.BilibiliLyricOffsetStore.write(provider.id.value, displayed) },
            valueRange = -5_000f..5_000f, stepSize = 100f, visibilityThreshold = 1f,
            contentDescription = pinnedString("player_lyric_offset"), modifier = Modifier.testTag("bilibili-lyric-offset"))
    }
}
