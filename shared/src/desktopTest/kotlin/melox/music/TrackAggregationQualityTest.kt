package melox.music

import melox.music.model.*
import melox.music.provider.TrackAggregation
import melox.network.neteaseSearchQuality
import melox.provider.kugou.kugouSearchQuality
import melox.provider.kuwo.kuwoSearchQuality
import melox.provider.qqmusic.qqMusicQualityHint
import melox.provider.qishui.QishuiParsing
import org.json.JSONObject
import kotlin.test.*

class TrackAggregationQualityTest {
    private fun track(source: MusicSource, tier: AudioQualityTier? = null,
        availability: TrackAvailability = TrackAvailability.Playable, duration: Long? = 240_000L) = MusicTrack(
        MusicResourceId(source, "native-id"), "晴天", listOf(MusicArtistRef(name = "周杰伦")),
        durationMs = duration, availability = availability, bestQuality = tier,
    )

    @Test fun playableStandardBeatsSubscriptionLossless() {
        val playable = track(MusicSource.Netease, AudioQualityTier.Standard)
        val gated = track(MusicSource.QQMusic, AudioQualityTier.Lossless, TrackAvailability.SubscriptionRequired)
        assertEquals(playable, TrackAggregation.aggregate(listOf(gated, playable)).single().recommendation?.track)
    }

    @Test fun advertisedLosslessBeatsProviderPreferenceOnlyWhenAvailabilityIsEqual() {
        val high = track(MusicSource.QQMusic, AudioQualityTier.High)
        val lossless = track(MusicSource.Kuwo, AudioQualityTier.Lossless)
        assertEquals(lossless, TrackAggregation.aggregate(listOf(high, lossless)).single().recommendation?.track)
        assertEquals(high, TrackAggregation.aggregate(listOf(high, lossless.copy(availability = TrackAvailability.PreviewOnly)))
            .single().recommendation?.track)
    }

    @Test fun absentCatalogueHintsDoNotInventAProviderCeiling() {
        val known = track(MusicSource.Kuwo, AudioQualityTier.High)
        val unknown = track(MusicSource.QQMusic)
        val group = TrackAggregation.aggregate(listOf(unknown, known)).single()
        assertEquals(known, group.recommendation?.track)
        assertEquals(0, group.candidates.single { it.track == unknown }.qualityScore)
    }

    @Test fun durationSeparatesDifferentRecordingsAndToleratesMinorDrift() {
        val short = track(MusicSource.Netease, duration = 30_000L)
        val long = track(MusicSource.QQMusic)
        assertEquals(2, TrackAggregation.aggregate(listOf(short, long)).size)
        assertEquals(1, TrackAggregation.aggregate(listOf(long, short.copy(durationMs = 242_000L))).size)
        assertEquals(1, TrackAggregation.aggregate(listOf(long, short.copy(durationMs = null))).size)
    }

    @Test fun collaboratorOrderDeduplicatesButLiveAndStudioStaySeparate() {
        val a = track(MusicSource.Netease).copy(artists = listOf(MusicArtistRef(name = "周杰伦 / 费玉清")))
        val b = track(MusicSource.QQMusic).copy(artists = listOf(MusicArtistRef(name = "费玉清、周杰伦")))
        assertEquals(1, TrackAggregation.aggregate(listOf(a, b)).size)
        assertEquals(2, TrackAggregation.aggregate(listOf(a, b.copy(title = "晴天 (Live)"))).size)
    }

    @Test fun qqLegacyAndNewFormatShapesPreserveBestAdvertisedTierAndUnits() {
        assertEquals(AudioQualityTier.Lossless, qqMusicQualityHint(JSONObject(
            """{"file":{"size_128mp3":100,"size_320mp3":300,"size_flac":900}}"""))?.tier)
        val modern = qqMusicQualityHint(JSONObject(
            """{"file":{"size_new":[{"format":"mp3","bitrate":320},{"format":"flac","bitrate":1000}]}}"""))
        assertEquals(AudioQualityTier.Lossless, modern?.tier)
        assertEquals(1_000_000, modern?.bitrate)
        assertEquals(AudioQualityTier.Master, qqMusicQualityHint(JSONObject(
            """{"fileInfo":{"size_new":{"x":{"format":"master","br":1411}}}}"""))?.tier)
        assertEquals(AudioQualityTier.Immersive, qqMusicQualityHint(JSONObject(
            """{"songFile":{"size":[{"ext":"atmos","bitrate":768}]}}"""))?.tier)
        assertNull(qqMusicQualityHint(JSONObject("""{"name":"晴天"}""")))
    }

    @Test fun kugouSizesAndKuwoFormatListsAreParsedWithoutBitrateUnitConfusion() {
        assertEquals(AudioQualityTier.Lossless, kugouSearchQuality(JSONObject(
            """{"FileSize":"100","HQFileSize":"200","SQFileSize":"900"}"""))?.tier)
        assertNull(kugouSearchQuality(JSONObject("""{"SQFileSize":0}""")))
        val hint = kuwoSearchQuality(JSONObject().put("N_MINFO", "level:p,bitrate:320,format:mp3;level:ff,bitrate:1000,format:flac"))
        assertEquals(AudioQualityTier.Lossless, hint?.tier)
        assertEquals(1_000_000, hint?.bitrate)
        assertEquals(AudioQualityTier.High, kuwoSearchQuality(JSONObject().put("BITRATE", 320_000))?.tier)
        assertNull(kuwoSearchQuality(JSONObject()))
    }

    @Test fun neteaseSearchEmbeddedQualityIsOptionalAndKeepsNativeBps() {
        assertEquals(AudioQualityTier.HiResolution to 1_500_000, neteaseSearchQuality(JSONObject(
            """{"hr":{"br":1500000},"h":{"br":320000}}""")))
        assertEquals(AudioQualityTier.High to 320_000, neteaseSearchQuality(JSONObject(
            """{"quality":{"bitrate":320000}}""")))
        assertNull(neteaseSearchQuality(JSONObject()))
    }

    @Test fun qishuiCatalogueHintNeverChangesPlaybackAvailability() {
        val parsed = assertNotNull(QishuiParsing.track(JSONObject("""{"id":"q-real-id","name":"歌曲","max_bitrate":320}""")))
        assertEquals(320_000, parsed.bestBitrate)
        assertEquals(AudioQualityTier.High, parsed.bestQuality)
        assertEquals(TrackAvailability.Unknown, parsed.availability)
        assertEquals("q-real-id", parsed.id.value)
    }
}
