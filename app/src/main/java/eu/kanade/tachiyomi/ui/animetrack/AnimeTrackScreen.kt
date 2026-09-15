package eu.kanade.tachiyomi.ui.animetrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.anime.components.AnimeTrackInfoDialogHome
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.track.TrackChapterSelector
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import mihon.app.di.appGraph
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Tracking for one anime: what it is bound to, and a search to bind it to something new.
 */
class AnimeTrackScreen(private val animeId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<AnimeTrackViewModel, AnimeTrackViewModel.Factory> {
            create(animeId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()

        var searchingWith by remember { mutableStateOf<Tracker?>(null) }
        var editing by remember { mutableStateOf<Editing?>(null) }
        val context = LocalContext.current
        val dateFormat = remember { UiPreferences.dateFormat(context.appGraph.uiPreferences.dateFormat.get()) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.manga_tracking_tab),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.loading -> LoadingScreen(Modifier.padding(contentPadding))
                state.loggedIn.isEmpty() -> EmptyScreen(
                    stringRes = ANMR.strings.anime_track_no_account,
                    modifier = Modifier.padding(contentPadding),
                )
                // Mihon's own rows, fed by the anime adapter: status, progress, score,
                // dates and the overflow, instead of the name-and-bin list this used to be.
                else -> Box(modifier = Modifier.padding(contentPadding)) {
                    AnimeTrackInfoDialogHome(
                        trackItems = state.items,
                        dateFormat = dateFormat,
                        onStatusClick = { editing = Editing.Status(it) },
                        onEpisodeClick = { editing = Editing.Episodes(it) },
                        onScoreClick = { editing = Editing.Score(it) },
                        onStartDateEdit = { editing = Editing.StartDate(it) },
                        onEndDateEdit = { editing = Editing.FinishDate(it) },
                        onNewSearch = { searchingWith = it.tracker as Tracker },
                        onOpenInBrowser = { item ->
                            item.track?.remoteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                navigator.push(WebViewScreen(url = url, initialTitle = item.track.title))
                            }
                        },
                        onRemoved = { item -> item.track?.let(viewModel::unbind) },
                        onCopyLink = { item ->
                            item.track?.remoteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                context.copyToClipboard(url, url)
                            }
                        },
                        onTogglePrivate = viewModel::togglePrivate,
                    )
                }
            }
        }

        // The same four selectors Mihon opens from those rows, reused outright: they take
        // a selection, a range and two callbacks, and nothing about them is manga-shaped.
        //
        // Wrapped in a Dialog, because what they return is dialog *content*. Mihon pushes each
        // one as its own screen on the navigator inside its tracking dialog; this screen is not
        // one, so drawn bare they had no surface behind them — the picker rendered straight on
        // top of the card above it with both sets of text overlapping and neither readable.
        if (editing != null) {
            Dialog(onDismissRequest = { editing = null }) {
                // And on a surface, which is the other half of what the dialog host gives
                // Mihon's pushed screens: without it the wheel and its title drew straight
                // over the card behind, both legible and neither readable.
                Surface(
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    when (val current = editing) {
                        null -> {}
                        is Editing.Status -> {
                            val tracker = current.item.tracker as Tracker
                            var selection by remember { mutableLongStateOf(current.item.track?.status ?: 0L) }
                            TrackStatusSelector(
                                selection = selection,
                                onSelectionChange = { selection = it },
                                selections = remember(tracker) {
                                    val anime = current.item.tracker as AnimeTracker
                                    anime.getStatusListAnime().associateWith(anime::getStatusForAnime)
                                },
                                onConfirm = {
                                    viewModel.setStatus(current.item, selection)
                                    editing = null
                                },
                                onDismissRequest = { editing = null },
                            )
                        }
                        is Editing.Episodes -> {
                            val track = current.item.track
                            var selection by remember { mutableIntStateOf(track?.lastEpisodeSeen?.toInt() ?: 0) }
                            TrackChapterSelector(
                                title = stringResource(ANMR.strings.episodes),
                                selection = selection,
                                onSelectionChange = { selection = it },
                                range = remember(track) {
                                    val total = track?.totalEpisodes?.toInt() ?: 0
                                    0..(if (total > 0) total else 10_000)
                                },
                                onConfirm = {
                                    viewModel.setEpisodesSeen(current.item, selection)
                                    editing = null
                                },
                                onDismissRequest = { editing = null },
                            )
                        }
                        is Editing.Score -> {
                            val tracker = current.item.tracker as Tracker
                            val scores = remember(tracker) { tracker.getScoreList() }
                            var selection by remember {
                                mutableStateOf(current.item.displayScore.ifBlank { scores.first() })
                            }
                            TrackScoreSelector(
                                selection = selection,
                                onSelectionChange = { selection = it },
                                selections = scores,
                                onConfirm = {
                                    viewModel.setScore(current.item, selection)
                                    editing = null
                                },
                                onDismissRequest = { editing = null },
                            )
                        }
                        is Editing.StartDate -> TrackDateSelector(
                            title = stringResource(MR.strings.track_started_reading_date),
                            initialSelectedDateMillis = current.item.track?.startDate
                                ?.takeIf { it != 0L } ?: System.currentTimeMillis(),
                            selectableDates = remember { AllDates },
                            onConfirm = {
                                viewModel.setStartDate(current.item, it)
                                editing = null
                            },
                            onRemove = {
                                viewModel.setStartDate(current.item, 0L)
                                editing = null
                            }.takeIf { (current.item.track?.startDate ?: 0L) != 0L },
                            onDismissRequest = { editing = null },
                        )
                        is Editing.FinishDate -> TrackDateSelector(
                            title = stringResource(MR.strings.track_finished_reading_date),
                            initialSelectedDateMillis = current.item.track?.finishDate
                                ?.takeIf { it != 0L } ?: System.currentTimeMillis(),
                            selectableDates = remember { AllDates },
                            onConfirm = {
                                viewModel.setFinishDate(current.item, it)
                                editing = null
                            },
                            onRemove = {
                                viewModel.setFinishDate(current.item, 0L)
                                editing = null
                            }.takeIf { (current.item.track?.finishDate ?: 0L) != 0L },
                            onDismissRequest = { editing = null },
                        )
                    }
                }
            }
        }

        searchingWith?.let { tracker ->
            TrackSearchDialog(
                tracker = tracker,
                state = state,
                onSearch = { query -> viewModel.search(tracker, query) },
                onPick = { selection ->
                    viewModel.bind(tracker, selection)
                    searchingWith = null
                },
                onDismiss = {
                    viewModel.clearSearch()
                    searchingWith = null
                },
                initialQuery = { viewModel.animeTitle() },
            )
        }
    }
}

@Composable
private fun TrackSearchDialog(
    tracker: Tracker,
    state: AnimeTrackViewModel.State,
    onSearch: (String) -> Unit,
    onPick: (eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: suspend () -> String?,
) {
    var query by remember { mutableStateOf("") }

    // The anime's own title is almost always the right search, so it is filled in rather than
    // leaving the user to retype what is already on screen.
    androidx.compose.runtime.LaunchedEffect(tracker.id) {
        val title = initialQuery()
        if (title != null) {
            query = title
            onSearch(title)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tracker.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(MR.strings.action_search)) },
                )
                when {
                    state.searching -> LoadingScreen()
                    state.searchError -> Text(stringResource(ANMR.strings.anime_track_search_failed))
                    else -> LazyColumn {
                        items(state.searchResults, key = { it.remote_id }) { result ->
                            ListItem(
                                headlineContent = { Text(result.title) },
                                supportingContent = {
                                    if (result.total_episodes > 0) {
                                        Text(
                                            stringResource(
                                                ANMR.strings.anime_track_episode_count,
                                                result.total_episodes.toInt(),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { onPick(result) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSearch(query) }) {
                Text(stringResource(MR.strings.action_search))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/** Which selector the sheet has open, if any. */
private sealed interface Editing {
    val item: AnimeTrackItem

    data class Status(override val item: AnimeTrackItem) : Editing
    data class Episodes(override val item: AnimeTrackItem) : Editing
    data class Score(override val item: AnimeTrackItem) : Editing
    data class StartDate(override val item: AnimeTrackItem) : Editing
    data class FinishDate(override val item: AnimeTrackItem) : Editing
}

/** Every date is allowed, as on the manga side. */
private object AllDates : SelectableDates
