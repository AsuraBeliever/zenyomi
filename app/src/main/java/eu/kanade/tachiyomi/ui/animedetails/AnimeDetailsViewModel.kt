package eu.kanade.tachiyomi.ui.animedetails

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
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
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.presentation.anime.AnimeSourceHealth
import eu.kanade.presentation.anime.NoVideoFoundException
import eu.kanade.presentation.anime.SourceOutdatedException
import eu.kanade.presentation.manga.DownloadAction
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.StartAnimeDownload
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.domain.anime.model.toAnimeUpdate
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.service.getEpisodeSort
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.MR

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
    private val startAnimeDownload: StartAnimeDownload,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getAnimeTracks: GetAnimeTracks,
    private val getNextEpisodes: GetNextEpisodes,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val updateEpisode: UpdateEpisode,
    private val trackEpisode: TrackEpisode,
    private val libraryPreferences: LibraryPreferences,
    private val imageSaver: ImageSaver,
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
                    // Solo se le pide a la fuente cuando hace falta de verdad, que es lo que
                    // hace la ficha de manga: si la entrada ya esta rellena y sus episodios ya
                    // estan guardados, lo que hay en la base de datos vale y volver a entrar es
                    // instantaneo. Antes se pedia en cada apertura porque la bandera vivia en el
                    // ViewModel, y al salir y volver habia un ViewModel nuevo: una entrada ya
                    // vista costaba otra vuelta a la red para acabar enseñando lo mismo.
                    //
                    // Para forzar una recarga esta el gesto de tirar hacia abajo, que es donde
                    // el usuario lo pide a proposito.
                    val needsDetails = !anime.initialized
                    val needsEpisodes = episodes.isEmpty()
                    if (!episodesFetched && (needsDetails || needsEpisodes)) {
                        episodesFetched = true
                        _state.update { it.copy(isRefreshingData = true) }
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
                        _state.update { it.copy(isRefreshingData = false) }
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
     *
     * @param ask show the quality question for this download alone, whatever quality is
     * saved. What a long press on the download button is for.
     */
    fun downloadEpisode(episode: Episode, ask: Boolean = false) = startDownload(listOf(episode), ask)

    /**
     * The toolbar's batch download, with the same choices the manga one offers.
     *
     * "Next" means the next ones to watch — counted forwards from the earliest unseen episode,
     * never from whatever happens to be at the top of the screen. That distinction is the whole
     * point of the option: with the list newest-first, which is how anime is usually read, this
     * used to take the *last* five episodes of the entry and call them the next five. Mihon
     * sorts the same way for the same reason ([tachiyomi.domain.episode.service.getEpisodeSort]
     * with the direction forced, exactly as `getUnreadChaptersSorted` does).
     */
    fun downloadEpisodes(action: DownloadAction) {
        val anime = state.value.anime ?: return
        // What you already have is out of the running before the count starts, never after.
        // Counting first and discarding afterwards makes "the next five" mean "of the next
        // five, the ones I am missing" — and when the next one is already on disk, asking for
        // it queues nothing at all and the button looks broken. Mihon drops them up front for
        // this reason; so does this.
        val missing = { episode: Episode ->
            episode.id !in state.value.downloadedEpisodeIds &&
                downloadManager.queue.value.none { it.episodeId == episode.id }
        }
        val pending = state.value.visibleEpisodes
            .filterNot { it.seen }
            .filter(missing)
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
        val wanted = when (action) {
            DownloadAction.NEXT_1_CHAPTER -> pending.take(1)
            DownloadAction.NEXT_5_CHAPTERS -> pending.take(5)
            DownloadAction.NEXT_10_CHAPTERS -> pending.take(10)
            DownloadAction.NEXT_25_CHAPTERS -> pending.take(25)
            DownloadAction.UNREAD_CHAPTERS -> pending
            DownloadAction.BOOKMARKED_CHAPTERS ->
                state.value.visibleEpisodes
                    .filter { it.bookmark }
                    .filter(missing)
        }
        if (wanted.isNotEmpty()) startDownload(wanted)
    }

    /**
     * Marks the rows as busy before anything is asked of the network, and unmarks them once
     * the episodes are queued or the dialog is up.
     *
     * The button has to react to the first tap. Queueing at a settled quality is immediate,
     * but the first download of all — and every long press — has to go and ask the source what
     * it has and weigh it, which is seconds of a button that did nothing. The state existed and
     * nothing was reading it; the row now spins on it, exactly as it does once the episode is
     * really in the queue, so the wait looks like the beginning of the download it is.
     */
    private fun startDownload(episodes: List<Episode>, ask: Boolean = false) {
        val anime = state.value.anime ?: return
        if (episodes.isEmpty()) return
        val preparing = episodes.mapTo(mutableSetOf()) { it.id }
        _state.update { it.copy(preparingEpisodeIds = it.preparingEpisodeIds + preparing) }
        viewModelScope.launch {
            val outcome = startAnimeDownload.await(anime, episodes, ask)
            _state.update {
                it.copy(
                    preparingEpisodeIds = it.preparingEpisodeIds - preparing,
                    qualityDialog = outcome as? StartAnimeDownload.Outcome.Choose,
                )
            }
        }
    }

    fun confirmQuality(height: Int?) {
        val anime = state.value.anime ?: return
        val dialog = state.value.qualityDialog ?: return
        _state.update { it.copy(qualityDialog = null) }
        startAnimeDownload.confirm(anime, dialog, height)
    }

    fun dismissQualityDialog() = _state.update { it.copy(qualityDialog = null) }

    /**
     * Calls off a download, whether it is waiting its turn or coming down right now.
     *
     * Not the same thing as [deleteDownload], which is for a file that is already there. These
     * were wired to the same method, so cancelling a download in flight deleted a file that did
     * not exist yet and left the video coming down regardless.
     */
    fun cancelDownload(episode: Episode) {
        downloadManager.cancel(episode.id)
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

    /**
     * Vuelve a pedirle a la fuente los detalles y los episodios, que es lo que hace tirar hacia
     * abajo en la ficha de un manga. La sincronizacion inicial solo corre una vez por pantalla;
     * esto es la forma de pedirla de nuevo a proposito.
     */
    fun refreshFromSource() {
        val anime = state.value.anime ?: return
        if (state.value.isRefreshingData) return
        _state.update { it.copy(isRefreshingData = true) }
        viewModelScope.launchIO {
            try {
                sourceManager.get(anime.source)?.let { source ->
                    fetchDetails(anime, source)
                    runCatching { syncEpisodesWithSource.await(anime, source) }
                        .onFailure { error ->
                            logcat(LogPriority.WARN, error) { "Could not refresh episodes" }
                            _state.update { it.copy(episodeError = error) }
                        }
                        .onSuccess { _state.update { it.copy(episodeError = null) } }
                }
            } finally {
                _state.update { it.copy(isRefreshingData = false) }
            }
        }
    }

    // Las mismas preferencias que la lista de capitulos: el gesto significa lo mismo a los dos
    // lados, asi que se lee el ajuste de Mihon en vez de inventar uno propio que el usuario
    // tendria que configurar dos veces.
    val episodeSwipeStartAction = libraryPreferences.swipeToEndAction.get()
    val episodeSwipeEndAction = libraryPreferences.swipeToStartAction.get()

    fun swipeEpisode(episode: Episode, action: LibraryPreferences.ChapterSwipeAction) {
        when (action) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead ->
                markEpisodesSeen(listOf(episode), !episode.seen)
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark ->
                bookmarkEpisodes(listOf(episode), !episode.bookmark)
            LibraryPreferences.ChapterSwipeAction.Download -> {
                // El estado de descarga ya vive en el estado de la pantalla, asi que se pregunta
                // ahi en vez de volver a consultar al gestor.
                if (episode.id in
                    state.value.downloadedEpisodeIds
                ) {
                    deleteDownload(episode)
                } else {
                    downloadEpisode(episode)
                }
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> Unit
        }
    }

    // ---- Portada a pantalla completa -----------------------------------------------------

    val coverSnackbarHostState = SnackbarHostState()

    fun showCover() = _state.update { it.copy(coverDialog = true) }

    fun dismissCover() = _state.update { it.copy(coverDialog = false) }

    fun saveCover(context: Context) {
        viewModelScope.launch {
            try {
                writeCover(context, temp = false)
                coverSnackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.cover_saved),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                coverSnackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_saving_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareCover(context: Context) {
        viewModelScope.launch {
            try {
                val uri = writeCover(context, temp = true) ?: return@launch
                withUIContext { context.startActivity(uri.toShareIntent(context)) }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                coverSnackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.error_sharing_cover),
                    withDismissAction = true,
                )
            }
        }
    }

    /** Decodifica la portada a su tamaño original y la deja en Imágenes, o en caché si es para compartir. */
    private suspend fun writeCover(context: Context, temp: Boolean): Uri? {
        val anime = state.value.anime ?: return null
        val request = ImageRequest.Builder(context)
            .data(anime.asAnimeCover())
            .size(Size.ORIGINAL)
            .build()
        return withIOContext {
            val bitmap = context.imageLoader.execute(request).image
                ?.asDrawable(context.resources)
                ?.getBitmapOrNull()
                ?: return@withIOContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    name = anime.title,
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }
    }

    // ---- Intervalo de actualizacion -----------------------------------------------------

    fun showSetIntervalDialog() = _state.update { it.copy(setIntervalDialog = true) }

    fun dismissSetIntervalDialog() = _state.update { it.copy(setIntervalDialog = false) }

    /**
     * El valor que elige el usuario se guarda **en negativo**, que es como el lado de manga
     * distingue "lo he puesto yo" de "lo he calculado": asi la siguiente pasada del job no lo
     * pisa con su propia estimacion.
     */
    fun setFetchInterval(interval: Int) {
        val anime = state.value.anime ?: return
        dismissSetIntervalDialog()
        viewModelScope.launchIO {
            updateAnime.await(AnimeUpdate(id = anime.id, fetchInterval = -interval.coerceAtLeast(0)))
            state.value.anime?.let { updateAnime.awaitUpdateFetchInterval(it) }
        }
    }

    // ---- Seleccion de episodios ---------------------------------------------------------
    //
    // El mismo modelo que Mihon usa con los capitulos, rango incluido: mantener pulsado sobre
    // un episodio alejado del ultimo selecciona todo lo que hay entre medias, que es de donde
    // sale el "marcar de aqui para abajo" sin tener que ir uno a uno. Las posiciones viven
    // fuera del estado a proposito; son un detalle de como se calcula, no algo que se dibuje.

    private val selectedPositions = arrayOf(-1, -1)

    fun toggleSelection(episode: Episode, selected: Boolean, fromLongPress: Boolean = false) {
        _state.update { state ->
            val visible = state.visibleEpisodes
            val index = visible.indexOfFirst { it.id == episode.id }
            if (index < 0) return@update state

            val already = episode.id in state.selectedEpisodeIds
            if (already == selected) return@update state

            val ids = state.selectedEpisodeIds.toMutableSet()
            val firstSelection = ids.isEmpty()
            if (selected) ids.add(episode.id) else ids.remove(episode.id)

            if (selected && fromLongPress) {
                if (firstSelection) {
                    selectedPositions[0] = index
                    selectedPositions[1] = index
                } else {
                    val range = when {
                        index < selectedPositions[0] -> (index + 1)..<selectedPositions[0]
                        index > selectedPositions[1] -> (selectedPositions[1] + 1)..<index
                        else -> IntRange.EMPTY
                    }
                    if (index < selectedPositions[0]) selectedPositions[0] = index
                    if (index > selectedPositions[1]) selectedPositions[1] = index
                    range.forEach { ids.add(visible[it].id) }
                }
            } else if (!fromLongPress) {
                if (selected) {
                    if (index < selectedPositions[0]) selectedPositions[0] = index
                    if (index > selectedPositions[1]) selectedPositions[1] = index
                } else {
                    if (index == selectedPositions[0]) {
                        selectedPositions[0] = visible.indexOfFirst { it.id in ids }
                    }
                    if (index == selectedPositions[1]) {
                        selectedPositions[1] = visible.indexOfLast { it.id in ids }
                    }
                }
            }

            state.copy(selectedEpisodeIds = ids)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        selectedPositions[0] = -1
        selectedPositions[1] = -1
        _state.update { state ->
            state.copy(
                selectedEpisodeIds = if (selected) state.visibleEpisodes.map { it.id }.toSet() else emptySet(),
            )
        }
    }

    fun invertSelection() {
        selectedPositions[0] = -1
        selectedPositions[1] = -1
        _state.update { state ->
            state.copy(
                selectedEpisodeIds = state.visibleEpisodes
                    .filterNot { it.id in state.selectedEpisodeIds }
                    .map { it.id }
                    .toSet(),
            )
        }
    }

    fun clearSelection() {
        selectedPositions[0] = -1
        selectedPositions[1] = -1
        _state.update { it.copy(selectedEpisodeIds = emptySet()) }
    }

    // ---- Acciones en lote ---------------------------------------------------------------

    /**
     * Marca visto o no visto de una vez.
     *
     * Al desmarcar se pone el segundo a cero: un episodio "no visto" que conserva la posicion
     * volveria a abrirse por la mitad, que no es lo que nadie espera de desmarcarlo. Y solo se
     * avisa a los trackers al marcar visto, porque solo avanzan.
     */
    fun markEpisodesSeen(episodes: List<Episode>, seen: Boolean) {
        if (episodes.isEmpty()) return
        clearSelection()
        viewModelScope.launchIO {
            updateEpisode.awaitAll(
                episodes.map {
                    EpisodeUpdate(id = it.id, seen = seen, lastSecondSeen = if (seen) it.lastSecondSeen else 0L)
                },
            )
            if (!seen) return@launchIO
            episodes.maxByOrNull { it.episodeNumber }?.let { trackEpisode.await(animeId, it.episodeNumber) }
        }
    }

    /**
     * Marca vistos todos los episodios anteriores al señalado, en el orden en que se ven, no en
     * el que estan puestos en pantalla: con la lista invertida "anterior" sigue queriendo decir
     * el episodio de antes, no el de arriba.
     */
    fun markPreviousAsSeen(pointer: Episode) {
        val episodes = state.value.episodes
        val previous = episodes.filter { it.episodeNumber < pointer.episodeNumber && !it.seen }
        markEpisodesSeen(previous, seen = true)
    }

    fun bookmarkEpisodes(episodes: List<Episode>, bookmarked: Boolean) {
        if (episodes.isEmpty()) return
        clearSelection()
        viewModelScope.launchIO {
            updateEpisode.awaitAll(episodes.map { EpisodeUpdate(id = it.id, bookmark = bookmarked) })
        }
    }

    /** Encola varios. Nombre distinto de [downloadEpisodes] a proposito: esa toma una accion
     *  de la barra superior, y dos sobrecargas harian ambigua la referencia `::downloadEpisodes`. */
    fun enqueueDownloads(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        clearSelection()
        episodes.forEach { downloadEpisode(it) }
    }

    fun deleteEpisodeDownloads(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        clearSelection()
        episodes.forEach { deleteDownload(it) }
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
        /** Los episodios marcados ahora mismo. Vacio = no hay modo seleccion. */
        val selectedEpisodeIds: Set<Long> = emptySet(),
        /** Tirando hacia abajo para volver a pedirle los episodios a la fuente. */
        val isRefreshingData: Boolean = false,
        val setIntervalDialog: Boolean = false,
        val coverDialog: Boolean = false,
        /** Asking the source what qualities it has, before anything is queued. */
        val preparingEpisodeIds: Set<Long> = emptySet(),
        val qualityDialog: StartAnimeDownload.Outcome.Choose? = null,
    ) {
        val selectedEpisodes: List<Episode> get() = visibleEpisodes.filter { it.id in selectedEpisodeIds }
    }

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
