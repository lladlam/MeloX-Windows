package melox.playback

import melox.music.model.AudioQualityTier
import melox.music.model.MusicTrack
import melox.platform.logWarn

internal data class ThirdPartyTrack(val url: String, val requestHeaders: Map<String, String> = emptyMap(), val sourceLabel: String)
internal fun interface ThirdPartySource {
    fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack?
    fun cacheIdentity(): String = ""
}

/** Final, separately opted-in stage; a failing configured source cannot hide later sources. */
class ThirdPartyPlaybackResolver internal constructor(
    private val enabledProvider: () -> Boolean,
    private val membershipOnlyProvider: () -> Boolean,
    private val sources: List<ThirdPartySource>,
) {
    constructor(
        enabledProvider: () -> Boolean,
        membershipOnlyProvider: () -> Boolean = { false },
        chksz: ChkszPlaybackResolver? = null,
        lx: LxUserPlaybackResolver? = null,
    ) : this(enabledProvider, membershipOnlyProvider, listOfNotNull(
        chksz?.let { resolver -> object : ThirdPartySource {
            override fun resolve(track: MusicTrack, quality: AudioQualityTier) = resolver.resolve(track, quality)?.let { ThirdPartyTrack(it.url, sourceLabel = "chksz") }
            override fun cacheIdentity() = "chksz:${resolver.cacheIdentity()}"
        } },
        lx?.let { resolver -> object : ThirdPartySource {
            override fun resolve(track: MusicTrack, quality: AudioQualityTier) = resolver.resolve(track, quality)?.let { ThirdPartyTrack(it.url, it.requestHeaders, "lx:${it.sourceId}") }
            override fun cacheIdentity() = "lx:${resolver.cacheIdentity()}"
        } },
    ))

    fun cacheIdentity() = if (enabledProvider()) "${membershipOnlyProvider()}:${sources.joinToString("|") { it.cacheIdentity() }}" else "disabled"

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier, membershipFailure: Boolean): ThirdPartyTrack? {
        if (!enabledProvider() || track.title.isBlank() || membershipOnlyProvider() && !membershipFailure) return null
        for (source in sources) {
            val result = runCatching { source.resolve(track, quality) }
                .onFailure { logWarn("MeloXThirdParty", "Source failed: ${it.javaClass.simpleName}") }.getOrNull() ?: continue
            if (result.url.startsWith("https://") || result.url.startsWith("http://")) return result
        }
        return null
    }
}
