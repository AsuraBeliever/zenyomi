package eu.kanade.tachiyomi.ui.animebrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * One anime source's catalogue.
 *
 * Shows the source's popular listing. Search and filters exist in [GetRemoteAnime] but
 * have no UI yet; the entry detail screen they would lead to is also still to come, so
 * tapping an entry does nothing for now.
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
                is LoadState.Error -> Text(
                    text = refresh.error.message ?: refresh.error.toString(),
                    color = MaterialTheme.colorScheme.error,
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
                                model = anime.thumbnailUrl,
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
