package melox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import melox.audio.*
import melox.download.MeloXDownloadStore
import melox.music.model.AudioQualityTier
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.PlaybackAccountStore
import melox.playback.CrossProviderPlaybackRuntime
import melox.playback.ProviderPlaybackQualityRuntime
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.*
import melox.ui.navigation.MeloXBackHandler

private data class ActiveQuality(
    val actual: MusicQuality,
    val title: String,
    val downloaded: Boolean,
    val fallbackSource: MusicSource?,
)

private fun activeQuality(track: MusicTrack): ActiveQuality {
    val neteaseId = track.id.takeIf { it.source == MusicSource.Netease }?.value?.toLongOrNull()
    val downloaded = neteaseId?.let(MeloXDownloadStore.instance()::downloadedQuality)
    val providerActual = ProviderPlaybackQualityRuntime.actualFor(track.id)
    val actual = downloaded ?: if (track.id.source == MusicSource.Netease) {
        MusicQualityRuntime.actualFor(neteaseId) ?: MusicQualityRuntime.selected
    } else providerActual?.toMusicQuality() ?: MusicQualityRuntime.selected
    val title = if (downloaded != null || track.id.source == MusicSource.Netease) actual.title
        else providerActual?.sceneTitle() ?: actual.title
    return ActiveQuality(actual, title, downloaded != null, CrossProviderPlaybackRuntime.sourceFor(neteaseId))
}

@Composable
private fun rememberActiveQuality(track: MusicTrack, refreshMs: Long): ActiveQuality {
    var quality by remember(track.id) { mutableStateOf(activeQuality(track)) }
    LaunchedEffect(track.id, refreshMs) {
        while (true) {
            quality = activeQuality(track)
            delay(refreshMs)
        }
    }
    return quality
}

/** MeloXNowPlayingCoreControls SceneQualityChip: actual resolver quality, never guessed bitrate. */
@Composable
internal fun MeloXPlayerQualityChip(track: MusicTrack, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val quality = rememberActiveQuality(track, 750)
    val title = quality.fallbackSource?.let { "${it.displayName} · ${quality.title}" } ?: quality.title
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(.5f, 10_000f), label = "scene-quality-chip-press")
    Box(modifier) {
        Row(Modifier.testTag("player-quality").graphicsLayer { scaleX = scale; scaleY = scale }.height(48.dp)
            .meloXLiquidButton(RoundedCornerShape(7.dp), surfaceColor = Color.White.copy(alpha = .10f),
                lensRadius = 6.dp, refractionHeight = 9.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                MeloXSymbolIcon(MeloXSymbol.Waveform, size = 12, iconSize = 14, color = Color.White.copy(alpha = .86f))
            }
            Text(title, color = Color.White.copy(alpha = .86f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** The source overlay reports quality; its Settings action opens the real playback settings route. */
@Composable
internal fun MeloXPlayerQualityOverlay(
    track: MusicTrack, visible: Boolean, onDismiss: () -> Unit, onOpenPlaybackSettings: () -> Unit,
) {
    val quality = rememberActiveQuality(track, if (visible) 180 else 750)
    var availability by remember(track.id, visible) { mutableStateOf(SongAudioAvailability.Unknown) }
    LaunchedEffect(track.id, visible, quality.downloaded) {
        if (!visible || quality.downloaded || track.id.source != MusicSource.Netease) return@LaunchedEffect
        val id = track.id.value.toLongOrNull() ?: return@LaunchedEffect
        availability = try {
            NeteaseQualityClient(cookieProvider = PlaybackAccountStore::neteaseCookie).audioAvailability(id)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SongAudioAvailability.Unknown }
    }
    MeloXBackHandler(visible, priority = 110, onBack = onDismiss)
    val foreground = MaterialTheme.colorScheme.onSurface
    val sourceLabel = when {
        quality.downloaded -> "已下载音频"
        quality.fallbackSource != null -> "实际音源：${quality.fallbackSource.displayName}"
        track.id.source != MusicSource.Netease -> "${track.id.source.displayName} 音源"
        else -> "当前播放"
    }
    val resource = availability.resourceFor(quality.actual)
    val details = if (quality.downloaded) "本地文件 · 参数以下载音频为准" else buildList {
        resource?.bitrate?.let { add("${it / 1000} kbps") }
        resource?.sampleRate?.let { add("${it / 1_000.0} kHz") }
        if (quality.actual in listOf(MusicQuality.HighDefinitionSurround, MusicQuality.ImmersiveSurround)) add("5.1 声道")
        else if (track.id.source == MusicSource.Netease) add("2 声道")
    }.joinToString(" · ").ifBlank { "${track.id.source.displayName} 未提供音频参数" }
    MeloXInlinePlayerDialog(visible, onDismiss, "quality-dialog") {
        Text("音质", style = MaterialTheme.typography.titleLarge)
        Text("当前音质：${quality.actual.title}", Modifier.padding(top = 9.dp), color = foreground.copy(alpha = .68f), fontSize = 14.sp)
        Text(details, Modifier.padding(top = 4.dp), color = foreground.copy(alpha = .68f), fontSize = 14.sp)
        Text(sourceLabel, Modifier.padding(top = 4.dp), color = foreground.copy(alpha = .54f), fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(onClick = { onDismiss(); onOpenPlaybackSettings() }, modifier = Modifier.weight(1f).testTag("quality-settings"),
                style = MeloXGlassButtonStyle.Bordered) { Text("设置") }
            MeloXGlassButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("quality-confirm"),
                style = MeloXGlassButtonStyle.BorderedProminent) { Text("确认") }
        }
    }
}

@Composable
internal fun MeloXPlayerOutputError(message: String?, onDismiss: () -> Unit) {
    MeloXBackHandler(message != null, priority = 120, onBack = onDismiss)
    var retained by remember { mutableStateOf<String?>(null) }
    if (message != null) retained = message
    MeloXInlinePlayerDialog(message != null, onDismiss, "output-error") {
        Text("音频输出", style = MaterialTheme.typography.titleLarge)
        Text(retained.orEmpty(), modifier = Modifier.padding(top = 9.dp), fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
        MeloXGlassButton(onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            style = MeloXGlassButtonStyle.BorderedProminent) { Text("确认") }
    }
}

@Composable
private fun MeloXInlinePlayerDialog(visible: Boolean, onDismiss: () -> Unit, tag: String, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible,
        enter = fadeIn(spring(.86f, MeloXMotion.PanelEnterStiffness)) + scaleIn(spring(.86f, MeloXMotion.PanelEnterStiffness), initialScale = .96f),
        exit = fadeOut(spring(.90f, MeloXMotion.PanelExitStiffness)) + scaleOut(spring(.90f, MeloXMotion.PanelExitStiffness), targetScale = .98f)) {
        val dark = MaterialTheme.colorScheme.background.luminance() < .5f
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = 28.dp).fillMaxWidth().widthIn(max = 360.dp).testTag(tag)
                .meloXGlassSurface(RoundedCornerShape(28.dp),
                    tint = Color.White.copy(alpha = if (dark) .08f else .34f),
                    surfaceColor = if (dark) Color.Black else Color.White)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(horizontal = 18.dp, vertical = 18.dp), content = content)
        }
    }
}

private fun SongAudioAvailability.resourceFor(quality: MusicQuality): SongAudioResource? = when (quality) {
    MusicQuality.Standard -> standard
    MusicQuality.High -> high ?: medium
    MusicQuality.Lossless -> lossless
    MusicQuality.HiResolution -> hiResolution
    MusicQuality.HighDefinitionSurround -> highDefinitionSurround
    MusicQuality.ImmersiveSurround -> immersiveSurround
    MusicQuality.UltraClearMaster -> ultraClearMaster
}

private fun AudioQualityTier.toMusicQuality(): MusicQuality = when (this) {
    AudioQualityTier.Standard -> MusicQuality.Standard
    AudioQualityTier.High -> MusicQuality.High
    AudioQualityTier.Lossless -> MusicQuality.Lossless
    AudioQualityTier.HiResolution -> MusicQuality.HiResolution
    AudioQualityTier.Immersive -> MusicQuality.ImmersiveSurround
    AudioQualityTier.Master -> MusicQuality.UltraClearMaster
}

private fun AudioQualityTier.sceneTitle(): String = when (this) {
    AudioQualityTier.Immersive -> "臻品/环绕"
    AudioQualityTier.Master -> "臻品母带"
    else -> toMusicQuality().title
}
