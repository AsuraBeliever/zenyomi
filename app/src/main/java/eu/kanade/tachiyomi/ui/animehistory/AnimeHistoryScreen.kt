package eu.kanade.tachiyomi.ui.animehistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.anime.components.AnimeHistoryItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.animeplayer.AnimePlayerActivity
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DeleteSweep
import mihon.icons.materialsymbols.rounded.SwapCalls
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Recently watched anime.
 *
 * Like [eu.kanade.tachiyomi.ui.animeupdates.AnimeUpdatesScreen], reachable both as a pushed
 * screen and as the anime half of the bottom bar's History tab; [onSwitchToManga] is what
 * tells the two apart.
 */
class AnimeHistoryScreen(private val onSwitchToManga: (() -> Unit)? = null) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val viewModel = metroViewModel<AnimeHistoryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                // SearchToolbar, not a plain AppBar: the manga history can be searched and
                // this one could not, which is a missing control rather than a style choice.
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(ANMR.strings.label_anime_history)) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = viewModel::search,
                    navigateUp = if (onSwitchToManga == null) ({ navigator.pop() }) else null,
                    actions = {
                        onSwitchToManga?.let { switch ->
                            IconButton(onClick = switch) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.SwapCalls,
                                    contentDescription = stringResource(MR.strings.history),
                                )
                            }
                        }
                        IconButton(onClick = viewModel::removeAll) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.DeleteSweep,
                                contentDescription = stringResource(MR.strings.action_delete),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                // "Nothing watched recently", not Mihon's "Nothing read recently". The
                // string was already there and this screen was borrowing the wrong one.
                state.isEmpty -> EmptyScreen(
                    stringRes = ANMR.strings.information_no_recent_anime,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> FastScrollLazyColumn(contentPadding = contentPadding) {
                    items(
                        items = state.history,
                        key = { item ->
                            when (item) {
                                is AnimeHistoryUiModel.Header -> "header-${item.date}"
                                is AnimeHistoryUiModel.Item -> "item-${item.item.id}"
                            }
                        },
                        contentType = { item ->
                            when (item) {
                                is AnimeHistoryUiModel.Header -> "header"
                                is AnimeHistoryUiModel.Item -> "item"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is AnimeHistoryUiModel.Header -> ListGroupHeader(
                                modifier = Modifier.animateItem(),
                                text = relativeDateText(item.date),
                            )
                            is AnimeHistoryUiModel.Item -> {
                                val entry = item.item
                                AnimeHistoryItem(
                                    modifier = Modifier.animateItem(),
                                    history = entry,
                                    onClickCover = { navigator.push(AnimeDetailsScreen(entry.animeId)) },
                                    onClickResume = {
                                        viewModel.resume(entry) { request, episode ->
                                            if (request != null && episode != null) {
                                                context.startActivity(
                                                    AnimePlayerActivity.newIntent(
                                                        context,
                                                        request,
                                                        episode.name,
                                                        episode.id,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    onClickDelete = { viewModel.remove(entry) },
                                    onClickFavorite = { viewModel.addToLibrary(entry.animeId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
