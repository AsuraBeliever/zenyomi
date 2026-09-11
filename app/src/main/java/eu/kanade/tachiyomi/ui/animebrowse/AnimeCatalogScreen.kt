package eu.kanade.tachiyomi.ui.animebrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

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
                AppBar(
                    title = state.sourceName,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val refresh = animeList.loadState.refresh) {
                is LoadState.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                is LoadState.Error -> CatalogError(
                    message = refresh.error.message ?: refresh.error.toString(),
                    baseUrl = state.baseUrl,
                    onRetry = animeList::retry,
                    onOpenInWebView = { url ->
                        navigator.push(WebViewScreen(url, state.sourceName, sourceId))
                    },
                    modifier = Modifier.padding(contentPadding).padding(16.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = contentPadding,
                ) {
                    items(animeList.itemCount) { index ->
                        val anime = animeList[index] ?: return@items
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable { navigator.push(AnimeDetailsScreen(anime.id)) },
                        ) {
                            AsyncImage(
                                // The Anime itself, not its url: AnimeCoverFetcher needs
                                // the source to attach its headers.
                                model = anime,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                            Text(
                                text = anime.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
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
