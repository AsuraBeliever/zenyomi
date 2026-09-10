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

    fun getAnimeDir(anime: Anime, source: AnimeSource): UniFile? {
        return downloadsDir()
            ?.createDirectory(DiskUtil.buildValidFilename(source.toString()))
            ?.createDirectory(DiskUtil.buildValidFilename(anime.title))
    }

    fun findEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode): UniFile? {
        val dir = getAnimeDir(anime, source) ?: return null
        return dir.listFiles()
            .orEmpty()
            .firstOrNull { it.name?.substringBeforeLast('.') == episodeFileName(episode) }
            ?.takeIf { it.isFile && it.length() > 0 }
    }

    fun createEpisodeFile(anime: Anime, source: AnimeSource, episode: Episode, extension: String): UniFile? {
        val dir = getAnimeDir(anime, source) ?: return null
        return dir.createFile(episodeFileName(episode) + "." + extension)
    }

    fun episodeFileName(episode: Episode): String = DiskUtil.buildValidFilename(episode.name)
}
