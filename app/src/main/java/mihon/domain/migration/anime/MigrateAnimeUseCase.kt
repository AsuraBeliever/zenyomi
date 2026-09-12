package mihon.domain.migration.anime

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import kotlin.time.Clock

/**
 * Moves an anime in the library from one source to another, keeping what the user did.
 *
 * This matters more here than it does for manga. Anime sources die constantly — a domain
 * lapses, a site rewrites its pages, an extension stops being maintained — and without this
 * the only way out of a dead source is to re-add the series elsewhere and re-watch, or
 * re-mark, everything.
 *
 * Mirrors [mihon.domain.migration.usecases.MigrateMangaUseCase] minus the parts that do not
 * exist on this side: anime have no custom covers and no notes.
 */
@Inject
class MigrateAnimeUseCase(
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val updateAnime: UpdateAnime,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val updateEpisode: UpdateEpisode,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getAnimeTracks: GetAnimeTracks,
    private val insertAnimeTrack: InsertAnimeTrack,
) {

    /**
     * @param replace whether the old entry leaves the library. False keeps both, which is what
     * you want when the new source is a different cut of the same show rather than a
     * replacement for a dead one.
     */
    suspend operator fun invoke(current: Anime, target: Anime, replace: Boolean): Result<Unit> {
        return try {
            val targetSource = sourceManager.get(target.source)
                ?: return Result.failure(IllegalStateException("Source ${target.source} is not loaded"))

            // The target usually comes from a search result and has no episodes locally yet.
            syncEpisodesWithSource.await(target, targetSource)

            carryOverProgress(current, target)

            val categoryIds = getAnimeCategories.await(current.id).map { it.id }
            if (categoryIds.isNotEmpty()) setAnimeCategories.await(target.id, categoryIds)

            getAnimeTracks.await(current.id).forEach { track ->
                insertAnimeTrack.await(track.copy(animeId = target.id))
            }

            // The download manager deletes per episode, so the old entry's files go one by
            // one. Leaving them would keep a dead source's downloads on disk for good.
            if (replace) {
                sourceManager.get(current.source)?.let { currentSource ->
                    getEpisodesByAnimeId.await(current.id).forEach { episode ->
                        downloadManager.deleteEpisode(current, currentSource, episode)
                    }
                }
            }

            updateAnime.await(
                AnimeUpdate(
                    id = target.id,
                    favorite = true,
                    episodeFlags = current.episodeFlags,
                    viewerFlags = current.viewerFlags,
                    dateAdded = if (replace) {
                        current.dateAdded
                    } else {
                        Clock.System.now().toEpochMilliseconds()
                    },
                ),
            )
            if (replace) {
                updateAnime.await(AnimeUpdate(id = current.id, favorite = false, dateAdded = 0))
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Could not migrate ${current.title}" }
            Result.failure(e)
        }
    }

    /**
     * Marks on the target whatever was watched on the source, matched by episode number.
     *
     * Numbers rather than urls or titles: two sources agree on almost nothing else. Everything
     * at or below the furthest episode watched is marked seen, which is the assumption that
     * holds — you do not watch episode 40 without having watched 1 to 39 — and it recovers the
     * common case where the old source's episodes were never individually marked.
     */
    private suspend fun carryOverProgress(current: Anime, target: Anime) {
        val previous = getEpisodesByAnimeId.await(current.id)
        val episodes = getEpisodesByAnimeId.await(target.id)
        if (previous.isEmpty() || episodes.isEmpty()) return

        val furthestSeen = previous.filter { it.seen }.maxOfOrNull { it.episodeNumber }
        val previousByNumber = previous.filter { it.isRecognizedNumber }.associateBy { it.episodeNumber }

        val updates = episodes.mapNotNull { episode ->
            if (!episode.isRecognizedNumber) return@mapNotNull null
            val match = previousByNumber[episode.episodeNumber]
            val seen = furthestSeen != null && episode.episodeNumber <= furthestSeen

            if (match == null && !seen) return@mapNotNull null

            EpisodeUpdate(
                id = episode.id,
                seen = seen || match?.seen == true,
                bookmark = match?.bookmark,
                fillermark = match?.fillermark,
                // Only the position inside a part-watched episode is worth carrying; a
                // finished one starts from the beginning anyway.
                lastSecondSeen = match?.lastSecondSeen?.takeIf { match.seen.not() && it > 0 },
                dateFetch = match?.dateFetch,
            )
        }

        if (updates.isNotEmpty()) updateEpisode.awaitAll(updates)
    }
}
