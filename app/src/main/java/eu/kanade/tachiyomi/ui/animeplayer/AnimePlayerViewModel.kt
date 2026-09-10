package eu.kanade.tachiyomi.ui.animeplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import java.util.Date
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate

/**
 * Keeps an episode's watch progress.
 *
 * Progress is written as the video plays rather than only on exit, so a process death
 * mid-episode still leaves a usable resume point.
 */
@AssistedInject
class AnimePlayerViewModel(
    @Assisted private val episodeId: Long,
    private val getEpisode: GetEpisode,
    private val updateEpisode: UpdateEpisode,
    private val upsertAnimeHistory: UpsertAnimeHistory,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val episode = getEpisode.await(episodeId)
            _state.update {
                it.copy(
                    loaded = true,
                    // Restarting an episode already finished is more useful than dropping
                    // the viewer back at the credits.
                    resumeAt = if (episode?.seen == true) 0 else episode?.lastSecondSeen?.toInt() ?: 0,
                )
            }
        }
    }

    fun saveProgress(positionSeconds: Int, durationSeconds: Int) {
        if (durationSeconds <= 0) return
        viewModelScope.launch {
            // History is what drives the recents list, so it is stamped on every save
            // rather than only when an episode finishes.
            upsertAnimeHistory.await(
                AnimeHistoryUpdate(episodeId = episodeId, seenAt = Date()),
            )
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId,
                    lastSecondSeen = positionSeconds.toLong(),
                    totalSeconds = durationSeconds.toLong(),
                    seen = positionSeconds >= durationSeconds * SEEN_THRESHOLD,
                ),
            )
        }
    }

    data class State(
        val loaded: Boolean = false,
        val resumeAt: Int = 0,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(episodeId: Long): AnimePlayerViewModel
    }

    companion object {
        /** Past this fraction an episode counts as watched, matching what readers do for chapters. */
        private const val SEEN_THRESHOLD = 0.85
    }
}
