package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.animesource.model.Video

/** `1080p` first, then the tail of a `1920x1080`. */
private val LABEL_HEIGHT = Regex("""(?:(\d{3,4})p\b)|(?:x(\d{3,4})\b)""")

/**
 * The vertical resolution of a video, from the field if the source fills it in and from its
 * own label if not.
 *
 * Plenty of sources leave [Video.resolution] null and put the number in the title instead —
 * "VidStreaming - 1080p (1920x1080) - 8.09 MB/s" — and without reading it a quality has no
 * height, which is indistinguishable from "whatever is best". Picking 360p then quietly
 * turned into 1080p on the next episode.
 */
fun Video.heightOrNull(): Int? = resolution ?: LABEL_HEIGHT.find(videoTitle)?.let { match ->
    match.groupValues[1].ifBlank { match.groupValues[2] }.toIntOrNull()
}

/**
 * The videos to try for a requested [height], best match first.
 *
 * Sources disagree about where the qualities live. Some put them inside one stream, as a
 * master playlist, and there the choice is made when the playlist is opened. Others — like
 * the one this was written against — return a separate video per quality, and there the
 * choice has to be made *here*, before anything is fetched: picking the variant inside the
 * playlist does nothing when the playlist has no variants, and the download quietly came down
 * at whatever quality the player would have preferred.
 *
 * Falls back the same way everything else does: the closest below, then whatever exists. An
 * episode at the wrong quality beats an episode that refuses to download.
 */
fun List<Video>.forQuality(height: Int?): List<Video> {
    if (height == null || isEmpty()) return this
    filter { it.heightOrNull() == height }.takeIf { it.isNotEmpty() }?.let { return it }
    val closestBelow = mapNotNull { it.heightOrNull() }.filter { it < height }.maxOrNull()
        ?: return this
    return filter { it.heightOrNull() == closestBelow }
}
