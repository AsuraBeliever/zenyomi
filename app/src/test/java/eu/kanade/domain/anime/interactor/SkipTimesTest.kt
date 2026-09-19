package eu.kanade.domain.anime.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * What the player is allowed to believe from AniSkip.
 *
 * The button seeks where this says, so everything that is not a clear pair of times has to
 * come back as "no idea": the fixed jump is a fine answer, and a seek to an invented second is
 * not.
 */
class SkipTimesTest {

    @Test
    fun `an episode with an opening and an ending`() {
        val body = """
            {"found":true,"results":[
              {"interval":{"startTime":32.5,"endTime":122.5},"skipType":"op","skipId":"x","episodeLength":1440.0},
              {"interval":{"startTime":1320.0,"endTime":1410.0},"skipType":"ed","skipId":"y","episodeLength":1440.0}
            ],"message":"","statusCode":200}
        """.trimIndent()
        val intervals = parseSkipTimes(body)
        assertEquals(32..122, intervals?.opening)
        assertEquals(1320..1410, intervals?.ending)
    }

    @Test
    fun `an episode with only an ending still answers`() {
        val body = """
            {"found":true,"results":[
              {"interval":{"startTime":1320.0,"endTime":1410.0},"skipType":"ed","skipId":"y","episodeLength":1440.0}
            ],"message":"","statusCode":200}
        """.trimIndent()
        val intervals = parseSkipTimes(body)
        assertNull(intervals?.opening)
        assertEquals(1320..1410, intervals?.ending)
    }

    @Test
    fun `nobody has timed this episode`() {
        assertNull(parseSkipTimes("""{"found":false,"results":[],"message":"","statusCode":404}"""))
    }

    @Test
    fun `found, but with nothing in it`() {
        assertNull(parseSkipTimes("""{"found":true,"results":[],"message":"","statusCode":200}"""))
    }

    @Test
    fun `an interval that goes nowhere is not an interval`() {
        val body = """
            {"found":true,"results":[
              {"interval":{"startTime":90.0,"endTime":90.0},"skipType":"op","skipId":"x","episodeLength":1440.0}
            ],"message":"","statusCode":200}
        """.trimIndent()
        assertNull(parseSkipTimes(body))
    }

    @Test
    fun `an answer that is not what we expect at all`() {
        assertNull(parseSkipTimes("<html>502 Bad Gateway</html>"))
        assertNull(parseSkipTimes(""))
    }

    @Test
    fun `times measured on this very copy still land a moment short`() {
        // Two seconds: a truncated second and a frame or two, not a guess about the copy.
        assertEquals(2, slackFor(timedLengthSeconds = 1440.0, ourLengthSeconds = 1440))
    }

    @Test
    fun `the further the timed copy is from this one, the earlier it lands`() {
        assertEquals(6, slackFor(timedLengthSeconds = 1444.0, ourLengthSeconds = 1440))
        assertEquals(6, slackFor(timedLengthSeconds = 1436.0, ourLengthSeconds = 1440))
        assertEquals(3, slackFor(timedLengthSeconds = 1439.4, ourLengthSeconds = 1440))
    }

    @Test
    fun `a copy that is wildly different says nothing about the opening`() {
        // A preview this stream does not carry, credits cut differently: capped, because the
        // difference is somewhere else in the episode.
        assertEquals(8, slackFor(timedLengthSeconds = 1400.0, ourLengthSeconds = 1440))
        assertEquals(8, slackFor(timedLengthSeconds = 1440.0, ourLengthSeconds = 1000))
    }

    @Test
    fun `nothing to compare is not a reason to land late`() {
        assertEquals(2, slackFor(timedLengthSeconds = null, ourLengthSeconds = 1440))
        assertEquals(2, slackFor(timedLengthSeconds = 1440.0, ourLengthSeconds = 0))
        assertEquals(2, slackFor(timedLengthSeconds = 0.0, ourLengthSeconds = 1440))
    }

    @Test
    fun `the slack comes back with the times`() {
        val body = """
            {"found":true,"results":[
              {"interval":{"startTime":32.5,"endTime":122.5},"skipType":"op","skipId":"x","episodeLength":1435.0}
            ],"message":"","statusCode":200}
        """.trimIndent()
        assertEquals(7, parseSkipTimes(body, ourLengthSeconds = 1440)?.openingSlack)
        // Asked without a length, there is still the length the answer was measured on.
        assertEquals(2, parseSkipTimes(body, ourLengthSeconds = 0)?.openingSlack)
    }
}
