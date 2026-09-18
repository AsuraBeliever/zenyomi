package eu.kanade.tachiyomi.ui.download.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import kotlin.time.Duration.Companion.seconds

/**
 * One episode waiting in the anime download queue, with enough about it to draw a row.
 *
 * @param progress non-null only for the one being fetched right now. The queue runs one at a
 * time, so everything else is simply waiting.
 */
data class AnimeDownloadQueueItem(
    val episodeId: Long,
    val animeTitle: String,
    val episodeName: String,
    val sourceName: String,
    val progress: AnimeDownloadProgress?,
)

/**
 * The anime half of the download queue screen.
 *
 * Its own view model beside Mihon's rather than inside it: Mihon's is built on a legacy
 * adapter whose items are typed to a manga download, and the charter keeps the two trees
 * apart. What they share is the screen, which now has a tab each.
 *
 * The queue stores ids and nothing else — the titles are read from the database each time, so
 * a renamed entry or one whose episode list changed cannot leave a stale name on screen.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class AnimeDownloadQueueViewModel(
    private val downloadManager: AnimeDownloadManager,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val sourceManager: AnimeSourceManager,
) : ViewModel() {

    val items: StateFlow<List<AnimeDownloadQueueItem>> =
        combine(downloadManager.queue, downloadManager.progress) { queue, progress ->
            queue.mapNotNull { item ->
                val anime = getAnime.await(item.animeId) ?: return@mapNotNull null
                val episode = getEpisode.await(item.episodeId) ?: return@mapNotNull null
                AnimeDownloadQueueItem(
                    episodeId = item.episodeId,
                    animeTitle = anime.title,
                    episodeName = episode.name,
                    sourceName = sourceManager.get(anime.source)?.name.orEmpty(),
                    progress = progress[item.episodeId],
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())

    /** How many are waiting, for the count beside the screen's title. */
    val count: StateFlow<Int> = downloadManager.queue
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), 0)

    fun cancel(episodeId: Long) = downloadManager.cancel(episodeId)

    fun clearAll() = downloadManager.clearQueue()
}
