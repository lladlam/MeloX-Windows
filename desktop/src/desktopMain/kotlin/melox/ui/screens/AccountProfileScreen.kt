package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.network.*
import melox.playback.PlaybackCommands
import melox.ui.glass.*
import melox.ui.legal.pinnedString
import melox.ui.navigation.MeloXBackHandler

/** Pinned AccountHomeScreen, reached by real user-search IDs and the signed-in profile. */
@Composable
internal fun AccountProfileScreen(userId: Long, onBack: () -> Unit) {
    val revision by NeteaseSessionStore.revision.collectAsState()
    val cookie = remember(revision) { NeteaseSessionStore.readCookie() }
    val details = remember(cookie) { NeteaseAccountDetailsClient({ cookie }) }
    val social = remember(cookie) { NeteaseSocialExtrasClient({ cookie }) }
    val library = remember(cookie) { NeteaseLibraryClient({ cookie }) }
    var profile by remember(userId, revision) { mutableStateOf<MeloXAccountDetail?>(null) }
    var playlists by remember(userId, revision) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var period by remember(userId) { mutableStateOf(MeloXUserPlayRecordPeriod.Week) }
    var records by remember(userId, revision) { mutableStateOf<List<MeloXUserPlayRecord>>(emptyList()) }
    var loading by remember(userId, revision) { mutableStateOf(true) }
    var error by remember(userId, revision) { mutableStateOf<String?>(null) }
    var rankError by remember(userId, revision) { mutableStateOf<String?>(null) }
    var retry by remember(userId) { mutableIntStateOf(0) }
    var selectedPlaylist by remember(userId, revision) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    LaunchedEffect(userId, revision, retry) {
        loading = true; error = null
        try {
            val loaded = withContext(Dispatchers.IO) { details.userDetail(userId) to library.userPlaylistsBlocking(userId) }
            profile = loaded.first; playlists = loaded.second
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: pinnedString("account_profile_failed") }
        finally { loading = false }
    }
    LaunchedEffect(userId, revision, profile?.userId, period, retry) {
        if (profile == null) return@LaunchedEffect
        rankError = null; records = emptyList()
        try { records = social.userPlayRecords(userId, period) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { rankError = failure.message ?: pinnedString("player_rank_failed") }
    }
    MeloXBackHandler(selectedPlaylist != null, priority = 20) { selectedPlaylist = null }
    selectedPlaylist?.let { playlist ->
        PlaylistDetailScreen(playlist, onBack = { selectedPlaylist = null })
        return
    }
    MeloXPinnedListPage(pinnedString("account_profile_title"), onBack, MeloXBottomContentClearanceDp,
        modifier = Modifier.testTag("search-account-profile")) {
        profile?.let { value -> item("account-hero") { AccountHero(value, playlists.size) } }
        if (loading && profile == null) item("loading") {
            Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        (error ?: rankError)?.let { message -> item("error") {
            MeloXGlassCard(Modifier.fillMaxWidth()) {
                Text(message, Modifier.testTag("search-account-error"), color = MaterialTheme.colorScheme.error)
                MeloXGlassButton(onClick = { retry++ }, Modifier.fillMaxWidth().testTag("search-account-retry"),
                    style = MeloXGlassButtonStyle.BorderedProminent, shape = MeloXShapes.capsule) { Text(pinnedString("account_retry")) }
            }
        } }
        if (profile != null) {
            item("rank-title") { Text(pinnedString("account_listening_rank"), fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, start = 4.dp)) }
            item("rank-period") {
                MeloXGlassSegmentedControl(listOf(pinnedString("player_rank_week"), pinnedString("player_rank_all_time")),
                    if (period == MeloXUserPlayRecordPeriod.Week) 0 else 1,
                    onSelected = { period = if (it == 0) MeloXUserPlayRecordPeriod.Week else MeloXUserPlayRecordPeriod.AllTime },
                    modifier = Modifier.fillMaxWidth().testTag("search-account-rank-period"))
            }
            records.take(100).forEachIndexed { index, record -> item("rank-${period.name}-${record.song.id}") {
                MeloXGlassCard(Modifier.fillMaxWidth().testTag("search-account-rank-${record.song.id}"),
                    onClick = { PlaybackCommands.playQueue(records.map(MeloXUserPlayRecord::song), record.song.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", Modifier.size(30.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        MeloXArtworkImage(record.song.artworkUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text(record.song.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(record.song.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(pinnedString("player_play_count", record.playCount), style = MaterialTheme.typography.labelMedium)
                    }
                }
            } }
        }
        if (playlists.isNotEmpty()) {
            item("playlist-title") { Text(pinnedString("account_playlists_count", playlists.size), fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, start = 4.dp)) }
            playlists.forEach { playlist -> item("playlist-${playlist.id}") {
                MeloXGlassCard(Modifier.fillMaxWidth().testTag("search-account-playlist-${playlist.id}"), onClick = { selectedPlaylist = playlist }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MeloXArtworkImage(playlist.coverUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(54.dp).clip(RoundedCornerShape(11.dp)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pinnedString("player_track_count", playlist.trackCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
        }
    }
}

@Composable
private fun AccountHero(profile: MeloXAccountDetail, playlistCount: Int) {
    MeloXGlassCard(Modifier.fillMaxWidth().testTag("search-account-hero")) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            MeloXArtworkImage(profile.avatarUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(132.dp).clip(CircleShape))
            Text(profile.nickname, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            profile.signature?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text(pinnedString("account_level_listens", profile.level, profile.listenSongs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pinnedString("account_user_id", profile.userId), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f))
            Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                AccountMetric(profile.follows, pinnedString("account_following"), Modifier.weight(1f))
                AccountMetric(profile.followers, pinnedString("account_followers"), Modifier.weight(1f))
                AccountMetric(profile.playlistCount.takeIf { it > 0 } ?: playlistCount, pinnedString("account_playlists"), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccountMetric(value: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
