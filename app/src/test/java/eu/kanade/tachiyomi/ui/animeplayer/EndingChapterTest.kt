package eu.kanade.tachiyomi.ui.animeplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which chapter of a file is the ending, and where it ends.
 *
 * The button that skips it is offered only when something comes after the credits, so what
 * matters here is the pair of times: reading the wrong chapter as the ending either hides the
 * button or jumps out of the episode a scene early.
 */
class EndingChapterTest {

    private fun chapter(title: String?, start: Int) = ZenyomiMPVView.Chapter(title, start)

    @Test
    fun `the chapter named ending ends where the next one begins`() {
        val chapters = listOf(
            chapter("Episode", 0),
            chapter("Ending", 1200),
            chapter("Preview", 1290),
        )
        assertEquals(1200..1290, endingChapter(chapters, durationSeconds = 1440))
    }

    @Test
    fun `credits that run to the last frame end with the file`() {
        // Unlike an opening, a last chapter named this is the ordinary case: there is nothing
        // after the credits, and the duration is where they stop.
        val chapters = listOf(chapter("Episode", 0), chapter("ED", 1300))
        assertEquals(1300..1440, endingChapter(chapters, durationSeconds = 1440))
    }

    @Test
    fun `the short form counts, and so does any capitalisation`() {
        assertEquals(
            600..700,
            endingChapter(listOf(chapter("Part A", 0), chapter("ed", 600), chapter("Part B", 700)), 800),
        )
        assertEquals(
            600..700,
            endingChapter(listOf(chapter("Part A", 0), chapter("OUTRO", 600), chapter("Part B", 700)), 800),
        )
        assertEquals(
            600..700,
            endingChapter(listOf(chapter("Part A", 0), chapter("Credits", 600), chapter("Part B", 700)), 800),
        )
    }

    @Test
    fun `an opening is not an ending`() {
        val chapters = listOf(
            chapter("Avant", 0),
            chapter("Opening", 8),
            chapter("Episode", 38),
        )
        assertNull(endingChapter(chapters, durationSeconds = 1440))
    }

    @Test
    fun `a word that merely contains ed is not an ending`() {
        val chapters = listOf(
            chapter("Red dawn", 0),
            chapter("Predicament", 300),
        )
        assertNull(endingChapter(chapters, durationSeconds = 600))
    }

    @Test
    fun `an ending past the end of the file says nothing`() {
        // A duration mpv has not reported yet, or chapters that do not belong to this file.
        // Either way there is no interval to be had, and no button.
        assertNull(endingChapter(listOf(chapter("Episode", 0), chapter("Ending", 1300)), 0))
    }

    @Test
    fun `a file with no chapters, and one not read yet`() {
        assertNull(endingChapter(emptyList(), durationSeconds = 1440))
        assertNull(endingChapter(null, durationSeconds = 1440))
    }
}
