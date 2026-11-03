package melox.ui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import melox.account.NeteaseSessionStore
import melox.network.MeloXWikiSection
import melox.network.NeteaseMusicOperationsClient

internal suspend fun loadDesktopSongWiki(id: Long): List<MeloXWikiSection> =
    NeteaseMusicOperationsClient({ NeteaseSessionStore.readCookie() }).songWiki(id)

/** The dialog owns its request: closing it or changing songs cancels in-flight work. */
@Composable
internal fun DesktopSongWikiDialog(
    songId: Long,
    songName: String,
    onDismiss: () -> Unit,
    load: suspend (Long) -> List<MeloXWikiSection> = ::loadDesktopSongWiki,
) {
    var revision by remember(songId) { mutableIntStateOf(0) }
    var sections by remember(songId) { mutableStateOf<List<MeloXWikiSection>>(emptyList()) }
    var loading by remember(songId) { mutableStateOf(true) }
    var error by remember(songId) { mutableStateOf<String?>(null) }
    LaunchedEffect(songId, revision) {
        loading = true
        error = null
        try { sections = load(songId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(.9f).testTag("desktop-song-wiki"),
            shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(desktopPlatformString("song_wiki"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (songName.isNotBlank()) Text(songName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(requireNotNull(error), Modifier.testTag("desktop-song-wiki-error"), color = MaterialTheme.colorScheme.error)
                        DesktopDialogButton("ui.common.retry", tag = "desktop-song-wiki-retry") { revision++ }
                    }
                    sections.isEmpty() -> Text(desktopPlatformString("song_wiki_empty"), Modifier.testTag("desktop-song-wiki-empty"))
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).testTag("desktop-song-wiki-content"),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(sections) { _, section ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(section.title, fontWeight = FontWeight.Bold)
                                SelectionContainer { Text(section.lines.joinToString("\n"), fontSize = 13.sp) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DesktopDialogButton("ui.common.ok", tag = "desktop-song-wiki-close", onClick = onDismiss)
                }
            }
        }
    }
}
