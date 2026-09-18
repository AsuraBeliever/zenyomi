package eu.kanade.domain.anime.interactor

import eu.kanade.tachiyomi.animesource.model.Video
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Reusing a resolved url is what keeps a second look at the same episode from repeating ten
 * seconds of hoster and extractor calls. It is also the piece with the most ways to be
 * quietly wrong: a stale url served past its life, a hit on the wrong episode, or a cache
 * that grows until it holds every episode of a long entry.
 */
class ResolvedVideoCacheTest {

    private val cache = GetEpisodeVideos(mockk())

    private fun video(url: String) = Video(videoUrl = url, videoTitle = url)

    private val t0 = 1_000_000L
    private val ttl = 5.minutes.inWholeMilliseconds

    @Test
    fun `an episode that was never resolved has nothing cached`() {
        assertNull(cache.cached(1L, t0))
    }

    @Test
    fun `what an episode resolved to comes back`() {
        cache.remember(1L, video("https://a.invalid/1.mp4"), t0)

        assertEquals("https://a.invalid/1.mp4", cache.cached(1L, t0)?.videoUrl)
    }

    @Test
    fun `episodes do not read each other's urls`() {
        cache.remember(1L, video("one"), t0)
        cache.remember(2L, video("two"), t0)

        assertEquals("one", cache.cached(1L, t0)?.videoUrl)
        assertEquals("two", cache.cached(2L, t0)?.videoUrl)
        assertNull(cache.cached(3L, t0))
    }

    @Test
    fun `an entry still inside its life is served`() {
        cache.remember(1L, video("fresh"), t0)

        assertEquals("fresh", cache.cached(1L, t0 + ttl)?.videoUrl)
    }

    @Test
    fun `an entry past its life is not, and is dropped rather than left to rot`() {
        cache.remember(1L, video("stale"), t0)

        assertNull(cache.cached(1L, t0 + ttl + 1))
        // Even asked again at the original instant: the expired entry is gone, not merely
        // hidden, so a clock that steps backwards cannot resurrect a dead url.
        assertNull(cache.cached(1L, t0))
    }

    @Test
    fun `a url the player could not open is forgotten, so a retry resolves again`() {
        cache.remember(1L, video("dead"), t0)

        cache.forget(1L)

        assertNull(cache.cached(1L, t0))
    }

    @Test
    fun `forgetting an episode that was never there is harmless`() {
        cache.forget(999L)

        assertNull(cache.cached(999L, t0))
    }

    @Test
    fun `re-resolving an episode replaces what it held`() {
        cache.remember(1L, video("old"), t0)
        cache.remember(1L, video("new"), t0 + 1)

        assertEquals("new", cache.cached(1L, t0 + 2)?.videoUrl)
    }

    @Test
    fun `an entry that outlives its ttl is renewed by re-resolving, not stuck at its first time`() {
        cache.remember(1L, video("first"), t0)
        cache.remember(1L, video("second"), t0 + ttl)

        assertEquals("second", cache.cached(1L, t0 + ttl + 1)?.videoUrl)
    }

    @Test
    fun `the cache does not grow past its cap`() {
        repeat(20) { cache.remember(it.toLong(), video("v$it"), t0) }

        // The last sixteen survive; what fell off the front is gone.
        assertNull(cache.cached(0L, t0))
        assertNull(cache.cached(3L, t0))
        assertEquals("v4", cache.cached(4L, t0)?.videoUrl)
        assertEquals("v19", cache.cached(19L, t0)?.videoUrl)
    }

    @Test
    fun `the episode being watched is not evicted by the ones around it`() {
        repeat(16) { cache.remember(it.toLong(), video("v$it"), t0) }
        // Reading it is what keeps it: this is the episode in the player, and the entries
        // arriving behind it are its neighbours being resolved.
        assertEquals("v0", cache.cached(0L, t0)?.videoUrl)

        cache.remember(100L, video("new"), t0)

        assertEquals("v0", cache.cached(0L, t0)?.videoUrl)
        assertNull(cache.cached(1L, t0))
    }
}
