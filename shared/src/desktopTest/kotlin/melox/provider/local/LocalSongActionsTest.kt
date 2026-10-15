package melox.provider.local

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.platform.getPreferences
import kotlin.test.*

class LocalSongActionsTest {
    @BeforeTest fun clear() { getPreferences("melox_local_music").clear() }
    @AfterTest fun cleanup() = clear()

    @Test fun favoriteAndPlaylistWritesSurviveRepositoryRecreationAndRescan() = runBlocking<Unit> {
        val repository = LocalMusicRepository()
        repository.replaceTracks((1..3).map { index -> LocalTrackRecord("key-$index", "file:///tone-$index.wav", "tone-$index.wav",
            "tone-$index", "generated", "", 30_000, "audio/wav", 100, 0) })
        val provider = LocalProvider(repository = repository)
        val tracks = provider.searchSongs("").items
        val playlist = provider.createPlaylist("本地歌单")
        tracks.forEach { provider.addTrackToPlaylist(it, playlist) }
        provider.addTrackToPlaylist(tracks.first(), playlist)
        provider.reorderPlaylistTrack(playlist, tracks.last(), 0)
        provider.setFavorite(tracks.first(), true)
        repository.replaceTracks(repository.tracks().map { it.copy(isFavorite = false) })
        val reloaded = LocalProvider(repository = LocalMusicRepository())
        assertTrue(reloaded.isFavorite(tracks.first()))
        assertEquals(listOf(tracks[2].id, tracks[0].id, tracks[1].id), reloaded.playlistDetail(playlist).tracks.map { it.id })
        reloaded.removeTrackFromPlaylist(tracks[0], playlist)
        reloaded.renamePlaylist(playlist, "已重命名")
        assertEquals("已重命名", reloaded.writablePlaylists().items.single().title)
        assertEquals(listOf(tracks[2].id, tracks[1].id), reloaded.playlistDetail(playlist).tracks.map { it.id })
        assertFailsWith<IllegalArgumentException> { reloaded.setFavorite(tracks.first().copy(id = MusicResourceId(MusicSource.Netease, "key-1")), false) }
        reloaded.deletePlaylist(playlist)
        assertTrue(LocalMusicRepository().playlists().isEmpty())
    }
}
