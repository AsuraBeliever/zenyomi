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
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.tachiyomi.ui.animeplayer.setting.PlayerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import java.util.Date

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
    private val trackEpisode: TrackEpisode,
    private val playerPreferences: PlayerPreferences,
) : ViewModel() {

    /**
     * Guards the tracker push so it happens once per playback, on the transition to seen,
     * rather than on every progress save after the threshold is crossed.
     */
    private var pushedToTrackers = false

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
            val threshold = playerPreferences.seenThreshold.get().coerceIn(1, 100) / 100.0
            val seen = positionSeconds >= durationSeconds * threshold
            updateEpisode.await(
                EpisodeUpdate(
                    id = episodeId,
                    lastSecondSeen = positionSeconds.toLong(),
                    totalSeconds = durationSeconds.toLong(),
                    seen = seen,
                ),
            )

            if (seen && !pushedToTrackers) {
                pushedToTrackers = true
                val episode = getEpisode.await(episodeId) ?: return@launch
                trackEpisode.await(episode.animeId, episode.episodeNumber)
            }
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

    /** Exposed so the player can apply them to mpv when a file opens. */
    val preferences: PlayerPreferences get() = playerPreferences
}
