package melox.player

import java.lang.reflect.Proxy
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine
import kotlin.test.*

class DesktopPcmOutputTest {
    @Test fun missingDefaultOutputTriesAnotherRealCandidateWithTheExactDecoderFormat() {
        val fixture = LineFixture()
        val output = MeloXPcmOutput.openCandidates(listOf(
            "unavailable default" to { throw IllegalArgumentException("No default line") },
            "available speaker" to { fixture.line },
            "unused device" to { error("Must not open extra devices after success") },
        ))
        assertSame(fixture.line, output)
        assertEquals(44100f, fixture.openedFormat?.sampleRate)
        assertEquals(16, fixture.openedFormat?.sampleSizeInBits)
        assertEquals(2, fixture.openedFormat?.channels)
        assertEquals(false, fixture.openedFormat?.isBigEndian)
        assertEquals(44100 * 4, fixture.requestedBuffer)
        assertEquals(0, fixture.closes)
    }

    @Test fun rejectedOpenClosesThePartiallyCreatedDeviceBeforeTryingTheNextOutput() {
        val failed = LineFixture(failOpen = true)
        val working = LineFixture()
        assertSame(working.line, MeloXPcmOutput.openCandidates(listOf("busy" to { failed.line }, "working" to { working.line })))
        assertEquals(1, failed.closes)
        assertEquals(0, working.closes)
    }

    @Test fun exhaustionKeepsBothGenuineDeviceErrorsAndDoesNotReturnAnUnopenedLine() {
        val first = LineFixture(failOpen = true)
        val error = assertFailsWith<java.io.IOException> {
            MeloXPcmOutput.openCandidates(listOf("busy" to { first.line }, "missing" to { throw IllegalArgumentException("No output") }))
        }
        assertTrue(error.cause is javax.sound.sampled.LineUnavailableException)
        assertEquals("No output", error.suppressed.single().message)
        assertEquals(1, first.closes)
    }

    /** Explicit test line; this is not a physical-output or audibility acceptance. */
    private class LineFixture(val failOpen: Boolean = false) {
        var openedFormat: AudioFormat? = null
        var requestedBuffer = 0
        var closes = 0
        val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "open" -> {
                    openedFormat = args!![0] as AudioFormat
                    requestedBuffer = args[1] as Int
                    if (failOpen) throw javax.sound.sampled.LineUnavailableException("Device busy")
                    null
                }
                "close" -> { closes++; null }
                "getBufferSize" -> requestedBuffer
                "toString" -> "validation-only line fixture"
                else -> null
            }
        } as SourceDataLine
    }
}
