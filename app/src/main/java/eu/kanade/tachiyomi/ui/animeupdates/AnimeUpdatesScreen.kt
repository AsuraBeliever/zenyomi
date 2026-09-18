package eu.kanade.tachiyomi.ui.animeupdates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.anime.animeSourceErrorText
import eu.kanade.presentation.anime.components.AnimeUpdatesItem
import eu.kanade.presentation.anime.components.DownloadQualityDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.MangaBottomActionMenu
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.anime.describe
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.FlipToBack
import mihon.icons.materialsymbols.rounded.SelectAll
import mihon.icons.materialsymbols.rounded.SwapCalls
import mihon.icons.materialsymbols.roundedfilled.CheckCircle
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Episodes the anime library has gained, grouped by the day they arrived.
 *
 * Reachable two ways: pushed from the anime library, and hosted inside the bottom bar's
 * Updates tab, which swaps between the two halves. [onSwitchToManga] is what tells them apart
 * — when it is set the bar offers the swap instead of a back arrow, because inside a tab there
 * is nothing to go back to.
 */
class AnimeUpdatesScreen(private val onSwitchToManga: (() -> Unit)? = null) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<AnimeUpdatesViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
        val downloadQueue by viewModel.downloadQueue.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        // A tap that resolves to nothing is indistinguishable from a tap that missed, so the
        // failure is said out loud — in words, not as the exception the extension threw.
        val playbackErrorText = state.playbackError?.let { animeSourceErrorText(it) }
        LaunchedEffect(playbackErrorText) {
            playbackErrorText?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearPlaybackError()
            }
        }

        state.qualityDialog?.let { (_, choice) ->
            DownloadQualityDialog(
                qualities = choice.qualities,
                reason = choice.reason,
                onConfirm = viewModel::confirmQuality,
                onDismissRequest = viewModel::dismissQualityDialog,
            )
        }

        BackHandler(enabled = state.selected.isNotEmpty()) {
            viewModel.clearSelection()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(ANMR.strings.label_anime_updates),
                    navigateUp = if (onSwitchToManga == null) ({ navigator.pop() }) else null,
                    // The counting bar Mihon shows while a selection is open, with the same
                    // select-all and invert actions.
                    actionModeCounter = state.selected.size,
                    onCancelActionMode = viewModel::clearSelection,
                    actions = {
                        if (state.selected.isNotEmpty()) {
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.SelectAll,
                                    contentDescription = stringResource(MR.strings.action_select_all),
                                )
                            }
                            IconButton(onClick = viewModel::invertSelection) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.FlipToBack,
                                    contentDescription = stringResource(MR.strings.action_select_inverse),
                                )
                            }
                            return@AppBar
                        }
                        onSwitchToManga?.let { switch ->
                            IconButton(onClick = switch) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.SwapCalls,
                                    contentDescription = stringResource(MR.strings.label_recent_updates),
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Mihon's own menu: it takes nothing but lambdas, so the anime updates get the
                // identical bar rather than a second one that looks almost like it.
                MangaBottomActionMenu(
                    visible = state.selected.isNotEmpty(),
                    onMarkAsReadClicked = { viewModel.markSelected(seen = true) },
                    onMarkAsUnreadClicked = { viewModel.markSelected(seen = false) },
                    onDownloadClicked = { viewModel.downloadSelected() },
                    onDeleteClicked = { viewModel.deleteSelected() },
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isEmpty -> EmptyScreen(
                    stringRes = ANMR.strings.anime_updates_empty,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> FastScrollLazyColumn(contentPadding = contentPadding) {
                    items(
                        count = state.items.size,
                        key = { index ->
                            when (val item = state.items[index]) {
                                is AnimeUpdatesUiItem.Header -> "header-${item.dateFetch}"
                                is AnimeUpdatesUiItem.Item -> "episode-${item.update.episodeId}"
                            }
                        },
                    ) { index ->
                        when (val item = state.items[index]) {
                            // ListGroupHeader, like the manga updates: the old header was a
                            // primary-coloured label, which is not how any other date header
                            // in the app looks.
                            is AnimeUpdatesUiItem.Header -> ListGroupHeader(
                                modifier = Modifier.animateItem(),
                                text = relativeDateText(item.dateFetch),
                            )
                            is AnimeUpdatesUiItem.Item -> {
                                val update = item.update
                                val downloaded = update.episodeId in state.downloadedEpisodeIds
                                val progress = downloadProgress[update.episodeId]
                                val queued = downloadQueue.any { it.episodeId == update.episodeId }
                                // Asked for but not yet in the queue counts as queued for the
                                // row's purposes: the spinner starts on the tap, not once the
                                // source has finished being asked what it has.
                                val preparing = update.episodeId in state.preparingEpisodeIds
                                val downloadState = when {
                                    downloaded -> Download.State.DOWNLOADED
                                    progress != null -> Download.State.DOWNLOADING
                                    queued || preparing -> Download.State.QUEUE
                                    else -> Download.State.NOT_DOWNLOADED
                                }
                                AnimeUpdatesItem(
                                    modifier = Modifier.animateItem(),
                                    update = update,
                                    // Mientras baja, cuánto lleva y a qué velocidad. Es el
                                    // único hueco de texto de la fila y estaba sin usar.
                                    seenProgress = progress?.describe(context),
                                    selected = update.episodeId in state.selected,
                                    onLongClick = { viewModel.toggleSelection(update) },
                                    onClick = if (state.selected.isNotEmpty()) {
                                        { viewModel.toggleSelection(update) }
                                    } else {
                                        {
                                            viewModel.resolveVideo(update) { request ->
                                                if (request != null) {
                                                    context.startActivity(
                                                        AnimePlayerActivity.newIntent(
                                                            context,
                                                            request,
                                                            update.episodeName,
                                                            update.episodeId,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClickCover = {
                                        navigator.push(AnimeDetailsScreen(update.animeId))
                                    }.takeIf { state.selected.isEmpty() },
                                    onDownloadEpisode = if (update.episodeId in state.downloadableEpisodeIds) {
                                        { action ->
                                            when (action) {
                                                ChapterDownloadAction.START ->
                                                    viewModel.downloadEpisode(update)
                                                // Holding the button on an episode you do not
                                                // have asks which quality, for this download
                                                // only. On one already queued the same action
                                                // means "start now", which a queue that runs in
                                                // order start to finish cannot honour.
                                                ChapterDownloadAction.START_NOW ->
                                                    if (downloadState == Download.State.NOT_DOWNLOADED) {
                                                        viewModel.downloadEpisode(update, ask = true)
                                                    }
                                                // Calling off a download that is happening and
                                                // deleting a file that is there are not the
                                                // same thing.
                                                ChapterDownloadAction.CANCEL ->
                                                    viewModel.cancelDownload(update)
                                                ChapterDownloadAction.DELETE ->
                                                    viewModel.deleteDownload(update)
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    downloadStateProvider = { downloadState },
                                    downloadProgressProvider = { progress?.percent ?: 0 },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
