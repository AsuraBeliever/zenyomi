package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.storage.service.StorageManager

/**
 * Where a downloaded episode lives.
 *
 * Mirrors the shape Mihon uses for chapters, `downloads/<source>/<entry>/<item>`, so an
 * anime download sits beside a manga one and neither has to know about the other.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeDownloadProvider(
    private val storageManager: StorageManager,
) {

    private fun downloadsDir(): UniFile? = storageManager.getDownloadsDirectory()

    /**
     * The entry's directory, creating it if it is not there. For writing only.
     *
     * Asking this on a read path is what made merely opening an entry create an empty
     * `downloads/<source>/<title>` for it, so reads go through [findAnimeDir].
     */
    fun getAnimeDir(anime: Anime, source: AnimeSource): UniFile? {
        return downloadsDir()
            ?.createDirectory(DiskUtil.buildValidFilename(source.toString()))
            ?.createDirectory(DiskUtil.buildValidFilename(anime.title))
    }

    /** The entry's directory if it exists, without creating anything. */
    private fun findAnimeDir(anime: Anime, source: AnimeSource): UniFile? {
        return downloadsDir()
            ?.findFile(DiskUtil.buildValidFilename(source.toString()))
            ?.findFile(DiskUtil.buildValidFilename(anime.title))
    }

    fun findEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode): UniFile? =
        findAnyEpisodeFile(anime, source, episode)?.takeIf { it.isPlayable() }

    /**
     * The episode's file whatever it turned out to be, playable or not.
     *
     * For deleting, where a leftover playlist has to go the same way a real video does, and
     * for clearing the way before a download writes. [findEpisodeFile] is the one to ask
     * whether an episode is downloaded.
     */
    fun findAnyEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode): UniFile? {
        val dir = findAnimeDir(anime, source) ?: return null
        val prefix = episodeFileName(episode) + "."
        return dir.listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.name?.startsWith(prefix) == true }
    }

    /**
     * The base names of everything already downloaded for this entry, in one listing.
     *
     * Asking [findEpisodeFile] per episode meant one directory lookup and one full listing
     * for every row, which on One Piece is over a thousand round trips to the storage
     * provider for an answer a single listing contains. Blocking I/O: call it off the main
     * thread.
     */
    fun downloadedFileNames(anime: Anime, source: AnimeSource): Set<String> {
        val dir = findAnimeDir(anime, source) ?: return emptySet()
        return dir.listFiles()
            .orEmpty()
            .filter { it.isPlayable() }
            .mapNotNullTo(mutableSetOf()) { it.name?.substringBeforeLast('.') }
    }

    /**
     * The file a download writes into, which is not yet the file it will end up as.
     *
     * It is created with [PARTIAL_SUFFIX] on the end so that nothing reads it as a finished
     * episode while it is being written. Remuxing a 24-minute episode takes minutes, and for
     * all of them the file exists and has bytes in it: without this the row shows the
     * downloaded tick straight away and tapping it plays however much had arrived. Call
     * [finish] when the download is complete.
     */
    fun createEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode, extension: String): UniFile? {
        val dir = getAnimeDir(anime, source) ?: return null
        return dir.createFile(episodeFileName(episode) + "." + extension + PARTIAL_SUFFIX)
    }

    /** Turns a finished download into the episode. Returns whether the rename took. */
    fun finish(file: UniFile): Boolean {
        val name = file.name ?: return false
        if (!name.endsWith(PARTIAL_SUFFIX)) return true
        return file.renameTo(name.removeSuffix(PARTIAL_SUFFIX))
    }

    fun episodeFileName(episode: Episode): String = DiskUtil.buildValidFilename(episode.name)

    companion object {
        /**
         * Playlists are not episodes.
         *
         * Until the downloader learned to remux, an HLS source left an m3u8 here — a few
         * kilobytes of links to segments still on the internet — and everything downstream
         * read that as a downloaded episode: the tick appeared, the download button turned
         * into a delete button, and the episode played only while online. Treating them as
         * absent puts those entries back within reach of a download that works.
         */
        private val PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8")

        /** What a download in flight is called until it finishes. */
        const val PARTIAL_SUFFIX = ".part"

        private fun UniFile.isPlayable(): Boolean {
            if (!isFile || length() <= 0) return false
            val name = this.name ?: return false
            if (name.endsWith(PARTIAL_SUFFIX)) return false
            return name.substringAfterLast('.', "").lowercase() !in PLAYLIST_EXTENSIONS
        }
    }
}
