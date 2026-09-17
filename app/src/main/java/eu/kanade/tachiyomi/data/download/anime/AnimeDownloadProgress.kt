package eu.kanade.tachiyomi.data.download.anime

/**
 * How far along one episode's download is.
 *
 * More than a percentage because a percentage answers the wrong question when a download is
 * slow. "43%" does not say whether it is moving; "180 MB of 420 MB, 1.2 MB/s" does, and it is
 * the difference between waiting and giving up.
 *
 * @param percent 0..100, or null when nothing has said how big the episode is. A stream can
 * refuse to declare a length, and a bar that sits at zero forever is worse than no bar.
 * @param bytesPerSecond averaged over the last few seconds, not since the start: what matters
 * is whether it is moving *now*.
 */
data class AnimeDownloadProgress(
    val downloadedBytes: Long,
    private val estimatedTotalBytes: Long?,
    val bytesPerSecond: Long,
) {
    /**
     * How big the episode is, as far as anything knows — and null once that stops being
     * credible.
     *
     * The total is an estimate worked out from a handful of sampled segments, so it can come
     * in under the truth. When it does, saying "200 MB of 160 MB" is worse than admitting the
     * size is not known: the number the viewer can see with their own eyes is the one that has
     * already arrived.
     */
    val totalBytes: Long? = estimatedTotalBytes?.takeIf { it > 0 && it >= downloadedBytes }

    val percent: Int? = totalBytes?.let { ((downloadedBytes * 100) / it).toInt().coerceIn(0, 100) }
}

/**
 * Turns a running byte count into a speed.
 *
 * Smoothed, because the raw difference between two samples swings between zero and double the
 * real rate depending on where the sample lands relative to a segment boundary — and a number
 * that flickers is one nobody can read. The weight favours recent samples enough to notice a
 * stall within a couple of seconds.
 */
internal class DownloadRate(private val now: () -> Long = System::nanoTime) {

    private var lastBytes = 0L
    private var lastAt = 0L
    private var smoothed = 0.0

    /** @return bytes per second, or 0 until there are two samples to compare. */
    fun sample(bytes: Long): Long {
        val at = now()
        if (lastAt == 0L) {
            lastBytes = bytes
            lastAt = at
            return 0
        }
        val elapsed = at - lastAt
        if (elapsed < MIN_INTERVAL_NANOS) return smoothed.toLong()

        val rate = (bytes - lastBytes).coerceAtLeast(0) * NANOS_PER_SECOND / elapsed
        lastBytes = bytes
        lastAt = at
        // The first real sample is the whole estimate; after that it only moves it.
        smoothed = if (smoothed == 0.0) rate.toDouble() else smoothed * (1 - WEIGHT) + rate * WEIGHT
        return smoothed.toLong()
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L

        /** Below this the divisor is small enough to turn rounding into a wild rate. */
        const val MIN_INTERVAL_NANOS = 250_000_000L

        const val WEIGHT = 0.35
    }
}
