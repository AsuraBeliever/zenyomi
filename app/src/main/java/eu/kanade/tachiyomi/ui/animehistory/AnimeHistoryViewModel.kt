package eu.kanade.tachiyomi.ui.animehistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Recently watched anime, newest first.
 *
 * Kept apart from Mihon's manga history rather than merged into it: the two read from
 * separate databases, and the tabbed layout this project chose keeps them separate in
 * the UI too.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeHistoryViewModel(
    private val getAnimeHistory: GetAnimeHistory,
    private val removeAnimeHistory: RemoveAnimeHistory,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val getEpisodeVideos: GetEpisodeVideos,
    private val updateAnime: UpdateAnime,
    private val downloadManager: AnimeDownloadManager,
    private val sourceManager: AnimeSourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        // launchIO: reading and mapping the history rows happens on the collecting thread.
        viewModelScope.launchIO {
            _state.map { it.searchQuery.orEmpty() }
                .distinctUntilChanged()
                .flatMapLatest { getAnimeHistory.subscribe(it) }
                .collect { history ->
                    _state.update { it.copy(isLoading = false, history = history.toUiModels()) }
                }
        }
    }

    fun search(query: String?) = _state.update { it.copy(searchQuery = query) }

    /**
     * Breaks the list into days, the way the manga history does.
     *
     * Mihon's own [eu.kanade.presentation.history.HistoryUiModel] is typed on the manga
     * relation, so this is its twin; the separator logic is the same, and the header is drawn
     * with the same generic [tachiyomi.presentation.core.components.ListGroupHeader].
     */
    private fun List<AnimeHistoryWithRelations>.toUiModels(): List<AnimeHistoryUiModel> {
        return map { AnimeHistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.seenAt?.time?.toLocalDate()
                val afterDate = after?.item?.seenAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> AnimeHistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    fun removeAll() {
        viewModelScope.launch { removeAnimeHistory.awaitAll() }
    }

    /** Forgets one entry, which is what the row's bin button does on the manga side too. */
    fun remove(history: AnimeHistoryWithRelations) {
        viewModelScope.launch { removeAnimeHistory.await(history) }
    }

    /** The heart on a row that is not in the library yet, same as the manga history. */
    fun addToLibrary(animeId: Long) {
        viewModelScope.launch { updateAnime.awaitUpdateFavorite(animeId, true) }
    }

    /**
     * Picks up where the episode was left off.
     *
     * Tapping a row resumes, as it does in Mihon: a history entry is a thing you were
     * watching, and sending it to the entry screen instead makes you find it again. The
     * resolution is the entry screen's, not a second copy of it — a downloaded file wins,
     * and a source that cannot answer reports rather than throwing.
     */
    fun resume(history: AnimeHistoryWithRelations, onResolved: (PlaybackRequest?, Episode?) -> Unit) {
        viewModelScope.launch {
            val anime = getAnime.await(history.animeId) ?: return@launch onResolved(null, null)
            val episode = getEpisode.await(history.episodeId) ?: return@launch onResolved(null, null)
            val source = sourceManager.get(anime.source)
            val local = source?.let {
                withIOContext { downloadManager.downloadedUri(anime, it, episode) }
            }
            if (local != null) return@launch onResolved(PlaybackRequest.local(local), episode)

            val video = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not resume ${episode.name}" } }
                .getOrDefault(emptyList())
                .let { getEpisodeVideos.playable(anime.source, it) }
            onResolved(video?.let(PlaybackRequest::from), episode)
        }
    }

    data class State(
        val isLoading: Boolean = true,
        val history: List<AnimeHistoryUiModel> = emptyList(),
        val searchQuery: String? = null,
    ) {
        val isEmpty: Boolean get() = history.isEmpty()
    }
}

/** A day's header, or one watched episode. The twin of Mihon's HistoryUiModel. */
sealed interface AnimeHistoryUiModel {
    data class Header(val date: LocalDate) : AnimeHistoryUiModel
    data class Item(val item: AnimeHistoryWithRelations) : AnimeHistoryUiModel
}
