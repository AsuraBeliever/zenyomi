package eu.kanade.tachiyomi.ui.animedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.domain.anime.interactor.SyncEpisodesWithSource
import eu.kanade.presentation.anime.NoVideoFoundException
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * One anime entry: its details and its episodes.
 *
 * Reads through [GetAnimeWithEpisodesAndSeasons], which watches the local database, so
 * an entry reached from a catalogue works too: the paging source inserts network results
 * as local rows before they ever get here.
 *
 * Seasons come back from the same interactor but have no UI yet.
 */
@AssistedInject
class AnimeDetailsViewModel(
    @Assisted private val animeId: Long,
    private val getAnimeWithEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons,
    private val getEpisodeVideos: GetEpisodeVideos,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val sourceManager: AnimeSourceManager,
    private val updateAnime: UpdateAnime,
    private val downloadManager: AnimeDownloadManager,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
) : ViewModel() {

    private var episodesFetched = false

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getAnimeWithEpisodesAndSeasons.subscribe(animeId).collect { (anime, episodes, _) ->
                _state.update {
                    it.copy(isLoading = false, anime = anime, episodes = episodes)
                }
                // The catalogue only stores the entry; its episodes have to be asked for.
                // Done once. A failure used to be swallowed, which left an entry reading
                // "0 episodes" whether the source was unreachable, blocked, or genuinely
                // empty — three very different things with the same appearance.
                refreshDownloaded()
                if (!episodesFetched) {
                    episodesFetched = true
                    sourceManager.get(anime.source)?.let { source ->
                        runCatching { syncEpisodesWithSource.await(anime, source) }
                            .onFailure { error ->
                                logcat(LogPriority.WARN, error) { "Could not fetch episodes" }
                                _state.update { state ->
                                    state.copy(episodeError = error)
                                }
                            }
                            .onSuccess {
                                _state.update { state -> state.copy(episodeError = null) }
                            }
                    }
                }
            }
        }
    }

    /**
     * Asks the source for the episode's videos and hands back the one to open.
     *
     * Sources reach the network here, so this reports failure instead of throwing: an
     * episode that resolves to nothing is a normal outcome when a source needs
     * configuration or the host is down.
     */
    fun resolveVideo(episode: Episode, onResolved: (url: String?, headers: Map<String, String>) -> Unit) {
        val anime = state.value.anime ?: return onResolved(null, emptyMap())
        _state.update { it.copy(resolvingEpisodeId = episode.id) }
        viewModelScope.launch {
            // A downloaded copy wins: it plays offline and costs the source nothing.
            val source = sourceManager.get(anime.source)
            val local = source?.let { downloadManager.downloadedUri(anime, it, episode) }
            if (local != null) {
                _state.update { it.copy(resolvingEpisodeId = null) }
                return@launch onResolved(local, emptyMap())
            }
            val result = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not resolve ${episode.name}" } }
            val video = result.getOrDefault(emptyList())
                .let { with(getEpisodeVideos) { it.best() } }

            // Tapping an episode that resolves to nothing used to do nothing at all, which
            // is indistinguishable from a tap that missed. Whatever went wrong is said out
            // loud instead.
            _state.update {
                it.copy(
                    resolvingEpisodeId = null,
                    // The throwable travels, not a string: what the user should be told is a
                    // presentation decision, and AnimeSourceError is where it is made.
                    playbackError = when {
                        video != null -> null
                        result.isFailure -> result.exceptionOrNull()
                        else -> NoVideoFoundException()
                    },
                )
            }
            // The headers travel with the url: a video host that checks the Referer answers
            // 403 to mpv otherwise, which looked like a player that would not play.
            onResolved(
                video?.videoUrl,
                video?.headers?.let { headers ->
                    headers.names().associateWith { headers[it].orEmpty() }
                }.orEmpty(),
            )
        }
    }

    val downloadProgress = downloadManager.progress

    val downloadQueue = downloadManager.queue

    init {
        // The job, not this ViewModel, does the downloading, so the only signal that a file
        // landed is the queue shrinking.
        viewModelScope.launch {
            downloadManager.queue
                .map { it.size }
                .distinctUntilChanged()
                .collect { refreshDownloaded() }
        }
    }

    /**
     * Queues an episode for offline watching.
     *
     * The download runs in [eu.kanade.tachiyomi.data.download.anime.AnimeDownloadJob], not here:
     * a video takes long enough that tying it to this ViewModel meant navigating back cancelled
     * it halfway. Video resolution happens in the job too, as late as possible, because a source
     * can hand back a different (or expired) url between queueing and downloading.
     */
    fun downloadEpisode(episode: Episode) {
        val anime = state.value.anime ?: return
        downloadManager.enqueue(anime, listOf(episode))
    }

    fun deleteDownload(episode: Episode) {
        val anime = state.value.anime ?: return
        viewModelScope.launch {
            val source = sourceManager.get(anime.source) ?: return@launch
            downloadManager.deleteEpisode(anime, source, episode)
            refreshDownloaded()
        }
    }

    /** Which episodes already have a file on disk, so rows can show it. */
    fun refreshDownloaded() {
        val anime = state.value.anime ?: return
        viewModelScope.launch {
            val source = sourceManager.get(anime.source) ?: return@launch
            val ids = state.value.episodes
                .filter { downloadManager.isDownloaded(anime, source, it) }
                .map { it.id }
                .toSet()
            _state.update {
                it.copy(
                    downloadedEpisodeIds = ids,
                    canDownload = downloadManager.isDownloadableSource(source),
                )
            }
        }
    }

    fun clearPlaybackError() = _state.update { it.copy(playbackError = null) }

    /**
     * Loads what categories exist and which of them this anime is already in, so the dialog
     * opens with the boxes already ticked rather than asking the user to remember.
     */
    fun showCategoryDialog() {
        viewModelScope.launch {
            val all = getAnimeCategories.await().filterNot { it.isSystemCategory }
            val current = getAnimeCategories.await(animeId).map { it.id }.toSet()
            _state.update { it.copy(categoryDialog = CategoryDialog(all, current)) }
        }
    }

    fun dismissCategoryDialog() = _state.update { it.copy(categoryDialog = null) }

    fun setCategories(categoryIds: List<Long>) {
        viewModelScope.launch {
            setAnimeCategories.await(animeId, categoryIds)
            _state.update { it.copy(categoryDialog = null) }
        }
    }

    fun toggleFavorite() {
        val anime = state.value.anime ?: return
        viewModelScope.launch {
            updateAnime.awaitUpdateFavorite(anime.id, !anime.favorite)
        }
    }

    data class State(
        val isLoading: Boolean = true,
        val anime: Anime? = null,
        val episodes: List<Episode> = emptyList(),
        val resolvingEpisodeId: Long? = null,
        val downloadedEpisodeIds: Set<Long> = emptySet(),
        val canDownload: Boolean = false,
        val episodeError: Throwable? = null,
        val playbackError: Throwable? = null,
        val categoryDialog: CategoryDialog? = null,
    )

    /** The categories that exist, and the ones this anime is currently filed under. */
    data class CategoryDialog(
        val categories: List<AnimeCategory>,
        val selected: Set<Long>,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long): AnimeDetailsViewModel
    }
}
