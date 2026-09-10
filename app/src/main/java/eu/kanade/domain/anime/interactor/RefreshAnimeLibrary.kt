package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Refetches episodes for every anime in the library.
 *
 * Drives both the manual pull-to-refresh and the periodic
 * [eu.kanade.tachiyomi.data.animelibrary.AnimeLibraryUpdateJob]; the job adds scheduling and
 * notifications around this, it does not duplicate the work.
 *
 * Sources are hit concurrently but capped, so a large library does not open dozens of
 * connections at once, and one failing source does not sink the rest.
 */
@Inject
class RefreshAnimeLibrary(
    private val getAnimeFavorites: GetAnimeFavorites,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val sourceManager: AnimeSourceManager,
) {

    /**
     * @param onProgress invoked as each anime finishes, with the number done, the total, and the
     * title just handled. Called from several coroutines, so it is dispatched under a lock and
     * must be cheap.
     */
    suspend fun await(
        onProgress: (current: Int, total: Int, title: String?) -> Unit = { _, _, _ -> },
    ): Result = coroutineScope {
        val favorites = getAnimeFavorites.await()
        val semaphore = Semaphore(CONCURRENCY)
        val progressLock = Mutex()
        var completed = 0

        val outcomes = favorites.map { anime ->
            async {
                semaphore.withPermit {
                    val source = sourceManager.get(anime.source)
                    val newEpisodes = if (source == null) {
                        null
                    } else {
                        runCatching { syncEpisodesWithSource.await(anime, source) }
                            .onFailure { logcat(LogPriority.WARN, it) { "Refresh failed for ${anime.title}" } }
                            .getOrNull()
                    }
                    progressLock.withLock {
                        completed++
                        onProgress(completed, favorites.size, anime.title)
                    }
                    anime to newEpisodes
                }
            }
        }.map { it.await() }

        Result(
            total = outcomes.size,
            failed = outcomes.count { it.second == null },
            updates = outcomes.mapNotNull { (anime, episodes) ->
                if (episodes.isNullOrEmpty()) null else anime to episodes
            },
        )
    }

    data class Result(
        val total: Int,
        val failed: Int,
        val updates: List<Pair<Anime, List<Episode>>> = emptyList(),
    )

    companion object {
        private const val CONCURRENCY = 5
    }
}
