package eu.kanade.tachiyomi.data.download.anime

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One quality a master playlist offers.
 *
 * @param height the vertical resolution, when the playlist declares one. Optional in HLS, and
 * plenty of streams leave it out, which is why [bandwidth] is what the qualities are ordered by.
 * @param bandwidth bits per second. Also what the size of the episode is worked out from: a
 * stream has no declared length, but bitrate times duration is one.
 */
data class HlsVariant(
    val url: String,
    val height: Int?,
    val bandwidth: Long?,
)

/**
 * The parts of an m3u8 this app needs.
 *
 * Not a general HLS parser and not trying to be: it reads the two things that decide what to
 * download — which qualities exist, and how long the episode runs — and leaves the rest to
 * ffmpeg, which is the thing that actually fetches the stream.
 */
object HlsPlaylist {

    private val STREAM_INF = Regex("""#EXT-X-STREAM-INF:([^\n]*)\n\s*([^#\s][^\n]*)""")
    private val BANDWIDTH = Regex("""(?:^|,)BANDWIDTH=(\d+)""")
    private val RESOLUTION = Regex("""(?:^|,)RESOLUTION=(\d+)x(\d+)""")
    private val EXTINF = Regex("""#EXTINF:\s*([0-9]*\.?[0-9]+)""")

    private const val MASTER_MARKER = "#EXT-X-STREAM-INF"
    private const val BITS_PER_BYTE = 8

    /** Whether this playlist names other playlists rather than segments. */
    fun isMaster(playlist: String): Boolean = playlist.contains(MASTER_MARKER)

    /**
     * The qualities a master playlist offers, best first, with absolute urls.
     *
     * Empty for a media playlist, which is a single quality and has nothing to choose between.
     */
    fun variants(playlist: String, playlistUrl: String): List<HlsVariant> {
        val base = playlistUrl.toHttpUrlOrNull() ?: return emptyList()
        return STREAM_INF.findAll(playlist)
            .mapNotNull { match ->
                val attributes = match.groupValues[1]
                val url = base.resolve(match.groupValues[2].trim())?.toString() ?: return@mapNotNull null
                HlsVariant(
                    url = url,
                    height = RESOLUTION.find(attributes)?.groupValues?.get(2)?.toIntOrNull(),
                    bandwidth = BANDWIDTH.find(attributes)?.groupValues?.get(1)?.toLongOrNull(),
                )
            }
            .sortedWith(compareByDescending<HlsVariant> { it.height ?: 0 }.thenByDescending { it.bandwidth ?: 0 })
            .toList()
    }

    /**
     * How long the episode runs, summed from a media playlist, or null if it does not say.
     *
     * A master playlist carries no durations at all — it names playlists, not segments — so
     * this answers null for one of those.
     */
    fun durationSeconds(playlist: String): Double? =
        EXTINF.findAll(playlist)
            .sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            .takeIf { it > 0 }

    /**
     * The segments a media playlist names, as absolute urls.
     *
     * Used to size an episode when the playlist declares no bitrate to work from: one segment
     * weighed and multiplied by how many there are lands close, because a stream is cut into
     * segments of the same length and encoded at one rate.
     */
    fun segmentUrls(playlist: String, playlistUrl: String): List<String> {
        val base = playlistUrl.toHttpUrlOrNull() ?: return emptyList()
        return playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { base.resolve(it)?.toString() }
            .toList()
    }

    /**
     * Roughly how many bytes a variant will come to.
     *
     * Bitrate times duration, which is what the numbers in the playlist support and no more.
     * It is an estimate and is shown as one: the real file lands within a few percent of this
     * for a constant-bitrate stream and further out for a variable one.
     */
    fun estimatedBytes(bandwidth: Long?, durationSeconds: Double?): Long? {
        if (bandwidth == null || bandwidth <= 0 || durationSeconds == null || durationSeconds <= 0) return null
        return (bandwidth / BITS_PER_BYTE * durationSeconds).toLong()
    }
}

/**
 * The variant to download for a requested [height].
 *
 * The exact one when it is there. Otherwise the closest below it, because a viewer who asked
 * for 720p wants an episode that fits the phone rather than one four times the size, and the
 * lowest on offer when even that does not exist. Never null for a non-empty list: an episode
 * downloading at the wrong quality beats one that refuses to download.
 *
 * This is the safety net rather than the decision. Which quality to fetch is settled before
 * the episode is queued, where there is somebody to ask; by the time the download runs there
 * is nobody there, and silently picking something sensible is the only thing left to do.
 */
fun List<HlsVariant>.pick(height: Int?): HlsVariant? {
    if (isEmpty()) return null
    if (height == null) return first()
    return firstOrNull { it.height == height }
        ?: filter { (it.height ?: 0) < height }.maxByOrNull { it.height ?: 0 }
        ?: last()
}
