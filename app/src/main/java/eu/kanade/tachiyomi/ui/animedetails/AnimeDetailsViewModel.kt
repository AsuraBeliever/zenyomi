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
import eu.kanade.domain.anime.model.copyFrom
import eu.kanade.domain.anime.model.downloadedFilter
import eu.kanade.domain.anime.model.episodesFiltered
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.presentation.anime.AnimeSourceHealth
import eu.kanade.presentation.anime.NoVideoFoundException
import eu.kanade.presentation.anime.SourceOutdatedException
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.toAnimeUpdate
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks

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
    private val sourceHealth: AnimeSourceHealth,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val sourceManager: AnimeSourceManager,
    private val updateAnime: UpdateAnime,
    private val downloadManager: AnimeDownloadManager,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getAnimeTracks: GetAnimeTracks,
    private val getNextEpisodes: GetNextEpisodes,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
) : ViewModel() {

    private var episodesFetched = false

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        // launchIO, as Mihon's MangaScreenModel does, and not the default main dispatcher:
        // the query and the row mapping happen on whichever thread collects, and the mapping
        // allocates one Episode per row and decodes a json column for each. On One Piece that
        // is eleven hundred rows per emission, and on the main thread it is eleven hundred
        // rows the UI cannot draw through. distinctUntilChanged because syncing an entry
        // writes in three passes and every one of them wakes this flow with a list that is
        // often identical to the last.
        viewModelScope.launchIO {
            getAnimeWithEpisodesAndSeasons.subscribe(animeId)
                .distinctUntilChanged()
                .collect { (anime, episodes, _) ->
                    _state.update {
                        it.copy(isLoading = false, anime = anime, episodes = episodes)
                    }
                    // The catalogue only stores the entry; its episodes have to be asked for.
                    // Done once. A failure used to be swallowed, which left an entry reading
                    // "0 episodes" whether the source was unreachable, blocked, or genuinely
                    // empty — three very different things with the same appearance.
                    refreshDownloaded(anime, episodes)
                    describeSource(anime)
                    recomputeVisible(anime, episodes, _state.value.downloadedEpisodeIds)
                    if (!episodesFetched) {
                        episodesFetched = true
                        sourceManager.get(anime.source)?.let { source ->
                            fetchDetails(anime, source)
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

    init {
        // The header shows how many trackers the entry is linked to, as the manga one does.
        viewModelScope.launchIO {
            getAnimeTracks.subscribe(animeId).distinctUntilChanged().collect { tracks ->
                _state.update { it.copy(trackingCount = tracks.size) }
            }
        }
    }

    /**
     * Asks the source for the entry's own details, once.
     *
     * A catalogue hands back only what a grid needs — a title and a cover — so an entry reached
     * from one has no description, author or status until somebody asks for them. Mihon asks
     * on open and so does this; without it the header is a row of "Unknown" next to an empty
     * description, which is what the anime screen looked like before it had a header worth
     * filling. Failure is swallowed on purpose: the episodes are the reason the screen exists
     * and they are fetched separately.
     */
    private suspend fun fetchDetails(anime: Anime, source: AnimeSource) {
        if (anime.initialized) return
        val details = runCatching { source.getAnimeDetails(anime.toSAnime()) }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not fetch details for ${anime.title}" } }
            .getOrNull() ?: return
        val updated = anime.copyFrom(details).copy(initialized = true)
        updateAnime.await(updated.toAnimeUpdate())
    }

    /**
     * Who the entry came from, and where to open it on the web.
     *
     * A stub source is one whose extension is gone: the entry is still in the database but
     * nothing can be asked of it, and the header says so rather than pretending otherwise.
     * Already on IO — this is called from the database collector.
     */
    private suspend fun describeSource(anime: Anime) {
        val source = sourceManager.getOrStub(anime.source)
        val url = (source as? AnimeHttpSource)
            ?.let { runCatching { it.getAnimeUrl(anime.toSAnime()) }.getOrNull() }
        _state.update {
            it.copy(
                sourceName = source.name,
                isStubSource = source is StubAnimeSource,
                webViewUrl = url,
            )
        }
    }

    /**
     * The episode the play button opens: the earliest one not yet seen.
     *
     * Through the same interactor the rest of the app uses, so "continue" means the same thing
     * here as it does everywhere else.
     */
    fun continueWatching(onResolved: (PlaybackRequest?, Episode?) -> Unit) {
        viewModelScope.launch {
            val next = withIOContext { getNextEpisodes.await(animeId, onlyUnseen = true) }
                .firstOrNull()
                ?: return@launch onResolved(null, null)
            resolveVideo(next) { request -> onResolved(request, next) }
        }
    }

    /**
     * The job behind [resolvingEpisodeId], kept so the viewer can call it off.
     *
     * Asking a source for a video is a network round trip that can take many seconds, and
     * there was no way out of it but to wait or kill the app — see [cancelResolve].
     */
    private var resolveJob: Job? = null

    /** Abandons the tap. The state clears, the screen unblocks, nothing is opened. */
    fun cancelResolve() {
        resolveJob?.cancel()
        resolveJob = null
        _state.update { it.copy(resolvingEpisodeId = null) }
    }

    /**
     * Asks the source for the episode's videos and hands back the one to open.
     *
     * Sources reach the network here, so this reports failure instead of throwing: an
     * episode that resolves to nothing is a normal outcome when a source needs
     * configuration or the host is down.
     */
    fun resolveVideo(episode: Episode, onResolved: (PlaybackRequest?) -> Unit) {
        val anime = state.value.anime ?: return onResolved(null)
        // A second tap while one is in flight replaces it rather than stacking a second
        // network call behind the first.
        resolveJob?.cancel()
        _state.update { it.copy(resolvingEpisodeId = episode.id) }
        resolveJob = viewModelScope.launch {
            // A downloaded copy wins: it plays offline and costs the source nothing.
            // Off the main thread: looking for the file is a round trip to the storage
            // provider, and this runs from a tap.
            val source = sourceManager.get(anime.source)
            val local = source?.let {
                withIOContext { downloadManager.downloadedUri(anime, it, episode) }
            }
            if (local != null) {
                _state.update { it.copy(resolvingEpisodeId = null) }
                return@launch onResolved(PlaybackRequest.local(local))
            }
            val result = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not resolve ${episode.name}" } }
            val video = result.getOrDefault(emptyList())
                .let { getEpisodeVideos.playable(anime.source, it) }

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
                        else -> noVideoReason(anime.source)
                    },
                )
            }
            // The whole video travels, not just its url: the headers it was resolved with and
            // any side-car subtitle track are as much a part of playing it as the url is.
            onResolved(video?.let(PlaybackRequest::from))
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

    /**
     * The toolbar's batch download, with the same choices the manga one offers.
     *
     * "Next" counts from the earliest unseen episode in the order the list is currently shown,
     * so it means the same thing here as it does over there.
     */
    fun downloadEpisodes(action: DownloadAction) {
        val anime = state.value.anime ?: return
        val unseen = state.value.visibleEpisodes.filterNot { it.seen }
        val wanted = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> unseen.take(1)
            DownloadAction.NEXT_5_CHAPTERS -> unseen.take(5)
            DownloadAction.NEXT_10_CHAPTERS -> unseen.take(10)
            DownloadAction.NEXT_25_CHAPTERS -> unseen.take(25)
            DownloadAction.UNREAD_CHAPTERS -> unseen
            DownloadAction.BOOKMARKED_CHAPTERS -> state.value.visibleEpisodes.filter { it.bookmark }
        }.filterNot { it.id in state.value.downloadedEpisodeIds }
        if (wanted.isNotEmpty()) downloadManager.enqueue(anime, wanted)
    }

    fun deleteDownload(episode: Episode) {
        val anime = state.value.anime ?: return
        viewModelScope.launch {
            val source = sourceManager.get(anime.source) ?: return@launch
            downloadManager.deleteEpisode(anime, source, episode)
            refreshDownloaded()
        }
    }

    /**
     * Which episodes already have a file on disk, so rows can show it.
     *
     * One listing of the entry's download directory, off the main thread. It used to ask the
     * storage framework about each episode in turn, from the main thread, on every emission
     * of the database flow — three round trips per row, so over three thousand of them on One
     * Piece before the screen could draw a frame. That is what made a long entry lock the app
     * up while a short one felt fine.
     */
    fun refreshDownloaded(
        anime: Anime? = state.value.anime,
        episodes: List<Episode> = state.value.episodes,
    ) {
        if (anime == null) return
        viewModelScope.launch {
            val source = sourceManager.get(anime.source) ?: return@launch
            val ids = withIOContext { downloadManager.downloadedEpisodeIds(anime, source, episodes) }
            _state.update {
                it.copy(
                    downloadedEpisodeIds = ids,
                    canDownload = downloadManager.isDownloadableSource(source),
                )
            }
            recomputeVisible(anime, episodes, ids)
        }
    }

    /**
     * Sorts and filters the episode list once, off the screen's thread.
     *
     * Done here rather than in the composition because the list runs to a thousand rows on a
     * long entry, and sorting a thousand rows inside a recomposition is how the screen came to
     * lock the app up in the first place.
     */
    private fun recomputeVisible(anime: Anime, episodes: List<Episode>, downloaded: Set<Long>) {
        val filtered = episodes.filter { episode ->
            val unseenOk = when (anime.unseenFilter) {
                TriState.ENABLED_IS -> !episode.seen
                TriState.ENABLED_NOT -> episode.seen
                TriState.DISABLED -> true
            }
            val downloadedOk = when (anime.downloadedFilter) {
                TriState.ENABLED_IS -> episode.id in downloaded
                TriState.ENABLED_NOT -> episode.id !in downloaded
                TriState.DISABLED -> true
            }
            val bookmarkedOk = when (anime.bookmarkedFilter) {
                TriState.ENABLED_IS -> episode.bookmark
                TriState.ENABLED_NOT -> !episode.bookmark
                TriState.DISABLED -> true
            }
            unseenOk && downloadedOk && bookmarkedOk
        }
        // Mihon's comparator, copied rather than reinvented, including the part that reads
        // backwards: sorting "by source, descending" means ascending *sourceOrder*, because a
        // source lists its newest item first. Writing what looks right instead of what Mihon
        // does is how the two screens end up ordering the same list differently.
        val descending = anime.sortDescending()
        val sorted = filtered.sortedWith { e1, e2 ->
            when (anime.sorting) {
                Anime.EPISODE_SORTING_NUMBER -> if (descending) {
                    e2.episodeNumber.compareTo(e1.episodeNumber)
                } else {
                    e1.episodeNumber.compareTo(e2.episodeNumber)
                }
                Anime.EPISODE_SORTING_UPLOAD_DATE -> if (descending) {
                    e2.dateUpload.compareTo(e1.dateUpload)
                } else {
                    e1.dateUpload.compareTo(e2.dateUpload)
                }
                Anime.EPISODE_SORTING_ALPHABET -> if (descending) {
                    e2.name.compareTo(e1.name)
                } else {
                    e1.name.compareTo(e2.name)
                }
                else -> if (descending) {
                    e1.sourceOrder.compareTo(e2.sourceOrder)
                } else {
                    e2.sourceOrder.compareTo(e1.sourceOrder)
                }
            }
        }

        _state.update {
            it.copy(visibleEpisodes = sorted, filterActive = anime.episodesFiltered())
        }
    }

    fun showEpisodeSettings() = _state.update { it.copy(episodeSettingsDialog = true) }

    fun dismissEpisodeSettings() = _state.update { it.copy(episodeSettingsDialog = false) }

    fun setUnseenFilter(state: TriState) = updateFlags { anime ->
        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
        }
        setAnimeEpisodeFlags.awaitSetUnseenFilter(anime, flag)
    }

    fun setDownloadedFilter(state: TriState) = updateFlags { anime ->
        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_DOWNLOADED
        }
        setAnimeEpisodeFlags.awaitSetDownloadedFilter(anime, flag)
    }

    fun setBookmarkedFilter(state: TriState) = updateFlags { anime ->
        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
        }
        setAnimeEpisodeFlags.awaitSetBookmarkFilter(anime, flag)
    }

    /** Selecting the mode already in use flips the direction, as it does on the manga side. */
    fun setSorting(mode: Long) = updateFlags { anime ->
        setAnimeEpisodeFlags.awaitSetSortingModeOrFlipOrder(anime, mode)
    }

    /**
     * Writes a flag change to the entry. Nothing else is needed: the flags live on the anime
     * row, so the database flow re-emits and the list is recomputed from the new value.
     */
    private fun updateFlags(block: suspend (Anime) -> Unit) {
        val anime = state.value.anime ?: return
        viewModelScope.launchIO { block(anime) }
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
        /** Sorted and filtered, and the only one the screen draws. */
        val visibleEpisodes: List<Episode> = emptyList(),
        val filterActive: Boolean = false,
        val episodeSettingsDialog: Boolean = false,
        val sourceName: String = "",
        val isStubSource: Boolean = false,
        /** Null when the source is not an http one, which hides the WebView button. */
        val webViewUrl: String? = null,
        val trackingCount: Int = 0,
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
