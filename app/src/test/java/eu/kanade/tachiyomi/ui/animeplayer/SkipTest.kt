package eu.kanade.tachiyomi.ui.animeplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Where a skip lands, and when it is worth offering at all.
 *
 * Both buttons — opening and ending — are this class with different times in it, so what is
 * checked here is the arithmetic they share: the margin left at the end, and the seconds at
 * the end of the stretch where pressing would be a jump backwards.
 */
class SkipTest {

    @Test
    fun `the landing is the margin short of the end`() {
        assertEquals(115, Skip(30..120, slack = 5).landing)
    }

    @Test
    fun `the drift between copies comes off the same end`() {
        assertEquals(108, Skip(30..120, slack = 5 + 7).landing)
    }

    @Test
    fun `a stretch shorter than the margin still goes forward`() {
        // Never back past the start: a two second opening is not a reason to rewind.
        assertEquals(31, Skip(30..32, slack = 5).landing)
    }

    @Test
    fun `offered inside, and not before or after`() {
        val skip = Skip(30..120, slack = 5)
        assertFalse(skip.offersAt(29))
        assertTrue(skip.offersAt(30))
        assertTrue(skip.offersAt(114))
        assertFalse(skip.offersAt(121))
    }

    @Test
    fun `not offered once there is nothing left to skip`() {
        // Past the landing the button would seek backwards, which is not what it says it does.
        val skip = Skip(30..120, slack = 5)
        assertFalse(skip.offersAt(115))
        assertFalse(skip.offersAt(118))
    }
}
