package melox.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import melox.music.model.MusicTrack
import melox.playback.MeloXAutoMixTransitionRuntime
import melox.settings.MeloXSettingsRuntime
import androidx.compose.ui.unit.dp

/** Pinned shared host split (28%, ease-out-back 1.24), 16dp gap, 620ms incoming cover handoff. */
@Composable
internal fun MeloXAutoMixArtwork(track: MusicTrack, color: Color, modifier: Modifier) {
    val mix by MeloXAutoMixTransitionRuntime.state.collectAsState()
    val exit by animateFloatAsState(if (mix.handedOff) 1f else 0f,
        tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 620, easing = CubicBezierEasing(.16f, 1f, .3f, 1f)), label = "automix-cover-handoff")
    if ((!mix.active && !mix.handedOff) || mix.incoming == null) {
        MeloXArtworkImage(track.artworkUrl, color, modifier)
        return
    }
    val phase = (mix.progress / .28f).coerceIn(0f, 1f) - 1
    val split = (1 + 2.24f * phase * phase * phase + 1.24f * phase * phase).coerceIn(0f, 1.08f)
    BoxWithConstraints(modifier.testTag("automix-paired-artwork")) {
        val full = maxWidth
        val gap = 16.dp
        val paired = (full - gap) / 2
        val outgoingSize = full + (paired - full) * split
        val incomingSize = paired + (full - paired) * exit
        val outgoingX = -(paired + gap) * exit
        val incomingPairedX = full + gap + ((paired + gap) - (full + gap)) * split
        val incomingX = incomingPairedX * (1 - exit)
        Box(Modifier.offset(outgoingX, (full - outgoingSize) / 2).size(outgoingSize).graphicsLayer { alpha = 1 - exit }
            .shadow(26.dp, RoundedCornerShape(12.dp), clip = false).clip(RoundedCornerShape(12.dp))) {
            MeloXArtworkImage(mix.outgoing?.artworkUrl ?: track.artworkUrl, color, Modifier.fillMaxSize())
        }
        if (split > .01f) Box(Modifier.offset(incomingX, (full - incomingSize) / 2).size(incomingSize)
            .shadow(26.dp, RoundedCornerShape(12.dp), clip = false).clip(RoundedCornerShape(12.dp))) {
            MeloXArtworkImage(mix.incoming?.artworkUrl, color, Modifier.fillMaxSize())
        }
    }
}
