package melox.playback

import melox.music.model.MusicResourceId
import melox.music.model.MusicSource

/**
 * Track IDs used by new providers. A legacy numeric ID is intentionally parsed
 * as NetEase so existing queues and every iOS-migrated NetEase path keep working.
 */
object PlaybackTrackIdentity {
    private const val Prefix = "melox:"
    const val SourceExtra = "melox.provider.source"
    const val ResourceIdExtra = "melox.provider.resource_id"
    const val DurationMsExtra = "melox.track.duration_ms"
    const val TitleExtra = "melox.track.title"
    const val ArtistExtra = "melox.track.artist"
    const val AlbumExtra = "melox.track.album"
    const val ArtworkExtra = "melox.track.artwork"

    fun encode(id: MusicResourceId): String =
        "${Prefix}${id.source.storageValue}:${id.value}"

    fun decode(mediaId: String): MusicResourceId? {
        mediaId.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MusicResourceId(MusicSource.Netease, it.toString())
        }
        if (!mediaId.startsWith(Prefix)) return null
        val payload = mediaId.removePrefix(Prefix)
        val separator = payload.indexOf(':')
        if (separator <= 0 || separator >= payload.lastIndex) return null
        val source = MusicSource.entries.firstOrNull {
            it.storageValue == payload.substring(0, separator)
        } ?: return null
        val value = payload.substring(separator + 1).trim()
        return value.takeIf(String::isNotBlank)?.let { MusicResourceId(source, it) }
    }

    fun fromMediaItem(item: Any?): MusicResourceId? {
        item ?: return null
        // Desktop player uses a simple track model; extras are not available.
        // Fall back to decoding the media ID string.
        val mediaId = (item as? String) ?: return null
        return decode(mediaId)
    }

    fun neteaseNumericId(item: Any?): Long? =
        fromMediaItem(item)
            ?.takeIf { it.source == MusicSource.Netease }
            ?.value
            ?.toLongOrNull()
}