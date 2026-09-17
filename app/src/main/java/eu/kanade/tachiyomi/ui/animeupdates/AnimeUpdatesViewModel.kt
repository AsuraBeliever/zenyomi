package eu.kanade.tachiyomi.ui.animeupdates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.presentation.anime.AnimeSourceHealth
import eu.kanade.presentation.anime.NoVideoFoundException
import eu.kanade.presentation.anime.SourceOutdatedException
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.StartAnimeDownload
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Episodes the anime library has gained, newest first.
 *
 * Deliberately not folded into Mihon's [eu.kanade.tachiyomi.ui.updates.UpdatesViewModel]: that
 * screen is built around the manga database and carries filters, selection and deletion that
 * would all need a second implementation anyway. Rewriting it to serve both would mean
 * rewriting a screen that has to keep merging cleanly from upstream.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeUpdatesViewModel(
    private val getAnimeUpdates: GetAnimeUpdates,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val updateEpisode: UpdateEpisode,
    private val trackEpisode: TrackEpisode,
    private val getEpisodeVideos: GetEpisodeVideos,
    private val sourceHealth: AnimeSourceHealth,
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val startAnimeDownload: StartAnimeDownload,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val downloadProgress = downloadManager.progress

    val downloadQueue = downloadManager.queue

    /**
     * Episode ids the download state was last scanned for. The list re-emits on any change to
     * the episode rows — marking one watched, for instance — and rescanning hundreds of files
     * for that would be wasted work, so a scan only happens when the set of episodes changes.
     */
    private var scannedEpisodeIds: Set<Long> = emptySet()

    init {
        // launchIO: reading and mapping the update rows happens on the collecting thread.
        viewModelScope.launchIO {
            getAnimeUpdates.subscribe(Clock.System.now() - HOW_FAR_BACK).collect { updates ->
                _state.update { it.copy(isLoading = false, items = updates.toUiItems()) }
                refreshDownloadState(updates)
            }
        }
    }

    /**
     * Groups by the day the episode was fetched. The list arrives ordered by the view, so a
     * header is emitted whenever the day changes rather than by sorting again.
     */
    private fun List<AnimeUpdatesWithRelations>.toUiItems(): List<AnimeUpdatesUiItem> {
        val items = mutableListOf<AnimeUpdatesUiItem>()
        var lastDate: LocalDate? = null
        forEach { update ->
            val date = update.dateFetch.toLocalDate()
            if (date != lastDate) {
                items += AnimeUpdatesUiItem.Header(update.dateFetch)
                lastDate = date
            }
            items += AnimeUpdatesUiItem.Item(update)
        }
        return items
    }

    private fun Long.toLocalDate(): LocalDate = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

    private suspend fun refreshDownloadState(updates: List<AnimeUpdatesWithRelations>, force: Boolean = false) {
        val episodeIds = updates.mapTo(mutableSetOf()) { it.episodeId }
        if (!force && episodeIds == scannedEpisodeIds) return
        scannedEpisodeIds = episodeIds

        // One lookup per anime and per source rather than per row: an updates list is mostly
        // several episodes of the same few series.
        val animeById = updates.distinctBy { it.animeId }
            .mapNotNull { update -> getAnime.await(update.animeId)?.let { update.animeId to it } }
            .toMap()
        val sourceById = animeById.values.distinctBy { it.source }
            .mapNotNull { anime -> sourceManager.get(anime.source)?.let { anime.source to it } }
            .toMap()

        val downloaded = mutableSetOf<Long>()
        val downloadable = mutableSetOf<Long>()
        updates.forEach { update ->
            val anime = animeById[update.animeId] ?: return@forEach
            val source = sourceById[anime.source] ?: return@forEach
            // The local source has nothing to download: the file is already on the device.
            if (downloadManager.isDownloadableSource(source)) downloadable += update.episodeId
            val episode = getEpisode.await(update.episodeId) ?: return@forEach
            if (downloadManager.isDownloaded(anime, source, episode)) downloaded += update.episodeId
        }
        _state.update {
            it.copy(downloadedEpisodeIds = downloaded, downloadableEpisodeIds = downloadable)
        }
    }

    fun downloadEpisode(update: AnimeUpdatesWithRelations) {
        viewModelScope.launch {
            val (anime, _, episode) = resolve(update) ?: return@launch
            when (val outcome = startAnimeDownload.await(anime, listOf(episode))) {
                is StartAnimeDownload.Outcome.Queued -> Unit
                is StartAnimeDownload.Outcome.Choose ->
                    _state.update { it.copy(qualityDialog = anime to outcome) }
            }
        }
    }

    fun confirmQuality(height: Int?) {
        val (anime, choice) = state.value.qualityDialog ?: return
        _state.update { it.copy(qualityDialog = null) }
        startAnimeDownload.confirm(anime, choice, height)
    }

    fun dismissQualityDialog() = _state.update { it.copy(qualityDialog = null) }

    fun deleteDownload(update: AnimeUpdatesWithRelations) {
        // launchIO, like the collector above: deleting the file and rescanning what is left
        // are both storage work, and refreshDownloadState carries the scanned-ids field
        // between them, which two dispatchers would race over.
        viewModelScope.launchIO {
            val (anime, source, episode) = resolve(update) ?: return@launchIO
            downloadManager.deleteEpisode(anime, source, episode)
            refreshDownloadState(
                updates = state.value.items.filterIsInstance<AnimeUpdatesUiItem.Item>().map { it.update },
                force = true,
            )
        }
    }

    /**
     * Long-pressing a row starts a selection, as it does on the manga updates screen.
     *
     * That screen has no per-row buttons either: marking seen, downloading and deleting all
     * happen to a selection, through the same bottom menu Mihon draws. Copying its row without
     * copying this would have quietly removed the only way to mark an episode watched by hand.
     */
    fun toggleSelection(update: AnimeUpdatesWithRelations) {
        _state.update { state ->
            val id = update.episodeId
            state.copy(
                selected = if (id in state.selected) state.selected - id else state.selected + id,
            )
        }
    }

    fun selectAll() = _state.update { state ->
        state.copy(selected = state.updates.mapTo(mutableSetOf()) { it.episodeId })
    }

    fun invertSelection() = _state.update { state ->
        state.copy(selected = state.updates.map { it.episodeId }.toSet() - state.selected)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Marks everything picked out seen, or unseen, in one write. */
    fun markSelected(seen: Boolean) {
        val chosen = state.value.selectedUpdates
        clearSelection()
        viewModelScope.launchIO {
            updateEpisode.awaitAll(
                chosen.map { update ->
                    EpisodeUpdate(
                        id = update.episodeId,
                        seen = seen,
                        lastSecondSeen = if (seen) update.lastSecondSeen else 0L,
                    )
                },
            )
            if (!seen) return@launchIO
            // The trackers only move forwards, so unmarking tells them nothing.
            chosen.forEach { update ->
                val episode = getEpisode.await(update.episodeId) ?: return@forEach
                trackEpisode.await(episode.animeId, episode.episodeNumber)
            }
        }
    }

    fun downloadSelected() {
        val chosen = state.value.selectedUpdates
        clearSelection()
        chosen.forEach { downloadEpisode(it) }
    }

    fun deleteSelected() {
        val chosen = state.value.selectedUpdates
        clearSelection()
        chosen.forEach { deleteDownload(it) }
    }

    /**
     * Asks the source for the episode's video and hands back what to open, or null with the
     * reason. Mirrors the entry screen: a downloaded copy wins, and failure is reported
     * rather than thrown, because a source that cannot answer is a normal outcome.
     */
    fun resolveVideo(
        update: AnimeUpdatesWithRelations,
        onResolved: (PlaybackRequest?) -> Unit,
    ) {
        _state.update { it.copy(resolvingEpisodeId = update.episodeId) }
        viewModelScope.launch {
            val resolved = resolve(update)
            if (resolved == null) {
                _state.update { it.copy(resolvingEpisodeId = null, playbackError = NoVideoFoundException()) }
                return@launch onResolved(null)
            }
            val (anime, source, episode) = resolved

            val local = withIOContext { downloadManager.downloadedUri(anime, source, episode) }
            if (local != null) {
                _state.update { it.copy(resolvingEpisodeId = null) }
                return@launch onResolved(PlaybackRequest.local(local))
            }

            val result = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not resolve ${episode.name}" } }
            val video = result.getOrDefault(emptyList())
                .let { getEpisodeVideos.playable(anime.source, it) }

            _state.update {
                it.copy(
                    resolvingEpisodeId = null,
                    // The throwable travels, not a string: AnimeSourceError decides what the
                    // user is told, and it is the only place that decision is made.
                    playbackError = when {
                        video != null -> null
                        result.isFailure -> result.exceptionOrNull()
                        else -> noVideoReason(anime.source)
                    },
                )
            }
            onResolved(video?.let(PlaybackRequest::from))
        }
    }

    /**
     * Why an episode produced no video: the episode, or the extension.
     *
     * Worth separating because the two ask opposite things of the user. Our last sweep of the
     * installed sources already knows which ones stopped working; saying "no video found for
     * this episode" about one of those sends people to try episode after episode of a source
     * that will never answer.
     */
    private fun noVideoReason(sourceId: Long): Throwable =
        if (sourceHealth.statusOf(sourceId) != null) {
            SourceOutdatedException()
        } else {
            NoVideoFoundException()
        }

    fun clearPlaybackError() = _state.update { it.copy(playbackError = null) }

    private suspend fun resolve(update: AnimeUpdatesWithRelations): Triple<Anime, AnimeSource, Episode>? {
        val anime = getAnime.await(update.animeId) ?: return null
        val source = sourceManager.get(anime.source) ?: return null
        val episode = getEpisode.await(update.episodeId) ?: return null
        return Triple(anime, source, episode)
    }

    data class State(
        val isLoading: Boolean = true,
        val items: List<AnimeUpdatesUiItem> = emptyList(),
        val downloadedEpisodeIds: Set<Long> = emptySet(),
        val downloadableEpisodeIds: Set<Long> = emptySet(),
        val resolvingEpisodeId: Long? = null,
        val playbackError: Throwable? = null,
        /** Episode ids picked out by long-pressing, as on the manga updates screen. */
        val selected: Set<Long> = emptySet(),
        /** The anime waiting on a quality, and what it has on offer. */
        val qualityDialog: Pair<Anime, StartAnimeDownload.Outcome.Choose>? = null,
    ) {
        val isEmpty: Boolean get() = items.isEmpty()

        val updates: List<AnimeUpdatesWithRelations>
            get() = items.filterIsInstance<AnimeUpdatesUiItem.Item>().map { it.update }

        val selectedUpdates: List<AnimeUpdatesWithRelations>
            get() = updates.filter { it.episodeId in selected }
    }

    companion object {
        /**
         * Same window Mihon gives its manga updates: anything older is library browsing, not
         * news, and the screen would otherwise grow without limit.
         */
        private val HOW_FAR_BACK = 90.days
    }
}

sealed interface AnimeUpdatesUiItem {
    data class Header(val dateFetch: Long) : AnimeUpdatesUiItem
    data class Item(val update: AnimeUpdatesWithRelations) : AnimeUpdatesUiItem
}
