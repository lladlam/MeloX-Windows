package melox.provider.lxuser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import melox.music.model.MusicAlbumRef
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata

/** Official LX scripts destructure these fields from the top-level musicInfo. */
class LxUserScriptContractTest {
    @Test fun qqMusicInfoIncludesFlatMediaAndQualityFields() {
        val track = MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, "004CyPQR03h5vU"),
            title = "晴天", artists = listOf(MusicArtistRef(name = "周杰伦")),
            album = MusicAlbumRef(MusicResourceId(MusicSource.QQMusic, "002pUZT93VpqJm"), "叶惠美"),
            artworkUrl = "https://img.example/cover.jpg", durationMs = 269_000,
            providerMetadata = ProviderTrackMetadata.QQMusic("004CyPQR03h5vU", "0035zfnZ1LwQPU", 97770L),
        )
        val info = buildLxMusicInfo(track, "tx", "flac")
        assertEquals("004CyPQR03h5vU", info["songmid"])
        assertEquals("0035zfnZ1LwQPU", info["strMediaMid"])
        assertEquals(97770L, info["songId"])
        assertEquals("002pUZT93VpqJm", info["albumMid"])
        assertEquals("002pUZT93VpqJm", info["albumId"])
        assertEquals("叶惠美", info["albumName"])
        assertEquals("https://img.example/cover.jpg", info["img"])
        assertEquals("04:29", info["interval"])
        assertEquals("tx", info["source"])
        assertEquals(listOf(mapOf("type" to "flac", "size" to null)), info["types"])
        assertTrue((info["_types"] as Map<*, *>).containsKey("flac"))
        val meta = info["meta"] as Map<*, *>
        assertEquals("0035zfnZ1LwQPU", meta["strMediaMid"])
        assertEquals(97770L, meta["id"])
    }

    @Test fun sourceIdsAndKugouMetadataAreNotLost() {
        val qq = MusicTrack(MusicResourceId(MusicSource.QQMusic, "mid"), "song", listOf(MusicArtistRef(name = "a")),
            providerMetadata = ProviderTrackMetadata.QQMusic("mid"))
        assertEquals("mid", buildLxMusicInfo(qq, "tx", "320k")["strMediaMid"])
        assertEquals("mid", buildLxMusicInfo(qq, "tx", "320k")["songId"])
        for (source in listOf(MusicSource.Kugou, MusicSource.KugouLite)) {
            val kg = MusicTrack(MusicResourceId(source, "hash"), "song", listOf(MusicArtistRef(name = "a")),
                durationMs = 65_000, providerMetadata = ProviderTrackMetadata.Kugou("hash", albumId = "88877"))
            val info = buildLxMusicInfo(kg, "kg", "128k")
            assertEquals("hash", info["hash"])
            assertEquals("hash", info["songmid"])
            assertEquals("88877", info["albumId"])
            assertEquals("01:05", info["interval"])
        }
        val kuwo = MusicTrack(MusicResourceId(MusicSource.Kuwo, "91734960"), "song", emptyList(),
            providerMetadata = ProviderTrackMetadata.Kuwo(91734960L))
        assertEquals(91734960L, buildLxMusicInfo(kuwo, "kw", "128k")["songId"])
        val wy = MusicTrack(MusicResourceId(MusicSource.Netease, "186016"), "song", emptyList())
        val info = buildLxMusicInfo(wy, "wy", "320k")
        assertEquals("186016", info["songmid"])
        assertEquals("", info["img"])
        assertFalse(info.containsKey("strMediaMid"))
    }

    @Test fun responseHeadersAreLowercaseAndStringValued() {
        val result = flattenLxResponseHeaders(mapOf("Set-Cookie" to listOf("a=1", "b=2"),
            "Content-Type" to listOf("application/json"), "X-Empty" to emptyList(), "content-type" to listOf("text/plain")))
        assertEquals(mapOf("set-cookie" to "a=1, b=2", "content-type" to "application/json, text/plain"), result)
    }
}
