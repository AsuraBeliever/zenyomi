package eu.kanade.tachiyomi.ui.animelibrary

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.animelibrary.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.ui.animebrowse.AnimeBrowseScreen
import eu.kanade.tachiyomi.ui.animecategory.AnimeCategoryScreen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.animehistory.AnimeHistoryScreen
import eu.kanade.tachiyomi.ui.animestats.AnimeStatsScreen
import eu.kanade.tachiyomi.ui.animeupdates.AnimeUpdatesScreen
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.Label
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.NewReleases
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Schedule
import mihon.icons.materialsymbols.rounded.SortByAlpha
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Anime library, the first anime-facing screen in the app.
 *
 * It sits before Mihon's manga library in the bar so the two libraries read as siblings,
 * which is the tabbed layout the project settled on rather than one merged library.
 */
data object AnimeLibraryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 5u,
                title = stringResource(ANMR.strings.label_anime_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val viewModel = metroViewModel<AnimeLibraryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        // Goes through the same worker as the periodic update instead of refreshing inline, so a
        // manual refresh survives leaving the screen and reports progress the same way.
        val onClickRefresh: () -> Unit = {
            val started = AnimeLibraryUpdateJob.startNow(context.workManager)
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.stringResource(
                        if (started) MR.strings.updating_library else MR.strings.update_already_running,
                    ),
                )
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = viewModel::search,
                    titleContent = { Text(stringResource(ANMR.strings.label_anime_library)) },
                    actions = {
                        var sortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.SortByAlpha,
                                contentDescription = stringResource(MR.strings.action_sort),
                            )
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            AnimeLibraryViewModel.Sort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sort.label)) },
                                    trailingIcon = {
                                        if (state.sort == sort) {
                                            Icon(MaterialSymbols.Rounded.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSort(sort)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                        IconButton(
                            onClick = onClickRefresh,
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Refresh,
                                contentDescription = stringResource(MR.strings.action_update_library),
                            )
                        }
                        IconButton(onClick = { navigator.push(AnimeUpdatesScreen()) }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.NewReleases,
                                contentDescription = stringResource(ANMR.strings.label_anime_updates),
                            )
                        }
                        // The bar was up to six icons and about to gain a seventh. What is
                        // used on every visit stays out; the rest goes behind the overflow.
                        var overflow by remember { mutableStateOf(false) }
                        IconButton(onClick = { overflow = true }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.MoreVert,
                                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            )
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.categories)) },
                                leadingIcon = {
                                    Icon(MaterialSymbols.AutoMirroredRounded.Label, contentDescription = null)
                                },
                                onClick = {
                                    overflow = false
                                    navigator.push(AnimeCategoryScreen())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(ANMR.strings.label_anime_history)) },
                                leadingIcon = {
                                    Icon(MaterialSymbols.Rounded.Schedule, contentDescription = null)
                                },
                                onClick = {
                                    overflow = false
                                    navigator.push(AnimeHistoryScreen())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(ANMR.strings.label_anime_statistics)) },
                                leadingIcon = {
                                    Icon(MaterialSymbols.Rounded.QueryStats, contentDescription = null)
                                },
                                onClick = {
                                    overflow = false
                                    navigator.push(AnimeStatsScreen())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(ANMR.strings.label_anime_extensions)) },
                                leadingIcon = {
                                    Icon(MaterialSymbols.Rounded.Explore, contentDescription = null)
                                },
                                onClick = {
                                    overflow = false
                                    navigator.push(AnimeBrowseScreen())
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
                // Only once something has actually been filed: a single tab reading "All"
                // is a row of chrome that does nothing.
                if (state.showCategoryTabs) {
                    AnimeCategoryTabs(
                        categories = state.categories,
                        counts = state.countByCategory,
                        selected = state.selectedCategory,
                        onSelect = viewModel::setCategory,
                    )
                }

                val innerPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = contentPadding.calculateBottomPadding(),
                )
                when {
                    state.isLoading -> LoadingScreen(Modifier.padding(innerPadding))
                    // A search that matched nothing is not an empty library, and telling the
                    // user to go add something would be wrong advice.
                    state.isFilteredEmpty -> EmptyScreen(
                        stringRes = ANMR.strings.anime_library_no_results,
                        modifier = Modifier.padding(innerPadding),
                    )
                    // Nor is an empty tab: there is nothing to search for, only something
                    // to file here.
                    state.isEmptyCategory -> EmptyScreen(
                        stringRes = ANMR.strings.anime_category_empty,
                        modifier = Modifier.padding(innerPadding),
                    )
                    state.isEmpty -> EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(innerPadding),
                    )
                    else -> AnimeLibraryContent(
                        library = state.library,
                        contentPadding = innerPadding,
                        onAnimeClick = { navigator.push(AnimeDetailsScreen(it)) },
                    )
                }
            }
        }
    }
}

/**
 * One tab per category, with "All" first.
 *
 * Scrollable rather than fixed: a fixed row squeezes six categories into unreadable slivers,
 * and the number of categories is the user's choice, not ours.
 */
@Composable
private fun AnimeCategoryTabs(
    categories: List<AnimeCategory>,
    counts: Map<Long, Int>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    val tabs = listOf<AnimeCategory?>(null) + categories.sortedBy { it.order }
    val selectedIndex = tabs.indexOfFirst { it?.id == selected }.coerceAtLeast(0)

    PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp) {
        tabs.forEachIndexed { index, category ->
            val count = if (category == null) counts.values.sum() else counts[category.id] ?: 0
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(category?.id) },
                text = {
                    Text(
                        text = when {
                            category == null -> stringResource(ANMR.strings.anime_category_all)
                            category.isSystemCategory -> stringResource(MR.strings.default_category)
                            else -> category.name
                        } + "  $count",
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
