package tachiyomi.core.common.torrent

import tachiyomi.core.common.torrent.model.FileStats

/**
 * Which file inside a torrent is the episode.
 *
 * A torrent is often more than a video: subtitles, a poster, a sample, a whole season. A
 * source that knows which one it means says so with `index=` in the magnet link, which is how
 * Aniyomi's extensions do it ([TorrServer's ids][FileStats.id] start at 1). Without that,
 * Aniyomi plays file 0, which is not even a valid id; here the episode is the largest video,
 * because that is what a person looking at the list would pick. In Big Buck Bunny's torrent
 * the first file is a 140-byte subtitle.
 */
object TorrentEpisodeFile {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "ts", "m2ts", "ogv",
    )

    /** The `index=` a magnet link carries, if any. */
    fun requestedIndex(link: String): Int? {
        if (!link.startsWith("magnet:", ignoreCase = true)) return null
        return link.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("index=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
    }

    fun choose(files: List<FileStats>, requestedIndex: Int?): FileStats? {
        if (requestedIndex != null) {
            files.firstOrNull { it.id == requestedIndex }?.let { return it }
        }
        return files.filter { it.isVideo }.maxByOrNull { it.length }
            ?: files.maxByOrNull { it.length }
    }

    private val FileStats.isVideo: Boolean
        get() = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
}
