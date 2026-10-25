package melox.playback

import melox.music.model.*
import melox.music.provider.*
import melox.remoteconfig.MeloXRemoteConfigDefaults
import kotlin.test.*

/** Protocol-stage fixtures assert observable ordering, opt-ins, and rejection of previews. */
class PlaybackFallbackOrderTest {
    private val calls = mutableListOf<String>()
    private val track = MusicTrack(MusicResourceId(MusicSource.Qishui, "7079108541549643812"), "Song", listOf(MusicArtistRef(name = "Artist")), durationMs = 200000)
    private class Provider(override val source: MusicSource, val calls: MutableList<String>, var result: PlaybackResolution) : MusicProvider, SearchCapability, PlaybackCapability {
        override val displayName = source.displayName
        override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback)
        override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
            calls += "${source.storageValue}:search"
            return MusicPage(listOf(MusicTrack(MusicResourceId(source, "native"), "Song", listOf(MusicArtistRef(name = "Artist")), durationMs = 200000)), page, pageSize)
        }
        override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
            calls += "${source.storageValue}:play"; return result
        }
    }
    private fun full(label: String) = PlaybackResolution.Playable("https://audio.invalid/$label", mapOf("Referer" to "https://origin.invalid/"), AudioQualityTier.Standard)
    private fun third(enabled: () -> Boolean = { true }, membershipOnly: () -> Boolean = { false }) =
        ThirdPartyPlaybackResolver(enabled, membershipOnly, listOf(ThirdPartySource { _, _ -> calls += "third"; ThirdPartyTrack("https://audio.invalid/third", sourceLabel = "fixture") }))
    private fun cross(providers: List<MusicProvider>, enabled: () -> Boolean = { true }) = CrossProviderPlaybackFallbackResolver(
        enabled, { MusicProviderRegistry(providers) }, fallbackConfigProvider = { MeloXRemoteConfigDefaults.Config.fallback.copy(order = listOf("qq_music", "kuwo", "qishui")) })
    private fun resolver(provider: Provider, cross: CrossProviderPlaybackFallbackResolver?, third: ThirdPartyPlaybackResolver) = ProviderPlaybackResolver(
        NeteasePlaybackResolver(), MusicProviderRegistry(listOf(provider)), crossProviderFallback = cross, thirdPartyPlayback = third)
    private fun uri() = ProviderPlaybackResolver.uriForTrack(track, AudioQualityTier.Standard)

    @Test fun aPlayableCurrentProviderNeverCallsFallbacks() {
        val native = Provider(MusicSource.Qishui, calls, full("native"))
        val result = resolver(native, cross(listOf(Provider(MusicSource.QQMusic, calls, full("qq")))), third()).resolveProviderRequest(uri())
        assertEquals("https://audio.invalid/native", result.uri); assertEquals(listOf("qishui:play"), calls)
    }
    @Test fun failedNativeUsesStrictBuiltInBeforeThirdPartyAndKeepsHeaders() {
        val native = Provider(MusicSource.Qishui, calls, PlaybackResolution.SubscriptionRequired)
        val result = resolver(native, cross(listOf(native, Provider(MusicSource.QQMusic, calls, full("qq")))), third()).resolveProviderRequest(uri())
        assertEquals("https://audio.invalid/qq", result.uri)
        assertEquals("https://origin.invalid/", result.headers["Referer"])
        assertEquals(listOf("qishui:play", "qq_music:search", "qq_music:play"), calls)
    }
    @Test fun configuredOrderIsSerialAndPreviewCandidatesDoNotWin() {
        val qq = Provider(MusicSource.QQMusic, calls, PlaybackResolution.Preview("https://audio.invalid/preview", 30000))
        val kuwo = Provider(MusicSource.Kuwo, calls, full("kuwo"))
        val native = Provider(MusicSource.Qishui, calls, PlaybackResolution.CopyrightRestricted)
        val result = resolver(native, cross(listOf(kuwo, native, qq)), third()).resolveProviderRequest(uri())
        assertEquals("https://audio.invalid/kuwo", result.uri)
        assertEquals(listOf("qishui:play", "qq_music:search", "qq_music:play", "kuwo:search", "kuwo:play"), calls)
    }
    @Test fun exhaustedBuiltInsUseThirdPartyLast() {
        val native = Provider(MusicSource.Qishui, calls, PlaybackResolution.Unavailable("missing"))
        val result = resolver(native, cross(listOf(Provider(MusicSource.QQMusic, calls, PlaybackResolution.Unavailable()))), third()).resolveProviderRequest(uri())
        assertEquals("https://audio.invalid/third", result.uri)
        assertEquals(listOf("qishui:play", "qq_music:search", "qq_music:play", "third"), calls)
    }
    @Test fun loginRequiredDoesNotFallThroughAnEntireQueue() {
        val native = Provider(MusicSource.Qishui, calls, PlaybackResolution.LoginRequired)
        assertFailsWith<java.io.IOException> { resolver(native, cross(emptyList()), third()).resolveProviderRequest(uri()) }
        assertEquals(listOf("qishui:play"), calls)
    }
    @Test fun fallbackSettingChangesInvalidateTheResolvedProviderCache() {
        var enabled = true
        val native = Provider(MusicSource.Qishui, calls, PlaybackResolution.Unavailable())
        val resolver = resolver(native, cross(listOf(Provider(MusicSource.QQMusic, calls, full("qq")))) { enabled }, third())
        assertEquals("https://audio.invalid/qq", resolver.resolveProviderRequest(uri()).uri)
        enabled = false
        assertEquals("https://audio.invalid/third", resolver.resolveProviderRequest(uri()).uri)
    }
    @Test fun disabledOrMembershipOnlyThirdPartyStagesRespectTheirSwitches() {
        assertNull(third({ false }).resolve(track, AudioQualityTier.Standard, true))
        assertNull(third(membershipOnly = { true }).resolve(track, AudioQualityTier.Standard, false))
        assertTrue(calls.isEmpty())
        assertNotNull(third(membershipOnly = { true }).resolve(track, AudioQualityTier.Standard, true))
    }
    @Test fun aFailingConfiguredThirdPartySourceDoesNotHideTheNextOne() {
        val sources = listOf(ThirdPartySource { _, _ -> calls += "chksz"; error("source unavailable") },
            ThirdPartySource { _, _ -> calls += "lx"; ThirdPartyTrack("https://audio.invalid/lx", sourceLabel = "lx") })
        assertNotNull(ThirdPartyPlaybackResolver({ true }, { false }, sources).resolve(track, AudioQualityTier.Standard, true))
        assertEquals(listOf("chksz", "lx"), calls)
    }
    @Test fun builtInStageFailureStillAllowsTheConfiguredFinalStage() {
        val failed = CrossProviderPlaybackFallbackResolver({ true }, { error("registry unavailable") })
        val result = PlaybackFallbackChain(failed, third()).resolve(track, AudioQualityTier.Standard, true)
        assertEquals("https://audio.invalid/third", assertNotNull(result).url)
        assertEquals(listOf("third"), calls)
    }
}
