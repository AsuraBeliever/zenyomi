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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DeleteSweep
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Recently watched anime.
 */
class AnimeHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<AnimeHistoryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(ANMR.strings.label_anime_history),
                    navigateUp = navigator::pop,
                    actions = {
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
                state.isEmpty -> EmptyScreen(
                    stringRes = MR.strings.information_no_recent_manga,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
                    items(state.history, key = { it.id }) { entry ->
                        ListItem(
                            leadingContent = {
                                AsyncImage(
                                    model = entry.coverData,
                                    contentDescription = entry.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                            },
                            headlineContent = { Text(entry.title) },
                            supportingContent = {
                                Text(
                                    text = "Ep. " + entry.episodeNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            modifier = Modifier.clickable {
                                navigator.push(AnimeDetailsScreen(entry.animeId))
                            },
                        )
                    }
                }
            }
        }
    }
}
