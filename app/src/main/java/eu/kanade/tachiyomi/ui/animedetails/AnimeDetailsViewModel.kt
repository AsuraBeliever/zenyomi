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
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
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
    private val downloader: AnimeDownloader,
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
                // Done once, and failures are silent because a source being unreachable is
                // ordinary and the stored episodes stay usable.
                refreshDownloaded()
                if (!episodesFetched) {
                    episodesFetched = true
                    sourceManager.get(anime.source)?.let { source ->
                        runCatching { syncEpisodesWithSource.await(anime, source) }
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
    fun resolveVideo(episode: Episode, onResolved: (String?) -> Unit) {
        val anime = state.value.anime ?: return onResolved(null)
        _state.update { it.copy(resolvingEpisodeId = episode.id) }
        viewModelScope.launch {
            // A downloaded copy wins: it plays offline and costs the source nothing.
            val source = sourceManager.get(anime.source)
            val local = source?.let { downloader.downloadedUri(anime, it, episode) }
            if (local != null) {
                _state.update { it.copy(resolvingEpisodeId = null) }
                return@launch onResolved(local)
            }
            val video = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .getOrDefault(emptyList())
                .let { with(getEpisodeVideos) { it.best() } }
            _state.update { it.copy(resolvingEpisodeId = null) }
            onResolved(video?.videoUrl)
        }
    }

    val downloadProgress = downloader.progress

    /**
     * Downloads an episode for offline watching.
     *
     * Resolution runs again here rather than being reused from playback: the two happen
     * at different times and a source can hand back different videos.
     */
    fun downloadEpisode(episode: Episode) {
        val anime = state.value.anime ?: return
        viewModelScope.launch {
            val source = sourceManager.get(anime.source) ?: return@launch
            val video = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .getOrDefault(emptyList())
                .let { with(getEpisodeVideos) { it.best() } }
                ?: return@launch
            downloader.download(anime, source, episode, video)
            refreshDownloaded()
        }
    }

    /** Which episodes already have a file on disk, so rows can show it. */
    private fun refreshDownloaded() {
        val anime = state.value.anime ?: return
        viewModelScope.launch {
            val source = sourceManager.get(anime.source) ?: return@launch
            val ids = state.value.episodes
                .filter { downloader.isDownloaded(anime, source, it) }
                .map { it.id }
                .toSet()
            _state.update {
                it.copy(
                    downloadedEpisodeIds = ids,
                    canDownload = downloader.isDownloadableSource(source),
                )
            }
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
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long): AnimeDetailsViewModel
    }
}
