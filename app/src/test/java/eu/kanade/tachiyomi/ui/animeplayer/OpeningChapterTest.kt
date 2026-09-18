package eu.kanade.tachiyomi.ui.animeplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which chapter of a file is the opening, and where it ends.
 *
 * The skip button jumps a fixed length when it has to guess and jumps exactly when the file
 * says where the opening ends — so everything here is about not mistaking one chapter for
 * another. A title read wrongly is a button that skips a minute and a half of the episode.
 */
class OpeningChapterTest {

    private fun chapter(title: String?, start: Int) = ZenyomiMPVView.Chapter(title, start)

    @Test
    fun `the chapter named opening ends where the next one begins`() {
        val chapters = listOf(
            chapter("Avant", 0),
            chapter("Opening", 8),
            chapter("Episode", 38),
        )
        assertEquals(8..38, openingChapter(chapters))
    }

    @Test
    fun `the short form counts, and so does any capitalisation`() {
        assertEquals(
            60..150,
            openingChapter(listOf(chapter("Part A", 0), chapter("op", 60), chapter("Part B", 150))),
        )
        assertEquals(
            60..150,
            openingChapter(listOf(chapter("Part A", 0), chapter("OPENING", 60), chapter("Part B", 150))),
        )
    }

    @Test
    fun `an avant is not an opening`() {
        // The cold open before the opening. Read as one, the button would skip the scene the
        // episode starts with and then play the opening anyway.
        val chapters = listOf(
            chapter("Avant", 0),
            chapter("Episode", 90),
        )
        assertNull(openingChapter(chapters))
    }

    @Test
    fun `an ending is not an opening`() {
        val chapters = listOf(
            chapter("Episode", 0),
            chapter("Ending", 1200),
            chapter("Preview", 1290),
        )
        assertNull(openingChapter(chapters))
    }

    @Test
    fun `a word that merely contains op is not an opening`() {
        val chapters = listOf(
            chapter("Operation begins", 0),
            chapter("Stop", 300),
        )
        assertNull(openingChapter(chapters))
    }

    @Test
    fun `an opening with nothing after it says nothing`() {
        // Where it ends is the next chapter's start. Skipping to the end of the episode on the
        // strength of a title is worse than not offering to.
        assertNull(openingChapter(listOf(chapter("Episode", 0), chapter("Opening", 1200))))
    }

    @Test
    fun `a file with no chapters, and one not read yet`() {
        assertNull(openingChapter(emptyList()))
        assertNull(openingChapter(null))
    }
}
