package eu.kanade.tachiyomi.ui.animetrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
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
                else -> LazyColumn(contentPadding = contentPadding) {
                    items(state.loggedIn, key = { it.id }) { tracker ->
                        val bound = state.tracks.firstOrNull { it.trackerId == tracker.id }
                        ListItem(
                            headlineContent = { Text(tracker.name) },
                            supportingContent = {
                                if (bound != null) {
                                    Text(
                                        "${bound.title} — ${bound.lastEpisodeSeen.toInt()}" +
                                            if (bound.totalEpisodes > 0) "/${bound.totalEpisodes}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    Text(
                                        stringResource(ANMR.strings.anime_track_not_bound),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            trailingContent = {
                                if (bound != null) {
                                    IconButton(onClick = { viewModel.unbind(bound) }) {
                                        Icon(
                                            imageVector = MaterialSymbols.Rounded.Delete,
                                            contentDescription = stringResource(MR.strings.action_remove),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { searchingWith = tracker },
                        )
                        HorizontalDivider()
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
