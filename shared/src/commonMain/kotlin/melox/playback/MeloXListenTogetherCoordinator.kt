package melox.playback

import melox.platform.logInfo
import melox.platform.logWarn
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop stub for the Android MediaSession-based listen-together coordinator.
 *
 * The desktop player uses a simple in-process coordinator instead of the
 * full MediaSession service. Listen-together features are preserved as
 * no-op hooks for parity with Android.
 */
class MeloXListenTogetherCoordinator {
    private val rooms = ConcurrentHashMap<String, ListenTogetherRoom>()

    fun createRoom(roomId: String): ListenTogetherRoom {
        val room = ListenTogetherRoom(roomId)
        rooms[roomId] = room
        logInfo("MeloXListenTogether", "Created room: $roomId")
        return room
    }

    fun roomOrNull(roomId: String): ListenTogetherRoom? = rooms[roomId]

    fun leaveRoom(roomId: String) {
        rooms.remove(roomId)
        logInfo("MeloXListenTogether", "Left room: $roomId")
    }

    fun clear() {
        rooms.clear()
    }
}

data class ListenTogetherRoom(
    val roomId: String,
    val participants: MutableList<String> = mutableListOf(),
) {
    fun addParticipant(userId: String) {
        if (participants.contains(userId).not()) participants.add(userId)
    }

    fun removeParticipant(userId: String) {
        participants.remove(userId)
    }
}