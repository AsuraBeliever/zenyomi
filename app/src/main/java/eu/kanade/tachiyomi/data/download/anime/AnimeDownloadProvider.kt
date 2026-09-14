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

    fun findEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode): UniFile? {
        val dir = findAnimeDir(anime, source) ?: return null
        return dir.listFiles()
            .orEmpty()
            .firstOrNull { it.name?.substringBeforeLast('.') == episodeFileName(episode) }
            ?.takeIf { it.isFile && it.length() > 0 }
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
            .filter { it.isFile && it.length() > 0 }
            .mapNotNullTo(mutableSetOf()) { it.name?.substringBeforeLast('.') }
    }

    fun createEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode, extension: String): UniFile? {
        val dir = getAnimeDir(anime, source) ?: return null
        return dir.createFile(episodeFileName(episode) + "." + extension)
    }

    fun episodeFileName(episode: Episode): String = DiskUtil.buildValidFilename(episode.name)
}
