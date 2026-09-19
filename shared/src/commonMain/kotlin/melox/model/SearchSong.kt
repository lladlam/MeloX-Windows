package melox.model

import melox.music.model.MusicTrack

data class SearchSong(
    val id: Long,
    val name: String,
    val artists: String,
    val album: String,
    val artworkUrl: String?,
    val durationMs: Long = 0L,
    /**
     * Optional provider-neutral backing track.
     *
     * This is deliberately carried by the legacy MeloX UI model instead of
     * teaching Compose screens about QQ/Kugou. Existing MeloX renderers,
     * transitions and backgrounds can therefore stay unchanged while playback
     * and data loading are dispatched by the bridge/data layer.
     */
    val providerTrack: MusicTrack? = null,
) {
    val playbackUrl: String
        get() = "https://music.163.com/song/media/outer/url?id=$id"
}
