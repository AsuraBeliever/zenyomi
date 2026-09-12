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
import eu.kanade.presentation.anime.NoVideoFoundException
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
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
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
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
        viewModelScope.launch {
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
            downloadManager.enqueue(anime, listOf(episode))
        }
    }

    fun deleteDownload(update: AnimeUpdatesWithRelations) {
        viewModelScope.launch {
            val (anime, source, episode) = resolve(update) ?: return@launch
            downloadManager.deleteEpisode(anime, source, episode)
            refreshDownloadState(
                updates = state.value.items.filterIsInstance<AnimeUpdatesUiItem.Item>().map { it.update },
                force = true,
            )
        }
    }

    /**
     * Marks an episode seen or unseen by hand, for one watched somewhere else.
     *
     * Marking it seen pushes to the trackers the same way finishing it in the player does;
     * unmarking it does not, because no tracker supports moving a count backwards from here.
     */
    fun toggleSeen(update: AnimeUpdatesWithRelations) {
        viewModelScope.launch {
            val seen = !update.seen
            updateEpisode.await(
                EpisodeUpdate(
                    id = update.episodeId,
                    seen = seen,
                    lastSecondSeen = if (seen) update.lastSecondSeen else 0L,
                ),
            )
            if (seen) {
                val episode = getEpisode.await(update.episodeId) ?: return@launch
                trackEpisode.await(episode.animeId, episode.episodeNumber)
            }
        }
    }

    /**
     * Asks the source for the episode's video and hands back what to open, or null with the
     * reason. Mirrors the entry screen: a downloaded copy wins, and failure is reported
     * rather than thrown, because a source that cannot answer is a normal outcome.
     */
    fun resolveVideo(
        update: AnimeUpdatesWithRelations,
        onResolved: (url: String?, headers: Map<String, String>) -> Unit,
    ) {
        _state.update { it.copy(resolvingEpisodeId = update.episodeId) }
        viewModelScope.launch {
            val resolved = resolve(update)
            if (resolved == null) {
                _state.update { it.copy(resolvingEpisodeId = null, playbackError = NoVideoFoundException()) }
                return@launch onResolved(null, emptyMap())
            }
            val (anime, source, episode) = resolved

            val local = downloadManager.downloadedUri(anime, source, episode)
            if (local != null) {
                _state.update { it.copy(resolvingEpisodeId = null) }
                return@launch onResolved(local, emptyMap())
            }

            val result = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not resolve ${episode.name}" } }
            val video = result.getOrDefault(emptyList())
                .let { with(getEpisodeVideos) { it.best() } }

            _state.update {
                it.copy(
                    resolvingEpisodeId = null,
                    // The throwable travels, not a string: AnimeSourceError decides what the
                    // user is told, and it is the only place that decision is made.
                    playbackError = when {
                        video != null -> null
                        result.isFailure -> result.exceptionOrNull()
                        else -> NoVideoFoundException()
                    },
                )
            }
            onResolved(
                video?.videoUrl,
                video?.headers?.let { headers ->
                    headers.names().associateWith { headers[it].orEmpty() }
                }.orEmpty(),
            )
        }
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
    ) {
        val isEmpty: Boolean get() = items.isEmpty()
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
