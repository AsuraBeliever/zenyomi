package tachiyomi.source.local.anime

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * Plays video files kept on the device, under the `localanime` folder of the storage
 * location chosen during onboarding.
 *
 * Each subfolder is an entry and the video files inside it are its episodes; a video
 * sitting loose in the folder is an entry with a single episode. Deliberately minimal
 * next to Aniyomi's local source, which also reads covers, backgrounds, thumbnails and
 * metadata files.
 */
@Inject
@SingleIn(AppScope::class)
class LocalAnimeSource(
    private val fileSystem: LocalAnimeSourceFileSystem,
) : AnimeSource {

    override val id: Long = ID
    override val name: String = "Local anime"
    override val lang: String = "other"
    override val supportsLatest: Boolean = false
    override val supportsRelatedAnime: Boolean = false

    override suspend fun getPopularAnime(page: Int): AnimesPage = allEntries()

    override suspend fun getLatestUpdates(page: Int): AnimesPage = allEntries()

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        val entries = allEntries().animes.filter { it.title.contains(query, ignoreCase = true) }
        return AnimesPage(entries, hasNextPage = false)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime.apply {
        initialized = true
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val directory = fileSystem.getAnimeDirectory(anime.url)
        val files = if (directory != null) {
            fileSystem.getFilesInAnimeDirectory(anime.url).filter { it.isVideo }
        } else {
            // A loose video file: the entry is the file, and it is its own episode.
            fileSystem.getFilesInBaseDirectory().filter { it.isVideo && it.name == anime.url }
        }
        return files
            .sortedBy { it.name }
            .mapIndexed { index, file ->
                SEpisode.create().apply {
                    url = file.uri.toString()
                    name = file.name.orEmpty()
                    episode_number = (index + 1).toFloat()
                    date_upload = file.lastModified()
                }
            }
            .reversed()
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        listOf(Video(videoUrl = episode.url, videoTitle = episode.name)).toHosterList()

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        val updated = if (fetchDetails) getAnimeDetails(anime) else anime
        return SAnimeEpisodeUpdate(updated, if (fetchEpisodes) getEpisodeList(updated) else episodes)
    }

    // Local entries have no seasons: a folder is one entry.
    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = SAnimeSeasonUpdate(anime, emptyList())

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    private fun allEntries(): AnimesPage {
        val entries = fileSystem.getFilesInBaseDirectory()
            .filter { it.isDirectory || it.isVideo }
            .sortedBy { it.name }
            .map { file ->
                SAnime.create().apply {
                    url = file.name.orEmpty()
                    title = file.name.orEmpty().substringBeforeLast('.')
                }
            }
        return AnimesPage(entries, hasNextPage = false)
    }

    companion object {
        const val ID = 0L
    }
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "ts", "m2ts", "ogv",
)

private val UniFile.isVideo: Boolean
    get() = isFile && name.orEmpty().substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
