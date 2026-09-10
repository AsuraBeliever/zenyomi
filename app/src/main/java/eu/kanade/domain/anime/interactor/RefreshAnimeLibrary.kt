package eu.kanade.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Refetches episodes for every anime in the library.
 *
 * A manual refresh rather than the periodic job Mihon runs for manga: that needs
 * WorkManager scheduling, notifications and update-restriction preferences, none of which
 * the anime side has yet. This is the part that does the actual work, and the job can be
 * built on it later.
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

    suspend fun await(): Result = coroutineScope {
        val favorites = getAnimeFavorites.await()
        val semaphore = Semaphore(CONCURRENCY)

        val outcomes = favorites.map { anime ->
            async {
                semaphore.withPermit {
                    val source = sourceManager.get(anime.source)
                    if (source == null) {
                        false
                    } else {
                        runCatching { syncEpisodesWithSource.await(anime, source) }
                            .onFailure { logcat(LogPriority.WARN, it) { "Refresh failed for ${anime.title}" } }
                            .isSuccess
                    }
                }
            }
        }.map { it.await() }

        Result(total = outcomes.size, failed = outcomes.count { !it })
    }

    data class Result(val total: Int, val failed: Int)

    companion object {
        private const val CONCURRENCY = 5
    }
}
