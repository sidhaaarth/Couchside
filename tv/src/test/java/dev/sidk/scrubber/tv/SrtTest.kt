package dev.sidk.scrubber.tv

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SrtTest {
    private val cues = Srt.parse(File("src/main/assets/charade-en.srt").readText())

    @Test
    fun parsesEveryCueWithTiming() {
        assertEquals(1663, cues.size)
        assertEquals(Cue(7 * 60_000 + 19_199L, "don't tell me you didn't know it was"), cues[0])
        assertEquals(((1 * 60 + 56) * 60 + 35) * 1000 + 679L, cues.last().startMs)
    }

    @Test
    fun multilineCueTextIsJoined() {
        assertEquals(listOf(Cue(1_000, "Hello there friend")), Srt.parse("1\n00:00:01,000 --> 00:00:02,000\nHello there\nfriend\n"))
    }

    @Test
    fun transcriptStopsAtCurrentPosition() {
        val t = Srt.transcriptUpTo(cues, 7 * 60_000 + 27_000L)
        assertEquals("[7:19] don't tell me you didn't know it was\n[7:26] can't he do something constructive like", t)
        assertEquals("", Srt.transcriptUpTo(cues, 60_000))
    }

    @Test
    fun clockKeepsMinutesPastAnHour() {
        assertEquals("116:35", Srt.clock(((1 * 60 + 56) * 60 + 35) * 1000L))
    }
}
