package melox.playback

import melox.player.MeloXPcmMix
import melox.platform.getPreferences
import java.util.UUID
import kotlin.math.*
import kotlin.test.*

class DesktopEqualizerDspTest {
    private fun pcm(hz: Double, amplitude: Int = 3000, frames: Int = 44_100): ByteArray = ByteArray(frames * 4).also { bytes ->
        repeat(frames) { frame ->
            val sample = (amplitude * sin(2 * PI * hz * frame / 44_100)).roundToInt()
            repeat(2) { channel -> val offset = frame * 4 + channel * 2; bytes[offset] = sample.toByte(); bytes[offset + 1] = (sample shr 8).toByte() }
        }
    }
    private fun rms(bytes: ByteArray, channel: Int = 0): Double {
        val samples = (bytes.size / 4 - 4410).coerceAtLeast(1)
        return sqrt((4410 until bytes.size / 4).sumOf { frame -> MeloXPcmMix.sample(bytes, frame * 4 + channel * 2).toDouble().pow(2) } / samples)
    }
    @Test fun disabledAndFlatUnityAreByteExactRegardlessOfBlockBoundaries() {
        val original = pcm(1017.0)
        for (enabled in listOf(false, true)) {
            val bytes = original.copyOf(); val dsp = MeloXEqualizerController(settingsProvider = { MeloXEqualizerSettings(enabled = enabled) })
            dsp.process(bytes, bytes.size)
            assertContentEquals(original, bytes)
        }
    }
    @Test fun boostAndCutAtEveryBandHaveMeasuredCenterGainAndNoChannelLeakage() {
        for ((index, hz) in MeloXEqualizerController.TARGET_FREQUENCIES_HZ.withIndex()) for (db in listOf(-6f, 6f)) {
            val original = pcm(hz.toDouble()); val bytes = original.copyOf()
            for (frame in 0 until bytes.size / 4) { bytes[frame * 4 + 2] = 0; bytes[frame * 4 + 3] = 0 }
            val dsp = MeloXEqualizerController(settingsProvider = { MeloXEqualizerSettings(true, "Custom", gainsDb = List(10) { if (it == index) db else 0f }) })
            dsp.process(bytes, bytes.size)
            val measured = 20 * log10(rms(bytes) / rms(original))
            assertTrue(abs(measured - db) < .2, "$hz Hz requested $db dB, actual $measured dB")
            assertEquals(0.0, rms(bytes, 1), "Left signal leaked into right filter state")
            assertEquals(0L, dsp.clippedSamples)
        }
    }
    @Test fun streamingChunksPreserveFilterHistoryAndReleaseClearsIt() {
        val settings = MeloXEqualizerSettings(true, "Custom", gainsDb = List(10) { if (it == 5) 6f else 0f })
        val all = pcm(1000.0); val chunks = all.copyOf()
        MeloXEqualizerController(settingsProvider = { settings }).process(all, all.size)
        val dsp = MeloXEqualizerController(settingsProvider = { settings })
        var offset = 0
        while (offset < chunks.size) {
            val size = min(3528, chunks.size - offset); val block = chunks.copyOfRange(offset, offset + size)
            dsp.process(block, size); block.copyInto(chunks, offset); offset += size
        }
        assertContentEquals(all, chunks)
        dsp.release()
        val silence = ByteArray(3528); dsp.process(silence, silence.size)
        assertTrue(silence.all { it == 0.toByte() })
    }
    @Test fun liveDisableMorphEndsInByteExactBypassAndPreampVolumeSaturatesWithoutWrap() {
        var settings = MeloXEqualizerSettings(true, preampDb = 12f)
        val dsp = MeloXEqualizerController(settingsProvider = { settings })
        val high = pcm(1000.0, 20_000)
        dsp.process(high, high.size)
        assertTrue(dsp.clippedSamples > 0)
        assertEquals(32767, (0 until high.size / 4).maxOf { MeloXPcmMix.sample(high, it * 4) })
        settings = MeloXEqualizerSettings()
        val transition = pcm(1000.0, frames = 1764); dsp.process(transition, transition.size)
        val bypass = pcm(1000.0); val original = bypass.copyOf(); dsp.process(bypass, bypass.size)
        assertContentEquals(original, bypass)
        val muted = original.copyOf(); dsp.process(muted, muted.size, 0f)
        assertTrue(muted.all { it == 0.toByte() })
        val reduced = original.copyOf()
        val quieter = MeloXEqualizerController(settingsProvider = { MeloXEqualizerSettings(true, preampDb = 6f) })
        quieter.process(reduced, reduced.size, .25f)
        assertTrue(abs(20 * log10(rms(reduced) / rms(original)) - (6 + 20 * log10(.25))) < .02)
    }
    @Test fun canonicalValuesOverrideLegacyWithoutDeletingExistingBytesAndCorruptGainsAreBounded() {
        val name = UUID.randomUUID().toString(); val app = getPreferences("eq_app_$name"); val legacy = getPreferences("eq_legacy_$name")
        try {
            legacy.putValues(mapOf("equalizer_enabled" to "true", "equalizer_preset" to "Custom", "equalizer_custom_band_5" to "6.0"))
            assertEquals(6f, MeloXEqualizerSettings.read(app, legacy).gainsDb[5])
            app.putValues(mapOf("equalizer_enabled" to "false", "equalizer_custom_band_5" to "-99.0", "equalizer_preamp_db" to "NaN"))
            val current = MeloXEqualizerSettings.read(app, legacy)
            assertFalse(current.enabled); assertEquals(-12f, current.gainsDb[5]); assertEquals(0f, current.preampDb)
            assertEquals("6.0", legacy.getString("equalizer_custom_band_5", null))
            assertTrue(legacy.getBoolean("equalizer_enabled", false))
        } finally { app.clear(); legacy.clear() }
    }
    @Test fun explicitResetMasksLegacyEqualizerWithoutErasingItsExistingValues() {
        val name = UUID.randomUUID().toString(); val app = getPreferences("eq_reset_app_$name"); val legacy = getPreferences("eq_reset_legacy_$name")
        try {
            legacy.putValues(mapOf("equalizer_enabled" to "true", "equalizer_preset" to "Bass", "equalizer_preamp_db" to "6.0", "queue" to "preserved"))
            assertTrue(MeloXEqualizerSettings.read(app, legacy).enabled)
            app.clear()
            MeloXEqualizerSettings.resetLegacyOverrides(app, legacy)
            assertEquals(MeloXEqualizerSettings(), MeloXEqualizerSettings.read(app, legacy))
            assertTrue(legacy.getBoolean("equalizer_enabled", false))
            assertEquals("Bass", legacy.getString("equalizer_preset", null))
            assertEquals("preserved", legacy.getString("queue", null))
        } finally { app.clear(); legacy.clear() }
    }
}
