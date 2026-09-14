package eu.kanade.tachiyomi.ui.animebrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.anime.animeSourceErrorText
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import tachiyomi.data.source.anime.NoAnimeResultsException
import tachiyomi.domain.anime.model.asAnimeCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * One anime source's catalogue.
 *
 * Shows the source's popular listing; tapping an entry opens it. Search and filters exist in
 * the interactor but have no UI yet.
 */
class AnimeCatalogScreen(private val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<AnimeCatalogViewModel, AnimeCatalogViewModel.Factory> {
            create(sourceId = sourceId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val animeList = viewModel.animeList.collectAsLazyPagingItems()

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = { AppBarTitle(state.sourceName) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = viewModel::search,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val refresh = animeList.loadState.refresh) {
                is LoadState.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                // A search that found nothing is not an error: no red text, no retry, no
                // suggestion to try another source. Mihon's browse draws the same empty
                // screen for it, and this is the only LoadState.Error that is not a failure.
                is LoadState.Error -> if (refresh.error is NoAnimeResultsException) {
                    EmptyScreen(
                        stringRes = MR.strings.no_results_found,
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    CatalogError(
                        message = animeSourceErrorText(refresh.error),
                        baseUrl = state.baseUrl,
                        onRetry = animeList::retry,
                        onOpenInWebView = { url ->
                            navigator.push(WebViewScreen(url, state.sourceName, sourceId))
                        },
                        modifier = Modifier.padding(contentPadding).padding(16.dp),
                    )
                }
                // The same grid Mihon's catalogue draws, down to the spacing and the
                // "already in your library" badge, because it is the same components.
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    contentPadding = contentPadding + PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
                    horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
                ) {
                    items(animeList.itemCount) { index ->
                        val anime = animeList[index] ?: return@items
                        MangaCompactGridItem(
                            title = anime.title,
                            // The AnimeCover, not the url: Coil routes it to
                            // AnimeCoverFetcher, which asks the source that published it and
                            // carries its headers.
                            coverData = anime.asAnimeCover(),
                            coverAlpha = if (anime.favorite) {
                                CommonMangaItemDefaults.BrowseFavoriteCoverAlpha
                            } else {
                                1f
                            },
                            coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                            onLongClick = { navigator.push(AnimeDetailsScreen(anime.id)) },
                            onClick = { navigator.push(AnimeDetailsScreen(anime.id)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * What to show when a source's catalogue will not load.
 *
 * The message alone was a dead end: a Cloudflare challenge, an expired session or a site that
 * happens to be down all looked the same and left nothing to do but go back. Retry covers the
 * transient cases, and opening the site in a WebView is how a challenge actually gets solved —
 * once it passes there, the cookies are shared and the source works.
 */
@Composable
private fun CatalogError(
    message: String,
    baseUrl: String?,
    onRetry: () -> Unit,
    onOpenInWebView: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) {
                Text(stringResource(MR.strings.action_retry))
            }
            if (baseUrl != null) {
                OutlinedButton(onClick = { onOpenInWebView(baseUrl) }) {
                    Text(stringResource(MR.strings.action_open_in_web_view))
                }
            }
        }
    }
}
