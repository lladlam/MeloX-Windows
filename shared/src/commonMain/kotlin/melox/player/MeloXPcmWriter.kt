package melox.player

import java.io.IOException
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import melox.platform.logDebug

/** Preserve already-processed PCM across Java Sound's legal stop/short-write returns. */
internal suspend fun writePcmBlock(
    line: SourceDataLine, bytes: ByteArray, count: Int,
    isCurrent: () -> Boolean, isPaused: () -> Boolean, pauseVersion: () -> Long,
    startIfPlaying: () -> Boolean, onAccepted: (Int) -> Unit,
): Int {
    var offset = 0
    var silence: ByteArray? = null
    while (offset < count && isCurrent()) {
        currentCoroutineContext().ensureActive()
        val version = pauseVersion()
        if (isPaused()) { delay(10); continue }
        val canWrite = try { startIfPlaying() }
        catch (error: Exception) { throw AudioPlaybackFailure(AudioFailureKind.Output, error) }
        if (!canWrite) { delay(10); continue }
        if (!isCurrent()) break
        if (isPaused()) continue
        val remaining = count - offset
        val output = if (melox.lan.LanPcmTap.muteLocalOutput) {
            silence ?: ByteArray(count).also { silence = it }
        } else bytes
        val accepted = try { line.write(output, offset, remaining) }
        catch (error: Exception) { throw AudioPlaybackFailure(AudioFailureKind.Output, error) }
        if (accepted == 0 && isCurrent() && line.isOpen && (isPaused() || pauseVersion() != version)) {
            // pause may already have been followed by resume before write returns.
            // Retain the offset and do not run EQ/volume processing a second time.
            logDebug("AudioPlayer", "PCM write interrupted by pause: offset=$offset remaining=$remaining paused=${isPaused()} pauseVersion=$version->${pauseVersion()}")
            continue
        }
        if (!isCurrent()) break
        if (accepted <= 0 || accepted > remaining || accepted % AudioPlayer.PCM_BYTES_PER_FRAME != 0) {
            val details = "accepted=$accepted requested=$remaining offset=$offset open=${line.isOpen} running=${line.isRunning} " +
                "paused=${isPaused()} pauseVersion=$version->${pauseVersion()} framePosition=${runCatching { line.longFramePosition }.getOrNull()} " +
                "buffer=${runCatching { line.bufferSize }.getOrNull()} available=${runCatching { line.available() }.getOrNull()}"
            throw AudioPlaybackFailure(AudioFailureKind.Output, IOException("Audio output stopped accepting aligned PCM frames ($details)"))
        }
        melox.lan.LanPcmTap.accepted(bytes, offset, accepted)
        onAccepted(accepted)
        offset += accepted
    }
    return offset
}
