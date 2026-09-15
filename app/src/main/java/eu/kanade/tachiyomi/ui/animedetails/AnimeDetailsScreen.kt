package eu.kanade.tachiyomi.ui.animedetails

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.anime.animeSourceErrorText
import eu.kanade.presentation.anime.components.AnimeInfoBox
import eu.kanade.presentation.anime.components.AnimeToolbar
import eu.kanade.presentation.anime.components.EpisodeHeader
import eu.kanade.presentation.anime.components.EpisodeSettingsDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.animebrowse.globalsearch.AnimeGlobalSearchScreen
import eu.kanade.tachiyomi.ui.animecategory.AnimeCategoryScreen
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import eu.kanade.tachiyomi.ui.animeplayer.PlaybackRequest
import eu.kanade.tachiyomi.ui.animetrack.AnimeTrackScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.shouldExpandFAB
import kotlin.time.Instant

/**
 * One anime entry: cover, description and the list of episodes.
 *
 * Deliberately the same screen as Mihon's [eu.kanade.presentation.manga.MangaScreen], piece for
 * piece and in the same order: a blurred backdrop behind the cover and titles, the row of
 * actions, the description that expands with its genre chips, then the episodes. The charter
 * makes Mihon the reference for how a thing is done, and an app where opening a manga and
 * opening an anime feel like two different apps has failed that regardless of what each screen
 * does on its own.
 *
 * Everything Mihon exposes on primitives is reused rather than copied — the action row, the
 * description, the list rows, the download indicator — so the two cannot drift apart. What is
 * copied is only what was bound to the Manga type.
 */
class AnimeDetailsScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = assistedMetroViewModel<AnimeDetailsViewModel, AnimeDetailsViewModel.Factory> {
            create(animeId = animeId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
        val anime = state.anime

        val snackbarHostState = remember { SnackbarHostState() }
        val episodeListState = rememberLazyListState()
        var showTrackSheet by remember { mutableStateOf(false) }

        state.categoryDialog?.let { dialog ->
            AnimeCategoryDialog(
                categories = dialog.categories,
                initiallySelected = dialog.selected,
                onDismiss = viewModel::dismissCategoryDialog,
                onConfirm = viewModel::setCategories,
                onEditCategories = {
                    viewModel.dismissCategoryDialog()
                    navigator.push(AnimeCategoryScreen())
                },
            )
        }

        // The same sheet the manga screen opens for tracking, and the same component, so the
        // Tracking button behaves identically whichever of the two you pressed it on.
        if (showTrackSheet && anime != null) {
            NavigatorAdaptiveSheet(
                screen = AnimeTrackScreen(anime.id),
                enableSwipeDismiss = { it.lastItem is AnimeTrackScreen },
                onDismissRequest = { showTrackSheet = false },
            )
        }

        if (state.episodeSettingsDialog) {
            EpisodeSettingsDialog(
                onDismissRequest = viewModel::dismissEpisodeSettings,
                anime = anime,
                onDownloadFilterChanged = viewModel::setDownloadedFilter,
                onUnseenFilterChanged = viewModel::setUnseenFilter,
                onBookmarkedFilterChanged = viewModel::setBookmarkedFilter,
                onSortModeChanged = viewModel::setSorting,
            )
        }

        // Resolved outside the effect: turning a failure into words needs the string
        // resources, which only a composable can reach.
        val playbackErrorText = state.playbackError?.let { animeSourceErrorText(it) }
        LaunchedEffect(playbackErrorText) {
            playbackErrorText?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearPlaybackError()
            }
        }

        // A tap that resolves to nothing is already reported through playbackError, so this
        // only has to know what to do when there is something to play.
        fun openPlayer(request: PlaybackRequest?, title: String, id: Long) {
            if (request != null) {
                context.startActivity(AnimePlayerActivity.newIntent(context, request, title, id))
            }
        }

        // A tap that opens an episode reaches the source over the network, which can take the
        // better part of ten seconds. Until now that was a spinner on one row and an app that
        // still accepted taps everywhere else, so it read as "nothing happened" and invited a
        // second tap on something else.
        if (state.resolvingEpisodeId != null) {
            EpisodeLoadingOverlay(onCancel = viewModel::cancelResolve)
        }

        Scaffold(
            topBar = {
                // The bar is transparent over the cover and fades in as the list arrives under
                // it, which is what makes the backdrop read as part of the entry rather than as
                // a picture behind a toolbar.
                val isFirstItemVisible by remember {
                    derivedStateOf { episodeListState.firstVisibleItemIndex == 0 }
                }
                val isFirstItemScrolled by remember {
                    derivedStateOf { episodeListState.firstVisibleItemScrollOffset > 0 }
                }
                val titleAlpha by animateFloatAsState(
                    if (!isFirstItemVisible) 1f else 0f,
                    label = "Top Bar Title",
                )
                val backgroundAlpha by animateFloatAsState(
                    if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                    label = "Top Bar Background",
                )
                AnimeToolbar(
                    title = anime?.title.orEmpty(),
                    hasFilters = state.filterActive,
                    navigateUp = navigator::pop,
                    onClickFilter = viewModel::showEpisodeSettings,
                    onClickDownload = viewModel::downloadEpisodes.takeIf { state.canDownload },
                    onClickRefresh = viewModel::refreshDownloaded,
                    onClickEditCategory = viewModel::showCategoryDialog
                        .takeIf { anime?.favorite == true },
                    onClickMigrate = {
                        navigator.push(
                            AnimeGlobalSearchScreen(
                                initialQuery = anime?.title.orEmpty(),
                                migrateFromId = anime?.id,
                            ),
                        )
                    }.takeIf { anime?.favorite == true },
                    onClickShare = null,
                    titleAlphaProvider = { titleAlpha },
                    backgroundAlphaProvider = { backgroundAlpha },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                val episodes = state.visibleEpisodes
                val isWatching = remember(episodes) { episodes.any { it.seen } }
                if (anime != null && episodes.any { !it.seen }) {
                    SmallExtendedFloatingActionButton(
                        text = {
                            Text(
                                stringResource(
                                    if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                                ),
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = MaterialSymbols.RoundedFilled.PlayArrow,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            viewModel.continueWatching { request, episode ->
                                if (episode != null) openPlayer(request, episode.name, episode.id)
                            }
                        },
                        expanded = episodeListState.shouldExpandFAB(),
                    )
                }
            },
        ) { contentPadding ->
            if (state.isLoading || anime == null) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }

            val topPadding = contentPadding.calculateTopPadding()
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                state = episodeListState,
                // The info box draws behind the bar, so the top padding belongs to it rather
                // than to the list.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            ) {
                item(key = "info-box", contentType = "info-box") {
                    AnimeInfoBox(
                        appBarPadding = topPadding,
                        anime = anime,
                        sourceName = state.sourceName,
                        isStubSource = state.isStubSource,
                        onCoverClick = {},
                        doSearch = { query, _ ->
                            navigator.push(AnimeGlobalSearchScreen(initialQuery = query))
                        },
                    )
                }

                item(key = "action-row", contentType = "action-row") {
                    MangaActionRow(
                        favorite = anime.favorite,
                        trackingCount = state.trackingCount,
                        nextUpdate = anime.expectedNextUpdate
                            ?.let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
                        isUserIntervalMode = anime.fetchInterval < 0,
                        onAddToLibraryClicked = viewModel::toggleFavorite,
                        onWebViewClicked = state.webViewUrl?.let { url ->
                            {
                                navigator.push(
                                    WebViewScreen(url = url, initialTitle = anime.title, sourceId = anime.source),
                                )
                            }
                        },
                        onWebViewLongClicked = null,
                        onTrackingClicked = { showTrackSheet = true },
                        // Anime has no per-entry update interval of its own yet, so the
                        // countdown is shown but not editable.
                        onEditIntervalClicked = null,
                        onEditCategory = viewModel::showCategoryDialog.takeIf { anime.favorite },
                    )
                }

                item(key = "description", contentType = "description") {
                    ExpandableMangaDescription(
                        defaultExpandState = !anime.favorite,
                        description = anime.description,
                        tagsProvider = { anime.genre },
                        notes = "",
                        onTagSearch = { tag ->
                            navigator.push(AnimeGlobalSearchScreen(initialQuery = tag))
                        },
                        onCopyTagToClipboard = {},
                        onEditNotes = {},
                    )
                }

                item(key = "episode-header", contentType = "episode-header") {
                    EpisodeHeader(
                        enabled = true,
                        episodeCount = state.visibleEpisodes.size,
                        onClick = viewModel::showEpisodeSettings,
                    )
                    // An empty list and a source that refused to answer look the same
                    // otherwise, and only one of them is worth retrying.
                    state.episodeError?.let { error ->
                        Text(
                            text = animeSourceErrorText(error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                items(
                    items = state.visibleEpisodes,
                    key = { "episode-${it.id}" },
                    contentType = { "episode" },
                ) { episode ->
                    val downloaded = episode.id in state.downloadedEpisodeIds
                    val progress = downloadProgress[episode.id]
                    val queued = downloadQueue.any { it.episodeId == episode.id }
                    val downloadState = when {
                        downloaded -> Download.State.DOWNLOADED
                        progress != null -> Download.State.DOWNLOADING
                        queued -> Download.State.QUEUE
                        else -> Download.State.NOT_DOWNLOADED
                    }
                    MangaChapterListItem(
                        title = episode.name,
                        date = relativeDateText(episode.dateUpload),
                        readProgress = null,
                        scanlator = episode.scanlator?.takeIf { it.isNotBlank() },
                        read = episode.seen,
                        bookmark = episode.bookmark,
                        selected = false,
                        downloadIndicatorEnabled = state.canDownload,
                        downloadStateProvider = { downloadState },
                        downloadProgressProvider = { progress ?: 0 },
                        // Swiping a row is a manga-side shortcut for marking read and
                        // bookmarking, neither of which the anime list offers yet.
                        chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.Disabled,
                        chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.Disabled,
                        onLongClick = {},
                        onClick = {
                            viewModel.resolveVideo(episode) { request ->
                                openPlayer(request, episode.name, episode.id)
                            }
                        },
                        onDownloadClick = { action ->
                            when (action) {
                                ChapterDownloadAction.START,
                                ChapterDownloadAction.START_NOW,
                                -> viewModel.downloadEpisode(episode)
                                ChapterDownloadAction.CANCEL,
                                ChapterDownloadAction.DELETE,
                                -> viewModel.deleteDownload(episode)
                            }
                        },
                        onChapterSwipe = {},
                    )
                }
            }
        }
    }
}

/**
 * Which categories an anime belongs to.
 *
 * Multi-select, because an anime can sit in several — that is the whole point of categories
 * over a single folder. The way to a library with no categories yet is through the same
 * dialog: offering nothing but Cancel would be a dead end.
 */
@Composable
private fun AnimeCategoryDialog(
    categories: List<AnimeCategory>,
    initiallySelected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    onEditCategories: () -> Unit,
) {
    val selected = remember { mutableStateListOf<Long>().apply { addAll(initiallySelected) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.categories)) },
        text = {
            if (categories.isEmpty()) {
                Text(stringResource(MR.strings.information_empty_category))
            } else {
                LazyColumn {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (category.id in selected) {
                                        selected.remove(category.id)
                                    } else {
                                        selected.add(category.id)
                                    }
                                },
                        ) {
                            Checkbox(
                                checked = category.id in selected,
                                onCheckedChange = null,
                            )
                            Text(
                                text = category.name,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (categories.isEmpty()) {
                TextButton(onClick = onEditCategories) {
                    Text(stringResource(MR.strings.action_edit_categories))
                }
            } else {
                TextButton(onClick = { onConfirm(selected.toList()) }) {
                    Text(stringResource(MR.strings.action_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/**
 * Covers the screen while an episode is being opened.
 *
 * It swallows taps on purpose: the point is that nothing else responds until the source has
 * answered. Back and the close button both call it off, so a mistaken tap costs a second rather
 * than the app.
 */
@Composable
private fun EpisodeLoadingOverlay(onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .background(Color.Black.copy(alpha = 0.6f))
            // No ripple and no onClick: this exists to absorb taps, not to act on them.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = stringResource(ANMR.strings.player_loading),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Close,
                    contentDescription = null,
                    tint = Color.White,
                )
                Text(
                    text = stringResource(ANMR.strings.player_cancel_loading),
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
