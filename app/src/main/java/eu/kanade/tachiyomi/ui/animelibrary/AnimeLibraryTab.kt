package eu.kanade.tachiyomi.ui.animelibrary

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.anime.components.DownloadQualityDialog
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.manga.components.LibraryBottomActionMenu
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.animelibrary.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.ui.animebrowse.AnimeBrowseScreen
import eu.kanade.tachiyomi.ui.animebrowse.globalsearch.AnimeGlobalSearchScreen
import eu.kanade.tachiyomi.ui.animecategory.AnimeCategoryScreen
import eu.kanade.tachiyomi.ui.animedetails.AnimeCategoryDialog
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
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.rounded.NewReleases
import mihon.icons.materialsymbols.rounded.QueryStats
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Schedule
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

        BackHandler(enabled = state.selectionMode, onBack = viewModel::clearSelection)

        state.qualityDialog?.let { (_, choice) ->
            DownloadQualityDialog(
                qualities = choice.qualities,
                firstTime = choice.remember,
                onConfirm = { height, _ -> viewModel.confirmQuality(height) },
                onDismissRequest = viewModel::dismissQualityDialog,
            )
        }

        state.changeCategoryDialog?.let { dialog ->
            // El mismo dialogo que la ficha, no una copia.
            AnimeCategoryDialog(
                categories = dialog.categories,
                initiallySelected = dialog.selected,
                onDismiss = viewModel::dismissChangeCategoryDialog,
                onConfirm = viewModel::setCategoriesForSelection,
                onEditCategories = {
                    viewModel.dismissChangeCategoryDialog()
                    navigator.push(AnimeCategoryScreen())
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = viewModel::search,
                    titleContent = { Text(stringResource(ANMR.strings.label_anime_library)) },
                    actions = {
                        var sheet by remember { mutableStateOf(false) }
                        IconButton(onClick = { sheet = true }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.FilterList,
                                contentDescription = stringResource(MR.strings.action_filter),
                                // The one piece of state worth showing without opening
                                // anything: a filter left on is otherwise invisible and
                                // looks like a library that lost entries.
                                tint = if (state.settings.filters.any) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                        if (sheet) {
                            AnimeLibrarySettingsSheet(
                                settings = state.settings,
                                onDismiss = { sheet = false },
                                onSort = viewModel::setSort,
                                onDisplayMode = viewModel::setDisplayMode,
                                onFilter = viewModel::cycleFilter,
                                onClearFilters = viewModel::clearFilters,
                            )
                        }
                        // Search, filter, overflow — the three the manga library shows, in
                        // that order. Refreshing and the updates shortcut moved into the menu
                        // where Mihon keeps them; a bar with five icons beside one with three
                        // is the difference you notice before you notice anything else.
                        var overflow by remember { mutableStateOf(false) }
                        IconButton(onClick = { overflow = true }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.MoreVert,
                                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            )
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            // The manga library's three, in its order. What used to live here —
                            // the updates, history and extensions shortcuts — is reachable from
                            // the bottom bar and from Browse; categories and statistics moved to
                            // More, beside the manga ones.
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_update_library)) },
                                onClick = {
                                    overflow = false
                                    onClickRefresh()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_update_category)) },
                                onClick = {
                                    overflow = false
                                    onClickRefresh()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_open_random_manga)) },
                                onClick = {
                                    overflow = false
                                    val random = viewModel.randomInCurrentCategory()
                                    if (random != null) {
                                        navigator.push(AnimeDetailsScreen(random.id))
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.stringResource(MR.strings.information_no_entries_found),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // La misma barra de la biblioteca de manga: solo toma lambdas.
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = viewModel::openChangeCategoryDialog,
                    onMarkAsReadClicked = { viewModel.markSelectedSeen(true) },
                    onMarkAsUnreadClicked = { viewModel.markSelectedSeen(false) },
                    onDownloadClicked = viewModel::downloadSelected,
                    onDeleteClicked = viewModel::removeSelectedFromLibrary,
                    onMigrateClicked = {
                        val first = state.selectedAnime.firstOrNull()
                        viewModel.clearSelection()
                        if (first != null) {
                            navigator.push(
                                AnimeGlobalSearchScreen(initialQuery = first.anime.title, migrateFromId = first.id),
                            )
                        }
                    },
                    episodes = true,
                )
            },
        ) { contentPadding ->
            Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
                // Only once something has actually been filed: a single tab reading "All"
                // is a row of chrome that does nothing.
                if (state.showCategoryTabs) {
                    AnimeCategoryTabs(
                        categories = state.categories,
                        counts = state.countByCategory,
                        totalCount = state.totalCount,
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
                    // A filter that excluded everything is not an empty library either: the
                    // fix is to relax it, not to go and add anime.
                    state.isFilterEmpty -> EmptyScreen(
                        stringRes = ANMR.strings.anime_library_no_matches,
                        modifier = Modifier.padding(innerPadding),
                    )
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
                        displayMode = state.settings.displayMode,
                        // The same preference the manga library reads, on purpose: it is how
                        // dense the grid is, and two libraries at different densities is the
                        // difference this screen exists to remove. 0 is "fit what you can",
                        // which is the default on both.
                        columns = viewModel.columnsFor(LocalConfiguration.current.orientation),
                        contentPadding = innerPadding,
                        searchQuery = state.searchQuery,
                        onAnimeClick = { id ->
                            // Con algo elegido, tocar suma o quita en vez de abrir la ficha,
                            // igual que en la biblioteca de manga.
                            if (state.selectionMode) {
                                state.library.firstOrNull { it.id == id }?.let(viewModel::toggleSelection)
                            } else {
                                navigator.push(AnimeDetailsScreen(id))
                            }
                        },
                        onGlobalSearchClicked = {
                            navigator.push(AnimeGlobalSearchScreen(initialQuery = state.searchQuery.orEmpty()))
                        },
                        selection = state.selection,
                        onAnimeLongClick = viewModel::toggleSelection,
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
    totalCount: Int,
    selected: Long?,
    onSelect: (Long) -> Unit,
) {
    // One tab per category and no "All", because Mihon has no "All": every entry is filed
    // somewhere, in the default category if nowhere else, so the tabs already cover the
    // library between them and an extra tab only showed the same covers again.
    val tabs = categories.sortedBy { it.order }
    val selectedIndex = tabs.indexOfFirst { it.id == selected }.coerceAtLeast(0)

    PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp) {
        tabs.forEachIndexed { index, category ->
            val count = counts[category.id] ?: 0
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(category.id) },
                text = {
                    Text(
                        text = if (category.isSystemCategory) {
                            stringResource(MR.strings.default_category)
                        } else {
                            category.name
                        } + "  $count",
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
