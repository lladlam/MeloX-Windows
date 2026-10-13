package melox.playback

import melox.model.SearchSong
import melox.music.model.*
import melox.music.provider.MeloXLegacyUiBridge
import kotlin.test.*

class DesktopSongOperationIdentityTest {
    @Test fun bridgedNeteaseWritesUseNativeIdAndForeignNumericIdsNeverReachNetease() {
        val native = MusicTrack(MusicResourceId(MusicSource.Netease, "12345"), "Song", emptyList())
        val bridged = MeloXLegacyUiBridge.track(native)
        assertTrue(bridged.id < 0)
        assertEquals(12345L, bridged.neteaseSongId)
        assertEquals(12345L, bridged.copy(id = 12345).neteaseSongId)
        for (source in MusicSource.entries.filter { it != MusicSource.Netease }) {
            assertNull(MeloXLegacyUiBridge.track(native.copy(id = MusicResourceId(source, "12345"))).neteaseSongId)
            assertNull(bridged.copy(id = 12345, providerTrack = native.copy(id = MusicResourceId(source, "12345"))).neteaseSongId)
        }
        assertNull(bridged.copy(providerTrack = native.copy(id = MusicResourceId(MusicSource.Netease, "invalid"))).neteaseSongId)
        assertEquals(12345L, SearchSong(12345, "Song", "", "", null).neteaseSongId)
        assertNull(SearchSong(-1, "Song", "", "", null).neteaseSongId)
    }
}
