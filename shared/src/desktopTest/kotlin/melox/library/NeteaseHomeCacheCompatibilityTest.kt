package melox.library

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import melox.network.MeloXAlbumSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeteaseHomeCacheCompatibilityTest {
    @Test fun preAlbumCacheStillLoadsAndDoesNotInventMissingMetadata() = runBlocking {
        val directory = Files.createTempDirectory("melox-home-old-cache").toFile()
        try {
            directory.resolve("home_${"legacy".hashCode().toUInt().toString(16)}.json").writeText("""{"playlists":[{"id":1,"name":"旧歌单","creatorName":"","trackCount":0}],"newSongs":[]}""")
            val loaded = assertNotNull(NeteaseLibraryCache(directory).loadHomeContent("legacy"))
            assertEquals("旧歌单", loaded.playlists.single().name)
            assertNull(loaded.playlists.single().copywriter)
            assertTrue(loaded.newAlbums.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun newHomeMetadataSurvivesNewCacheInstanceIncludingSparseAlbums() = runBlocking {
        val directory = Files.createTempDirectory("melox-home-albums").toFile()
        try {
            val content = NeteaseHomeContent(
                playlists = listOf(NeteasePlaylistSummary(1, "公开标题", null, 0, "", copywriter = "为你推荐 · 新歌")),
                newSongs = emptyList(),
                newAlbums = listOf(MeloXAlbumSummary(2, "新专辑", "https://example.invalid/art.jpg", "歌手", "Album", 1_700_000_000_000, 12),
                    MeloXAlbumSummary(3, "稀疏元数据", null, "", null)),
            )
            NeteaseLibraryCache(directory).saveHomeContent("roundtrip", content)
            assertEquals(content, NeteaseLibraryCache(directory).loadHomeContent("roundtrip"))
        } finally { directory.deleteRecursively() }
    }
}
