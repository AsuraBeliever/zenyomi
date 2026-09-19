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
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.domain.anime.interactor.GetSkipIntervals
import eu.kanade.domain.anime.interactor.ResolveEpisodeVideo
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.tachiyomi.ui.animeplayer.setting.PlayerPreferences
import eu.kanade.tachiyomi.ui.animeplayer.setting.SubtitlePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.service.getEpisodeSort
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import java.util.Date

/**
 * Drives one watching session: what is playing, what comes next, and how far it got.
 *
 * It holds an episode rather than *being* one. A player tied to a single episode meant that
 * finishing one sent the viewer back to the entry screen to find the next by hand, which is
 * the one thing nobody does once per episode willingly. Everything the switch needs —
 * the neighbours, the resolution, the progress of the episode being left — lives here, so the
 * screen only has to say "open that one".
 */
@AssistedInject
class AnimePlayerViewModel(
    @Assisted private val episodeId: Long,
    @Assisted private val request: PlaybackRequest,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val resolveEpisodeVideo: ResolveEpisodeVideo,
    private val getSkipIntervals: GetSkipIntervals,
    private val updateEpisode: UpdateEpisode,
    private val upsertAnimeHistory: UpsertAnimeHistory,
    private val trackEpisode: TrackEpisode,
    private val getEpisodeVideos: GetEpisodeVideos,
    private val playerPreferences: PlayerPreferences,
    private val subtitlePreferences: SubtitlePreferences,
) : ViewModel() {

    /**
     * Guards the tracker push so it happens once per episode, on the transition to seen,
     * rather than on every progress save after the threshold is crossed. A set rather than a
     * flag because a session is no longer one episode long.
     */
    private val pushedToTrackers = mutableSetOf<Long>()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The anime being watched, held for resolving its other episodes. */
    private var anime: Anime? = null

    /** Its episodes in the order the entry screen lists them, which is what "next" means. */
    private var episodes: List<Episode> = emptyList()

    /**
     * What AniSkip answered for each episode, answer or not, for as long as the player is
     * open.
     *
     * A map rather than a set of "already asked": switching episode clears the intervals from
     * the state, so going back to one — with the previous-episode button, or round a loop of
     * two — has to put them back. Remembering only that the question had been asked meant the
     * button lost its exact answer and the credits stopped being credits.
     */
    private val skipIntervals = mutableMapOf<Long, GetSkipIntervals.Intervals?>()

    private var switchJob: Job? = null
    private var prefetchJob: Job? = null

    /**
     * The next episode's video, resolved before it is needed.
     *
     * Resolving is the slow part of opening an episode — hoster list, video list, extractor
     * chain — and at the end of an episode there is a minute of credits in which to pay it.
     * Without this the countdown ends and the viewer watches a spinner for the ten seconds
     * they were promised would be automatic.
     */
    private var prefetched: Pair<Long, PlaybackRequest>? = null

    init {
        viewModelScope.launch {
            val episode = getEpisode.await(episodeId)
            _state.update {
                it.copy(
                    loaded = true,
                    title = episode?.name.orEmpty(),
                    playback = Playback(
                        episodeId = episodeId,
                        request = request,
                        resumeAt = episode.resumePoint(),
                    ),
                )
            }
            loadNeighbours(episodeId)
        }
    }

    /**
     * Restarting an episode already finished is more useful than dropping the viewer back at
     * the credits.
     */
    private fun Episode?.resumePoint(): Int =
        if (this == null || seen) 0 else lastSecondSeen.toInt()

    private suspend fun loadNeighbours(episodeId: Long) {
        val episode = getEpisode.await(episodeId) ?: return
        val anime = anime ?: getAnime.await(episode.animeId)?.also { anime = it } ?: return
        // Re-read rather than kept from the first pass: an episode seen a moment ago is a
        // different row now, and a library update during a marathon adds rows at the end.
        episodes = getEpisodesByAnimeId.await(anime.id)
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
        val index = episodes.indexOfFirst { it.id == episodeId }
        _state.update {
            it.copy(
                previous = if (index > 0) episodes[index - 1] else null,
                next = if (index >= 0) episodes.getOrNull(index + 1) else null,
            )
        }
    }

    /**
     * Opens another episode without leaving the player.
     *
     * The position and duration of the one being left come from the screen rather than from
     * the last periodic save: pressing "next" two seconds into the credits should not lose
     * those two seconds, and at the end of an episode those are the seconds that decide
     * whether it counts as seen.
     */
    fun open(episode: Episode, positionSeconds: Int, durationSeconds: Int) {
        if (switchJob?.isActive == true) return
        switchJob = viewModelScope.launch {
            saveProgress(positionSeconds, durationSeconds)
            _state.update { it.copy(switching = true, switchError = null) }
            val resolved = requestFor(episode)
            if (resolved == null) {
                _state.update { it.copy(switching = false) }
                return@launch
            }
            _state.update {
                it.copy(
                    switching = false,
                    title = episode.name,
                    opening = null,
                    openingDrift = 0,
                    ending = null,
                    playback = Playback(
                        episodeId = episode.id,
                        request = resolved,
                        resumeAt = episode.resumePoint(),
                        // Bumped rather than taken from the id, so replaying the episode that
                        // is already open still reaches mpv as a new file.
                        serial = (it.playback?.serial ?: 0) + 1,
                    ),
                )
            }
            loadNeighbours(episode.id)
        }
    }

    /** Null with [State.switchError] set when the episode could not be opened. */
    private suspend fun requestFor(episode: Episode): PlaybackRequest? {
        prefetched?.takeIf { it.first == episode.id }?.let { (_, request) ->
            prefetched = null
            return request
        }
        val anime = anime ?: return null
        return when (val resolved = resolveEpisodeVideo.await(anime, episode)) {
            is ResolveEpisodeVideo.Result.Playable -> resolved.request
            is ResolveEpisodeVideo.Result.Failed -> {
                _state.update { it.copy(switchError = resolved.reason) }
                null
            }
        }
    }

    /**
     * Resolves the next episode ahead of time. Called as the current one nears its end, and
     * cheap to call again: it does nothing once the answer is in hand.
     */
    fun prefetchNext() {
        val next = state.value.next ?: return
        if (prefetched?.first == next.id || prefetchJob?.isActive == true) return
        prefetchJob = viewModelScope.launch {
            val anime = anime ?: return@launch
            val resolved = resolveEpisodeVideo.await(anime, next)
            if (resolved is ResolveEpisodeVideo.Result.Playable) {
                prefetched = next.id to resolved.request
            }
        }
    }

    /**
     * Asks AniSkip where this episode's opening and ending are.
     *
     * Needs the episode's length, which only mpv knows and only once the file is open, so the
     * screen calls this rather than the other way round. Once per episode: the answer is a
     * property of the episode and the screen asks on every duration change.
     */
    fun loadSkipIntervals(durationSeconds: Int) {
        if (durationSeconds <= 0 || !playerPreferences.aniskipEnabled.get()) return
        val episodeId = state.value.playback?.episodeId ?: return
        if (skipIntervals.containsKey(episodeId)) {
            apply(episodeId, skipIntervals[episodeId])
            return
        }
        // Claimed before the request so a second call while it is in flight does not make a
        // second one; the answer replaces this.
        skipIntervals[episodeId] = null
        viewModelScope.launch {
            val episode = getEpisode.await(episodeId) ?: return@launch
            val intervals = getSkipIntervals.await(episode.animeId, episode.episodeNumber, durationSeconds)
            skipIntervals[episodeId] = intervals
            apply(episodeId, intervals)
        }
    }

    /** Puts intervals on the state, unless the player has moved on to another episode. */
    private fun apply(episodeId: Long, intervals: GetSkipIntervals.Intervals?) {
        _state.update {
            // A slow answer must not land on the episode after the one it was asked about.
            if (it.playback?.episodeId != episodeId) {
                it
            } else {
                it.copy(
                    opening = intervals?.opening,
                    openingDrift = intervals?.openingDrift ?: 0,
                    ending = intervals?.ending,
                )
            }
        }
    }

    fun clearSwitchError() = _state.update { it.copy(switchError = null) }

    /**
     * Drops the url the current episode was opened with.
     *
     * A resolved url is reused for a few minutes so that stepping out and back in does not
     * re-run the whole resolution. One that mpv could not play has to leave that cache on the
     * way out, or every retry is handed the same dead link and the episode looks broken
     * rather than unlucky.
     */
    fun onPlaybackFailed() {
        val episodeId = state.value.playback?.episodeId ?: return
        getEpisodeVideos.forget(episodeId)
        // The same url, kept for the episode after this one, is no more alive than this one.
        prefetched = null
    }

    fun saveProgress(positionSeconds: Int, durationSeconds: Int) {
        if (durationSeconds <= 0) return
        val episodeId = state.value.playback?.episodeId ?: return
        // launchNonCancellable, como hace el lector de manga al guardar la pagina: el ultimo
        // guardado ocurre cuando el reproductor se desmonta, y para entonces la actividad ya
        // se esta cerrando y viewModelScope esta cancelado. Con `launch` a secas ese guardado
        // no llegaba a la base de datos, asi que salir de una pausa perdia el avance.
        viewModelScope.launchNonCancellable {
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

            if (seen && pushedToTrackers.add(episodeId)) {
                val episode = getEpisode.await(episodeId) ?: return@launchNonCancellable
                trackEpisode.await(episode.animeId, episode.episodeNumber)
            }
        }
    }

    data class State(
        val loaded: Boolean = false,
        /** The episode's own name, which is what the player's title bar shows. */
        val title: String = "",
        val playback: Playback? = null,
        val previous: Episode? = null,
        val next: Episode? = null,
        /** An episode is being resolved; the picture on screen is still the old one. */
        val switching: Boolean = false,
        val switchError: Throwable? = null,
        /** Where AniSkip says the opening is, in seconds. Null when nobody knows. */
        val opening: IntRange? = null,
        /** How far [opening] may be from where it really is. See `GetSkipIntervals.driftFor`. */
        val openingDrift: Int = 0,
        /** The same for the ending, which is where the next episode is announced. */
        val ending: IntRange? = null,
    )

    /**
     * One file for mpv to open.
     *
     * [serial] is what the screen watches: the request alone cannot be compared usefully, and
     * the episode id says nothing when the same episode is opened twice.
     */
    data class Playback(
        val episodeId: Long,
        val request: PlaybackRequest,
        val resumeAt: Int,
        val serial: Int = 1,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(episodeId: Long, request: PlaybackRequest): AnimePlayerViewModel
    }

    /** Exposed so the player can apply them to mpv when a file opens. */
    val preferences: PlayerPreferences get() = playerPreferences

    /** Exposed so the player can style the subtitles, and the in-player panel can change them. */
    val subtitles: SubtitlePreferences get() = subtitlePreferences
}
