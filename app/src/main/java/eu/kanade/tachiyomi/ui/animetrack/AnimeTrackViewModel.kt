package eu.kanade.tachiyomi.ui.animetrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * Drives the tracking sheet for one anime.
 *
 * Only trackers that implement [AnimeTracker] are offered. The rest of Mihon's trackers are
 * manga-only services, and listing them here would only invite a user to sign in to something
 * that can never hold an anime.
 */
@AssistedInject
class AnimeTrackViewModel(
    @Assisted private val animeId: Long,
    private val getAnime: GetAnime,
    private val getTracks: GetAnimeTracks,
    private val insertTrack: InsertAnimeTrack,
    private val deleteTrack: DeleteAnimeTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    trackerManager: TrackerManager,
) : ViewModel() {

    private val animeTrackers = trackerManager.trackers.filterIsInstance<AnimeTracker>()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getTracks.subscribe(animeId).collect { tracks ->
                _state.update { it.copy(loading = false, tracks = tracks) }
            }
        }
        refreshLoggedIn()
    }

    private fun refreshLoggedIn() {
        _state.update {
            it.copy(
                loggedIn = animeTrackers
                    .filterIsInstance<Tracker>()
                    .filter { tracker -> tracker.isLoggedIn },
            )
        }
    }

    fun search(tracker: Tracker, query: String) {
        _state.update { it.copy(searching = true, searchResults = emptyList(), searchError = false) }
        viewModelScope.launch {
            val results = runCatching { (tracker as AnimeTracker).searchAnime(query) }
                .onFailure { logcat(LogPriority.WARN, it) { "Anime search failed on ${tracker.name}" } }
            _state.update {
                it.copy(
                    searching = false,
                    searchResults = results.getOrDefault(emptyList()),
                    searchError = results.isFailure,
                )
            }
        }
    }

    fun bind(tracker: Tracker, selection: AnimeTrackSearch) {
        viewModelScope.launch {
            val hasSeenEpisodes = getEpisodesByAnimeId.await(animeId).any { it.seen }
            runCatching {
                selection.anime_id = animeId
                val bound = (tracker as AnimeTracker).bindAnime(selection, hasSeenEpisodes)
                insertTrack.await(bound.toDomainTrack(animeId))
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Could not bind on ${tracker.name}" }
                _state.update { state -> state.copy(bindError = true) }
            }
            _state.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun unbind(track: AnimeTrack) {
        viewModelScope.launch {
            // The local row goes regardless: if the service call fails the user still expects
            // the anime to stop being tracked here, and a stale remote entry is recoverable.
            runCatching {
                val tracker = animeTrackers.filterIsInstance<Tracker>()
                    .firstOrNull { it.id == track.trackerId }
                (tracker as? AnimeTracker)?.deleteAnime(track.toDbTrack())
            }.onFailure { logcat(LogPriority.WARN, it) { "Could not remove the remote entry" } }
            deleteTrack.await(track.animeId, track.trackerId)
        }
    }

    fun clearSearch() = _state.update { it.copy(searchResults = emptyList(), searchError = false) }

    suspend fun animeTitle(): String? = getAnime.await(animeId)?.title

    data class State(
        val loading: Boolean = true,
        val tracks: List<AnimeTrack> = emptyList(),
        val loggedIn: List<Tracker> = emptyList(),
        val searching: Boolean = false,
        val searchResults: List<AnimeTrackSearch> = emptyList(),
        val searchError: Boolean = false,
        val bindError: Boolean = false,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long): AnimeTrackViewModel
    }
}
