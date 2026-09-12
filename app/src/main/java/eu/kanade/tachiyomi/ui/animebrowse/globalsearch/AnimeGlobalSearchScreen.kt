package eu.kanade.tachiyomi.ui.animebrowse.globalsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.animebrowse.AnimeCatalogScreen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * One query across every anime source.
 *
 * A row per source, results scrolling sideways inside it. The vertical alternative — every
 * result in one list — loses which source a result came from, and with this ecosystem that is
 * the single most important thing about a result: it decides whether it will actually play.
 */
class AnimeGlobalSearchScreen(
    private val initialQuery: String = "",
    /**
     * When set, results are offered as a migration target for this anime instead of being
     * opened. The search is the same search; only what a tap means changes.
     */
    private val migrateFromId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<AnimeGlobalSearchViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            if (initialQuery.isNotBlank() && !state.searched) {
                viewModel.setQuery(initialQuery)
                viewModel.search()
            }
        }

        val migrated = state.migrated
        LaunchedEffect(migrated) {
            if (migrated) {
                viewModel.clearMigrated()
                navigator.pop()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = {
                        AppBarTitle(
                            stringResource(
                                if (migrateFromId != null) {
                                    ANMR.strings.anime_migrate_pick
                                } else {
                                    ANMR.strings.anime_global_search
                                },
                            ),
                        )
                    },
                    searchQuery = state.query ?: "",
                    onChangeSearchQuery = viewModel::setQuery,
                    onSearch = { viewModel.search() },
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (!state.searched) {
                EmptyScreen(
                    stringRes = ANMR.strings.anime_global_search_hint,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            if (state.migrating) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }

            LazyColumn(contentPadding = contentPadding) {
                items(state.visible, key = { it.sourceId }) { result ->
                    SourceResultRow(
                        result = result,
                        onOpenSource = { navigator.push(AnimeCatalogScreen(result.sourceId)) },
                        onOpenAnime = {
                            if (migrateFromId != null) {
                                viewModel.askToMigrate(migrateFromId, it)
                            } else {
                                navigator.push(AnimeDetailsScreen(it.id))
                            }
                        },
                    )
                }
            }
        }

        state.migration?.let { migration ->
            AlertDialog(
                onDismissRequest = viewModel::dismissMigration,
                title = { Text(stringResource(ANMR.strings.anime_migrate_title)) },
                text = {
                    Text(
                        stringResource(
                            ANMR.strings.anime_migrate_confirm,
                            migration.current.title,
                            migration.target.title,
                        ),
                    )
                },
                // Two ways forward, because they are genuinely different intentions: replacing
                // a dead source, or keeping both because they are different cuts of the show.
                confirmButton = {
                    TextButton(onClick = { viewModel.migrate(replace = true) }) {
                        Text(stringResource(ANMR.strings.anime_migrate_replace))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.migrate(replace = false) }) {
                        Text(stringResource(ANMR.strings.anime_migrate_keep))
                    }
                },
            )
        }
    }
}

@Composable
private fun SourceResultRow(
    result: AnimeGlobalSearchViewModel.SourceResult,
    onOpenSource: () -> Unit,
    onOpenAnime: (Anime) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSource)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.sourceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = LocaleHelper.getSourceDisplayName(result.lang, LocalContext.current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.loading) CircularProgressIndicator(Modifier.size(18.dp))
        }

        when {
            result.error != null -> Text(
                // The same words the source screens use, so a failure reads the same wherever
                // it is met rather than being a different mystery each time.
                text = animeSourceErrorText(result.error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            result.anime.isEmpty() && !result.loading -> Text(
                text = stringResource(MR.strings.no_results_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(result.anime, key = { it.id }) { anime ->
                    Column(
                        modifier = Modifier
                            .width(96.dp)
                            .clickable { onOpenAnime(anime) },
                    ) {
                        AsyncImage(
                            model = anime,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .height(144.dp)
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
