package eu.kanade.tachiyomi.ui.animestats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks

/**
 * Counts up what the anime library holds.
 *
 * Walks the library entry by entry rather than doing it in SQL. An anime library is small —
 * tens of entries, not the thousands a manga library can reach — and a handful of queries is
 * cheaper to keep correct than a set of hand-written aggregate views.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeStatsViewModel(
    private val getAnimeFavorites: GetAnimeFavorites,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getAnimeTracks: GetAnimeTracks,
    private val downloadManager: AnimeDownloadManager,
    private val sourceManager: AnimeSourceManager,
    private val trackerManager: TrackerManager,
) : ViewModel() {

    private val _state = MutableStateFlow<AnimeStatsState>(AnimeStatsState.Loading)
    val state: StateFlow<AnimeStatsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() = withIOContext {
        val library = getAnimeFavorites.await()

        var totalEpisodes = 0
        var seenEpisodes = 0
        var downloaded = 0
        var watchedSeconds = 0L
        var startedAnime = 0
        var trackedAnime = 0
        var scoreSum = 0.0
        var scoreCount = 0

        // Only the trackers that can hold anime count here; the manga-only ones would
        // otherwise inflate "tracked" with entries that are nothing of the sort.
        val animeTrackerIds = trackerManager.trackers
            .filter { it is AnimeTracker }
            .map { it.id }
            .toSet()

        library.forEach { anime ->
            val episodes = getEpisodesByAnimeId.await(anime.id)
            totalEpisodes += episodes.size
            val seen = episodes.count { it.seen }
            seenEpisodes += seen
            if (seen > 0) startedAnime++
            // How far into each episode the user actually got, which for a finished episode is
            // its whole length. Anime history stores no duration of its own to add up.
            watchedSeconds += episodes.sumOf { it.lastSecondSeen }

            val source = sourceManager.get(anime.source)
            if (source != null) {
                downloaded += episodes.count { downloadManager.isDownloaded(anime, source, it) }
            }

            val tracks = getAnimeTracks.await(anime.id).filter { it.trackerId in animeTrackerIds }
            if (tracks.isNotEmpty()) {
                trackedAnime++
                tracks.filter { it.score > 0 }.forEach {
                    scoreSum += it.score
                    scoreCount++
                }
            }
        }

        _state.value = AnimeStatsState.Success(
            libraryCount = library.size,
            completedCount = library.count { it.status.toInt() == SAnime.COMPLETED },
            watchedSeconds = watchedSeconds,
            inGlobalUpdate = library.count { it.updateStrategy == AnimeUpdateStrategy.ALWAYS_UPDATE },
            startedCount = startedAnime,
            localCount = library.count { it.source == LOCAL_ANIME_SOURCE_ID },
            totalEpisodes = totalEpisodes,
            seenEpisodes = seenEpisodes,
            downloadedEpisodes = downloaded,
            trackedCount = trackedAnime,
            meanScore = if (scoreCount > 0) scoreSum / scoreCount else 0.0,
            loggedInTrackerCount = trackerManager.loggedInTrackers()
                .count { it.id in animeTrackerIds },
        )
    }

    companion object {
        /** Matches tachiyomi.source.local.anime.LocalAnimeSource.ID without depending on it. */
        private const val LOCAL_ANIME_SOURCE_ID = 0L
    }
}

sealed interface AnimeStatsState {
    data object Loading : AnimeStatsState

    data class Success(
        val libraryCount: Int,
        val completedCount: Int,
        val watchedSeconds: Long,
        val inGlobalUpdate: Int,
        val startedCount: Int,
        val localCount: Int,
        val totalEpisodes: Int,
        val seenEpisodes: Int,
        val downloadedEpisodes: Int,
        val trackedCount: Int,
        val meanScore: Double,
        val loggedInTrackerCount: Int,
    ) : AnimeStatsState
}
