package melox.ui.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.FavoriteCapability
import melox.music.provider.FavoriteStateCapability
import melox.network.NeteaseMusicOperationsClient
import melox.network.NeteaseSearchClient
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.MeloXUnifiedFavorites
import melox.ui.foundation.MeloXSymbol

/** Same real readers/writers as the first-layout song actions; no optimistic fake star. */
@Composable
internal fun DesktopPlayerFavorite(track: MusicTrack, scale: Float, refreshKey: Boolean) = key(track.id) {
    val scope = rememberCoroutineScope()
    val provider = remember { MeloXMusicProviders.create()[track.id.source] }
    val reader = provider as? FavoriteStateCapability
    val writer = provider as? FavoriteCapability
    val neteaseId = track.id.value.toLongOrNull()?.takeIf { track.id.source == MusicSource.Netease }
    val unifiedRevision by MeloXUnifiedFavorites.revision.collectAsState()
    val unified = MeloXUnifiedFavorites.enabled()
    val supported = unified || neteaseId != null || (reader != null && writer != null)
    val session by NeteaseSessionStore.revision.collectAsState()
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun read(): Boolean {
        return if (unified) MeloXUnifiedFavorites.contains(track) else if (neteaseId != null) {
            check(NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie())) { desktopString("ui.desktop.sidebar.not_signed_in") }
            val cookie = { NeteaseSessionStore.readCookie() }
            val profile = NeteaseSearchClient(cookieProvider = cookie).accountProfile()
            withContext(Dispatchers.IO) { neteaseId in NeteaseLibraryClient(cookie).likedSongIdsBlocking(profile.userId) }
        } else requireNotNull(reader).isFavorite(track)
    }
    LaunchedEffect(session, refreshKey, unifiedRevision) {
        if (!supported || refreshKey) return@LaunchedEffect
        liked = null
        busy = true
        try { liked = read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Unknown stays unknown; clicking retries and exposes the actual failure. */ }
        finally { busy = false }
    }
    DesktopPlayerCircleButton(if (liked == true) MeloXSymbol.StarFill else MeloXSymbol.Star, "desktop-player-favorite",
        if (!supported) desktopPlatformString("unavailable") else desktopString(if (liked == true) "ui.desktop.collection.unfavorite" else "ui.desktop.collection.favorite"),
        scale, enabled = supported && !busy, state = when (liked) { true -> "liked"; false -> "unliked"; null -> "unknown" }) {
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    val desired = !(liked ?: read())
                    if (unified) withContext(Dispatchers.IO) { MeloXUnifiedFavorites.set(track, desired) }
                    else if (neteaseId != null) NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }).setSongLiked(neteaseId, desired)
                    else requireNotNull(writer).setFavorite(track, desired)
                    liked = desired
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.favorite_failed") }
                finally { busy = false }
            }
        }
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text(desktopString("ui.error.favorite_failed")) },
            text = { Text(message) }, confirmButton = {
                TextButton(onClick = { error = null }) { Text(desktopString("ui.common.close")) }
            })
    }
}
