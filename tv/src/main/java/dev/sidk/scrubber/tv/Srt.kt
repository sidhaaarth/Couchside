package dev.sidk.scrubber.tv

data class Cue(val startMs: Long, val text: String)

object Srt {
    fun parse(srt: String): List<Cue> =
        srt.replace("\r", "").split(Regex("\n\\s*\n")).mapNotNull { block ->
            val lines = block.trim().lines()
            val timing = lines.indexOfFirst { "-->" in it }
            if (timing < 0) return@mapNotNull null
            Cue(toMs(lines[timing].substringBefore("-->").trim()), lines.drop(timing + 1).joinToString(" "))
        }

    /** Everything said up to [positionMs] — never past it, so the recap can't spoil what's next. */
    fun transcriptUpTo(cues: List<Cue>, positionMs: Long): String =
        cues.filter { it.startMs <= positionMs }.joinToString("\n") { "[${clock(it.startMs)}] ${it.text}" }

    fun clock(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

    private fun toMs(t: String): Long {
        val (h, m, rest) = t.split(":")
        val (s, ms) = rest.split(",")
        return ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong()
    }
}
