package melox.network

import melox.music.model.MusicSource

sealed interface NeteaseClipboardRequest {
    data class Media(val target: NeteaseClipboardTarget) : NeteaseClipboardRequest
    data class Together(val invitation: MeloXListenTogetherInvitation) : NeteaseClipboardRequest
}

/** Pinned MainActivity.onResume: process-local dedup, invitations before songId matching. */
class NeteaseClipboardIntake {
    private var lastText = ""

    fun consume(text: String?, enabled: Boolean, source: MusicSource): NeteaseClipboardRequest? {
        if (!enabled) return null
        val normalized = text?.trim().orEmpty()
        if (normalized.isBlank() || normalized == lastText) return null
        lastText = normalized
        parseNeteaseListenTogetherInvitation(normalized)?.let { return NeteaseClipboardRequest.Together(it) }
        if (source != MusicSource.Netease) return null
        return NeteaseClipboardLink.parse(normalized)?.let(NeteaseClipboardRequest::Media)
    }
}
