package eu.kanade.presentation.anime.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The number an episode row shows where a chapter row says "Page 12". It read "0:00" for
 * every position an episode could have, because it divided a value already in seconds by a
 * thousand — and "0:00" is indistinguishable from progress that was never saved.
 */
class EpisodePositionTest {

    @Test
    fun `an episode barely started`() {
        assertEquals("0:05", formatEpisodePosition(5))
    }

    @Test
    fun `the value that used to come out as zero`() {
        assertEquals("12:34", formatEpisodePosition(754))
    }

    @Test
    fun `seconds are padded, minutes are not`() {
        assertEquals("1:05", formatEpisodePosition(65))
        assertEquals("9:09", formatEpisodePosition(549))
    }

    @Test
    fun `the last second before an hour stays in minutes`() {
        assertEquals("59:59", formatEpisodePosition(3599))
    }

    @Test
    fun `and the first second of one grows the hours field`() {
        assertEquals("1:00:00", formatEpisodePosition(3600))
        assertEquals("1:02:03", formatEpisodePosition(3723))
    }

    @Test
    fun `a feature-length entry`() {
        assertEquals("12:00:00", formatEpisodePosition(43200))
    }

    @Test
    fun `zero, which no row actually shows, still formats rather than throwing`() {
        assertEquals("0:00", formatEpisodePosition(0))
    }

    @Test
    fun `a negative position reads as the start, not as a mangled clock`() {
        // Nothing should write one, but "%d:%02d" on a negative would print "-1:-30".
        assertEquals("0:00", formatEpisodePosition(-90))
    }
}
