package melox.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.LyricsCapability
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.MusicProviderRegistry
import melox.music.provider.ThirdPartyMusicSourceConsentStore
import melox.provider.lxuser.LxUserSourceStore
import melox.settings.MeloXSettingsRuntime

class LxLyricsIntegrationTest {
    @Test fun consentAndSourceChangesInvalidateCachedProviderLyrics() = runBlocking {
        val oldEnabled = ThirdPartyMusicSourceConsentStore.enabled()
        val oldAutomatic = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val source = """
            // @name LX lyric fixture
            // @version 1
            globalThis.lx.on('request', ({source,action,info}) => {
              if (source !== 'tx' || action !== 'lyric' || info.musicInfo.songmid !== 'lyric-mid') throw Error('bad request');
              return {lyric:'[00:01.00]LX lyric'};
            });
            lx.send('inited', {sources:{tx:{type:'music',qualitys:[],actions:['lyric']}}});
        """.trimIndent()
        val record = LxUserSourceStore.importScript(source)
        var nativeCalls = 0
        val native = object : MusicProvider, LyricsCapability {
            override val source = MusicSource.QQMusic
            override val displayName = "Native"
            override val capabilities = setOf(MusicCapability.Lyrics)
            override suspend fun lyrics(track: MusicTrack): LyricsDocument {
                nativeCalls++
                return LrcLyricsParser.parse("[00:01.00]Native lyric")
            }
        }
        val registry = MusicProviderRegistry(listOf(native))
        val track = MusicTrack(MusicResourceId(MusicSource.QQMusic, "lyric-mid"), "song", listOf(MusicArtistRef(name = "artist")))
        val loader = MeloXLyricsLoad()
        try {
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = false
            ThirdPartyMusicSourceConsentStore.reject()
            assertEquals("Native lyric", loader.load(track, registry).lines.single().text)
            ThirdPartyMusicSourceConsentStore.accept()
            assertEquals("LX lyric", loader.load(track, registry).lines.single().text)
            ThirdPartyMusicSourceConsentStore.reject()
            assertEquals("Native lyric", loader.load(track, registry).lines.single().text)
            assertEquals(1, nativeCalls)
        } finally {
            LxUserSourceStore.remove(record.id)
            if (oldEnabled) ThirdPartyMusicSourceConsentStore.accept() else ThirdPartyMusicSourceConsentStore.reject()
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = oldAutomatic
        }
    }
}
