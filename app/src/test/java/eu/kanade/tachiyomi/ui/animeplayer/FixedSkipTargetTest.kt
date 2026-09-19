package eu.kanade.tachiyomi.ui.animeplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Where the skip button lands when nothing says where this episode's opening ends.
 *
 * The rule that matters: pressing the button late must not cost the viewer episode. Jumping
 * the configured length from wherever it was pressed overshoots the end of the opening by
 * however long they waited before pressing, which is exactly the content they were trying to
 * keep. Landing a few seconds early is the acceptable direction to be wrong in.
 */
class FixedSkipTargetTest {

    private val length = 85
    private val duration = 1440

    @Test
    fun `pressing at the start lands at the end of an opening's length`() {
        assertEquals(85, fixedSkipTarget(position = 0, length = length, duration = duration))
    }

    @Test
    fun `pressing late in the opening lands in the same place, not past it`() {
        // The complaint this exists for: ten seconds of waiting used to cost ten seconds of
        // episode, because the jump was measured from the press.
        assertEquals(85, fixedSkipTarget(position = 10, length = length, duration = duration))
        assertEquals(85, fixedSkipTarget(position = 50, length = length, duration = duration))
        assertEquals(85, fixedSkipTarget(position = 84, length = length, duration = duration))
    }

    @Test
    fun `the configured length is what is being measured`() {
        assertEquals(120, fixedSkipTarget(position = 30, length = 120, duration = duration))
        assertEquals(60, fixedSkipTarget(position = 30, length = 60, duration = duration))
    }

    @Test
    fun `past that length the opening cannot have started with the episode`() {
        // Nothing to anchor to, so the jump is measured from the press, as it always was.
        assertEquals(185, fixedSkipTarget(position = 100, length = length, duration = duration))
        assertEquals(285, fixedSkipTarget(position = 200, length = length, duration = duration))
    }

    @Test
    fun `it never runs off the end of the episode`() {
        // A short episode: the next one must not be handed the screen by a skip.
        assertEquals(149, fixedSkipTarget(position = 100, length = length, duration = 150))
        assertEquals(85, fixedSkipTarget(position = 20, length = length, duration = 90))
    }

    @Test
    fun `it never seeks backwards`() {
        assertEquals(50, fixedSkipTarget(position = 50, length = length, duration = 40))
    }
}
