package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.browse.sourceItems
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.animebrowse.AnimeCatalogScreen
import eu.kanade.tachiyomi.ui.animebrowse.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.animebrowse.AnimeSourcesViewModel
import eu.kanade.tachiyomi.ui.animebrowse.animeSourceItems
import eu.kanade.tachiyomi.ui.animebrowse.globalsearch.AnimeGlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesViewModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.TravelExplore
import mihon.icons.materialsymbols.rounded.Visibility
import mihon.icons.materialsymbols.rounded.VisibilityOff
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * Every source, manga and anime, in one list.
 *
 * They were two tabs, whose titles the tab row then truncated to "Sources" and "Anime …".
 * Merging them is a layout change only: the two halves keep their own repositories, their own
 * pinning and their own preferences, and they are still built by the same two item builders
 * the separate screens use. What changes is that they share a scroll, a filter and a header
 * each can be folded by.
 */
@Composable
fun Screen.combinedSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    val mangaViewModel = metroViewModel<SourcesViewModel>()
    val mangaState by mangaViewModel.state.collectAsStateWithLifecycle()

    val animeViewModel = metroViewModel<AnimeSourcesViewModel>()
    val animeState by animeViewModel.state.collectAsStateWithLifecycle()

    val layout = metroViewModel<BrowseLayoutViewModel>()
    val media by layout.sourcesMedia.collectAsStateWithLifecycle()
    val mangaExpanded by layout.mangaSourcesExpanded.collectAsStateWithLifecycle()
    val animeExpanded by layout.animeSourcesExpanded.collectAsStateWithLifecycle()

    val showManga = media != BrowseMedia.ANIME
    val showAnime = media != BrowseMedia.MANGA
    val bothShown = media == BrowseMedia.BOTH

    return TabContent(
        titleRes = MR.strings.label_sources,
        actions = listOfNotNull(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = MaterialSymbols.Rounded.TravelExplore,
                // Whichever half is on screen is the one the search button belongs to. With
                // both shown the manga search wins, because that is Mihon's button.
                onClick = {
                    if (media == BrowseMedia.ANIME) {
                        navigator.push(AnimeGlobalSearchScreen())
                    } else {
                        navigator.push(GlobalSearchScreen())
                    }
                },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = MaterialSymbols.Rounded.FilterList,
                onClick = { navigator.push(SourcesFilterScreen()) },
            ).takeIf { showManga },
            AppBar.Action(
                title = stringResource(
                    if (animeState.showHidden) {
                        ANMR.strings.anime_sources_hide_hidden
                    } else {
                        ANMR.strings.anime_sources_show_hidden
                    },
                    animeState.hiddenCount,
                ),
                icon = if (animeState.showHidden) {
                    MaterialSymbols.Rounded.VisibilityOff
                } else {
                    MaterialSymbols.Rounded.Visibility
                },
                onClick = animeViewModel::toggleShowHidden,
            ).takeIf { showAnime && animeState.hiddenCount > 0 },
        ),
        content = { contentPadding, snackbarHostState ->
            if (mangaState.isLoading && animeState.isLoading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@TabContent
            }

            FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
                item(key = "media-filter") {
                    BrowseMediaFilter(selected = media, onSelect = layout::setSourcesMedia)
                }

                if (showManga) {
                    if (bothShown) {
                        item(key = "manga-sources-header") {
                            BrowseSectionHeader(
                                title = stringResource(ANMR.strings.browse_section_manga_sources),
                                count = mangaState.items.count { it is SourceUiModel.Item },
                                expanded = mangaExpanded,
                                onToggle = layout::toggleMangaSources,
                            )
                        }
                    }
                    if (!bothShown || mangaExpanded) {
                        sourceItems(
                            items = mangaState.items,
                            onClickItem = { source, listing ->
                                navigator.push(BrowseSourceScreen(source.id, listing.query))
                            },
                            onClickPin = mangaViewModel::togglePin,
                            onLongClickItem = mangaViewModel::showSourceDialog,
                        )
                    }
                }

                if (showAnime) {
                    if (bothShown) {
                        item(key = "anime-sources-header") {
                            BrowseSectionHeader(
                                title = stringResource(ANMR.strings.browse_section_anime_sources),
                                count = animeState.visibleSources.size,
                                expanded = animeExpanded,
                                onToggle = layout::toggleAnimeSources,
                            )
                        }
                    }
                    if (!bothShown || animeExpanded) {
                        animeSourceItems(
                            state = animeState,
                            onOpenSource = { navigator.push(AnimeCatalogScreen(it.id)) },
                            onOpenSettings = { navigator.push(AnimeSourcePreferencesScreen(it.id)) },
                            onTogglePinned = animeViewModel::togglePinned,
                            onToggleHidden = animeViewModel::toggleHidden,
                        )
                    }
                }
            }

            mangaState.dialog?.let { dialog ->
                val source = dialog.source
                SourceOptionsDialog(
                    source = source,
                    onClickPin = {
                        mangaViewModel.togglePin(source)
                        mangaViewModel.closeDialog()
                    },
                    onClickDisable = {
                        mangaViewModel.toggleSource(source)
                        mangaViewModel.closeDialog()
                    },
                    onDismiss = mangaViewModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                mangaViewModel.events.collectLatest { event ->
                    when (event) {
                        SourcesViewModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
