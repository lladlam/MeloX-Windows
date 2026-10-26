package melox.music

import kotlinx.coroutines.*
import melox.music.model.*
import melox.music.provider.*
import melox.platform.getPreferences
import melox.playback.MeloXPlaybackQueueStore
import org.json.JSONObject
import java.util.UUID
import kotlin.test.*

class UnifiedFavoritesTest {
    private fun prefs() = getPreferences("unified_favorites_test_${UUID.randomUUID()}")
    private fun track(source: MusicSource = MusicSource.QQMusic, title: String = "歌曲", tier: AudioQualityTier = AudioQualityTier.High) = MusicTrack(
        MusicResourceId(source, "native-${source.storageValue}"), title,
        listOf(MusicArtistRef(MusicResourceId(source, "artist"), "歌手")),
        MusicAlbumRef(MusicResourceId(source, "album"), "专辑", "https://example.test/album.jpg"),
        "https://example.test/track.jpg", 180_000L, TrackAvailability.Playable,
        ProviderTrackMetadata.QQMusic("native-qq", "media-mid", 42), tier, 320_000,
    )

    @Test fun everyProviderMetadataAndNativeIdentitySurvivesCodecAndQueueReload() {
        val values = listOf(
            MusicSource.Netease to ProviderTrackMetadata.Netease(42),
            MusicSource.QQMusic to ProviderTrackMetadata.QQMusic("mid", "media", 42),
            MusicSource.Kugou to ProviderTrackMetadata.Kugou("hash", 91, "album"),
            MusicSource.Kuwo to ProviderTrackMetadata.Kuwo(42),
            MusicSource.AppleMusic to ProviderTrackMetadata.AppleMusic("catalog", "jp", "https://example.test/preview.m4a"),
            MusicSource.Bilibili to ProviderTrackMetadata.Bilibili("BVnative", 42, 93, 2),
            MusicSource.Spotify to ProviderTrackMetadata.Spotify("spotify-id", "ISRC"),
            MusicSource.YouTubeMusic to ProviderTrackMetadata.YouTube("video", "set-video"),
            MusicSource.Local to ProviderTrackMetadata.Local("file:///music/local.wav", "file-key"),
            MusicSource.Jellyfin to ProviderTrackMetadata.Jellyfin("https://server.test", "playlist", "entry"),
            MusicSource.Qishui to ProviderTrackMetadata.Empty,
        )
        for ((source, metadata) in values) {
            val original = track(source).copy(providerMetadata = metadata)
            assertEquals(original, MeloXTrackJson.decode(JSONObject(MeloXTrackJson.encode(original).toString())))
            val ui = MeloXLegacyUiBridge.track(original)
            assertEquals(original, MeloXPlaybackQueueStore.songFromJson(MeloXPlaybackQueueStore.songToJson(ui))?.providerTrack)
        }
        assertNull(MeloXTrackJson.decode(JSONObject("""{"source":"unknown-source","id":"1","title":"title"}""")))
    }

    @Test fun initializeReportsPartialFailureAndKeepsRealSuccessfulSourceData() = runBlocking {
        val preferences = prefs(); val store = UnifiedFavoritesStore(preferences)
        assertFalse(store.enabled())
        val imported = track()
        val report = store.initialize(setOf(MusicSource.QQMusic, MusicSource.Netease)) {
            if (it == MusicSource.Netease) error("not signed in") else listOf(imported)
        }
        assertEquals(1, report.total); assertEquals(MusicSource.Netease, report.failures.single().source)
        val reopened = UnifiedFavoritesStore(preferences)
        assertTrue(reopened.enabled()); assertEquals(listOf(imported), reopened.tracks())
        reopened.setEnabled(false)
        assertFalse(reopened.enabled()); assertEquals(listOf(imported), reopened.tracks())
        reopened.setEnabled(true); assertTrue(reopened.contains(imported))
    }

    @Test fun lowerQualityCrossSourceAddKeepsHigherQualityIdentityAndRemovalUsesLogicalSong() = runBlocking {
        val store = UnifiedFavoritesStore(prefs())
        store.initialize(emptySet()) { emptyList() }
        val lossless = track(MusicSource.Kuwo, tier = AudioQualityTier.Lossless).copy(providerMetadata = ProviderTrackMetadata.Kuwo(42))
        val high = track()
        store.set(lossless, true); store.set(high, true)
        assertEquals(lossless.id, store.tracks().single().id)
        assertTrue(store.contains(high))
        store.set(high, false); assertTrue(store.tracks().isEmpty())
    }

    @Test fun reorderAndConcurrentAddsCommitWithoutLosingExistingRecords() = runBlocking {
        val preferences = prefs(); val store = UnifiedFavoritesStore(preferences)
        store.initialize(emptySet()) { emptyList() }
        val tracks = (0 until 20).map { index -> track(title = "Song $index").copy(id = MusicResourceId(MusicSource.QQMusic, "id-$index")) }
        withContext(Dispatchers.Default) { tracks.map { track -> async { store.set(track, true) } }.awaitAll() }
        assertEquals(tracks.map { it.id }.toSet(), store.tracks().map { it.id }.toSet())
        store.reorder(tracks.last(), 0)
        assertEquals(tracks.last(), UnifiedFavoritesStore(preferences).tracks().first())
    }

    @Test fun cancelledInitializationDoesNotEnableOrPersistPartialImports() = runBlocking {
        val store = UnifiedFavoritesStore(prefs()); val started = CompletableDeferred<Unit>()
        val job = launch { store.initialize(setOf(MusicSource.QQMusic)) { started.complete(Unit); awaitCancellation() } }
        started.await(); job.cancelAndJoin()
        assertFalse(store.initialized()); assertFalse(store.enabled()); assertTrue(store.tracks().isEmpty())
    }

    @Test fun malformedPersistedDataIsPreservedInsteadOfOverwrittenByEmptyInitialization() = runBlocking {
        val preferences = prefs(); val original = """{"initialized":true,"enabled":true,"tracks":[{"source":"future-source","id":"id","title":"title"}]}"""
        preferences.putString("state", original)
        val store = UnifiedFavoritesStore(preferences)
        assertFailsWith<IllegalArgumentException> { store.initialize(emptySet()) { emptyList() } }
        assertEquals(original, preferences.getString("state", null))
    }

    @Test fun providerCannotImportForeignIdentityAndLegacyQueueStillLoads() = runBlocking {
        val store = UnifiedFavoritesStore(prefs())
        val result = store.initialize(setOf(MusicSource.Netease)) { listOf(track(MusicSource.QQMusic)) }
        assertEquals(0, result.total); assertEquals(1, result.failures.size)
        val legacy = JSONObject("""{"id":"-93","title":"Old","artist":"Artist","providerSource":"kugou","providerId":"old-hash","durationMs":180000}""")
        assertEquals(MusicResourceId(MusicSource.Kugou, "old-hash"), MeloXPlaybackQueueStore.songFromJson(legacy)?.providerTrack?.id)
    }
}
