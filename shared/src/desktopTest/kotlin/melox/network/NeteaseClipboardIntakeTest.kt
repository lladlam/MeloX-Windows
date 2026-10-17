package melox.network

import melox.music.model.MusicSource
import kotlin.test.*

class NeteaseClipboardIntakeTest {
    @Test fun foregroundRescansDeduplicateCancelledRequestsButAcceptChangedClipboard() {
        val intake = NeteaseClipboardIntake()
        val song = "https://music.163.com/#/song?id=123"
        assertEquals(NeteaseClipboardRequest.Media(NeteaseClipboardTarget.Song(123)), intake.consume(" $song\n", true, MusicSource.Netease))
        assertNull(intake.consume(song, true, MusicSource.Netease))
        assertEquals(NeteaseClipboardRequest.Media(NeteaseClipboardTarget.Playlist(456)),
            intake.consume("https://music.163.com/playlist/456", true, MusicSource.Netease))
        assertNotNull(intake.consume(song, true, MusicSource.Netease))
    }

    @Test fun disabledScanDoesNotConsumeClipboardAndOtherProvidersDoNotLaunchNeteasePlayback() {
        val intake = NeteaseClipboardIntake()
        val song = "https://music.163.com/song?id=123"
        assertNull(intake.consume(song, false, MusicSource.Netease))
        assertNotNull(intake.consume(song, true, MusicSource.Netease))
        val other = NeteaseClipboardIntake()
        assertNull(other.consume(song, true, MusicSource.Local))
        assertNull(other.consume(song, true, MusicSource.Netease))
    }

    @Test fun invitationsPreserveRoomAndInviterAndTakePriorityOverSongIdAcrossProviders() {
        val intake = NeteaseClipboardIntake()
        val invitation = "一起听 https://st.music.163.com/listen-together/multishare/index.html?roomId=room%2B1&amp;inviterUid=42&amp;songId=123"
        assertEquals(NeteaseClipboardRequest.Together(MeloXListenTogetherInvitation("room+1", "42", 123)),
            intake.consume(invitation, true, MusicSource.Local))
        assertNull(intake.consume(invitation, true, MusicSource.Netease))
    }
}
