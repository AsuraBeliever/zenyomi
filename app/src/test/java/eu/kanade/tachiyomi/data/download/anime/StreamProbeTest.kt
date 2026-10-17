package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ffprobe's report has to go to a file. Logged, it shares libavutil's single log callback with
 * mpv, which takes it over once a player has started — and every download after that failed
 * its check on an empty report.
 */
class StreamProbeTest {

    @Test
    fun `the report is written to the file, ahead of the input`() {
        val arguments = StreamProbe.reportTo("/cache/probe1.json", listOf("-v", "error", "-i", "saf:3.mkv")).toList()

        assertEquals(listOf("-o", "/cache/probe1.json"), arguments.take(2))
        assertEquals(listOf("-v", "error", "-i", "saf:3.mkv"), arguments.drop(2))
    }
}
