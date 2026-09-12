package eu.kanade.tachiyomi.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.ExtensionTrustDialog
import eu.kanade.presentation.browse.extensionItems
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.animeextension.AddStoreDialog
import eu.kanade.tachiyomi.ui.animeextension.AnimeExtensionsViewModel
import eu.kanade.tachiyomi.ui.animeextension.LanguageFilter
import eu.kanade.tachiyomi.ui.animeextension.animeExtensionItems
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsViewModel
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * Every extension, manga and anime, in one list.
 *
 * The anime half is collapsed by default: the repositories publish several hundred entries and
 * expanded it buries everything under it, which is exactly the complaint that produced this
 * screen. The toolbar's search box drives both halves, so one query narrows the whole list
 * rather than half of it.
 */
@Composable
fun combinedExtensionsTab(
    extensionsViewModel: ExtensionsViewModel,
    animeExtensionsViewModel: AnimeExtensionsViewModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current

    val updatesCount by extensionsViewModel.updatesCount.collectAsStateWithLifecycle()
    var privateExtensionToUninstall by remember { mutableStateOf<Extension?>(null) }
    var trustState by remember { mutableStateOf<Extension.Untrusted?>(null) }
    var showAddStore by remember { mutableStateOf(false) }

    val layout = metroViewModel<BrowseLayoutViewModel>()
    val media by layout.extensionsMedia.collectAsStateWithLifecycle()
    val mangaExpanded by layout.mangaExtensionsExpanded.collectAsStateWithLifecycle()
    val animeExpanded by layout.animeExtensionsExpanded.collectAsStateWithLifecycle()

    val showManga = media != BrowseMedia.ANIME
    val showAnime = media != BrowseMedia.MANGA
    val bothShown = media == BrowseMedia.BOTH

    return TabContent(
        titleRes = MR.strings.label_extensions,
        badgeNumber = updatesCount.takeIf { it > 0 },
        searchEnabled = true,
        actions = listOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = { navigator.push(ExtensionFilterScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.extensionStores),
                onClick = { navigator.push(ExtensionStoresScreen()) },
            ),
            AppBar.OverflowAction(
                title = stringResource(ANMR.strings.label_anime_extension_repos),
                onClick = { showAddStore = true },
            ),
        ),
        content = { contentPadding, _ ->
            val state by extensionsViewModel.state.collectAsStateWithLifecycle()
            val animeState by animeExtensionsViewModel.state.collectAsStateWithLifecycle()

            BackHandler(enabled = state.searchQuery != null) {
                extensionsViewModel.search(null)
                animeExtensionsViewModel.search(null)
            }

            if (state.isLoading && animeState.isLoading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@TabContent
            }

            FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
                item(key = "media-filter") {
                    BrowseMediaFilter(selected = media, onSelect = layout::setExtensionsMedia)
                }

                if (showManga) {
                    if (bothShown) {
                        item(key = "manga-ext-header") {
                            BrowseSectionHeader(
                                title = stringResource(ANMR.strings.browse_section_manga_extensions),
                                count = state.items.values.sumOf { it.size },
                                expanded = mangaExpanded,
                                onToggle = layout::toggleMangaExtensions,
                            )
                        }
                    }
                    if (!bothShown || mangaExpanded) {
                        extensionItems(
                            items = state.items,
                            onLongClickItem = { extension ->
                                when (extension) {
                                    is Extension.Available ->
                                        extensionsViewModel.installExtension(extension)
                                    else -> {
                                        if (context.isPackageInstalled(extension.pkgName)) {
                                            extensionsViewModel.uninstallExtension(extension)
                                        } else {
                                            privateExtensionToUninstall = extension
                                        }
                                    }
                                }
                            },
                            onClickItemCancel = extensionsViewModel::cancelInstallUpdateExtension,
                            onOpenWebView = { extension ->
                                extension.sources.getOrNull(0)?.let {
                                    navigator.push(
                                        WebViewScreen(
                                            url = it.baseUrl,
                                            initialTitle = it.name,
                                            sourceId = it.id,
                                        ),
                                    )
                                }
                            },
                            onInstallExtension = extensionsViewModel::installExtension,
                            onUpdateExtension = extensionsViewModel::updateExtension,
                            onOpenExtension = { navigator.push(ExtensionDetailsScreen(it.pkgName)) },
                            onRequestTrust = { trustState = it },
                            onClickUpdateAll = extensionsViewModel::updateAllExtensions,
                        )
                    }
                }

                if (showAnime) {
                    if (bothShown) {
                        item(key = "anime-ext-header") {
                            BrowseSectionHeader(
                                title = stringResource(ANMR.strings.browse_section_anime_extensions),
                                count = animeState.installed.size + animeState.groupedAvailable.sumOf {
                                    it.second.size
                                },
                                expanded = animeExpanded,
                                onToggle = layout::toggleAnimeExtensions,
                            )
                        }
                    }
                    if (!bothShown || animeExpanded) {
                        item(key = "anime-ext-controls") {
                            AnimeExtensionControls(
                                languages = animeState.languages,
                                selectedLanguage = animeState.selectedLanguage,
                                onSelectLanguage = animeExtensionsViewModel::setLanguage,
                                refreshing = animeState.isRefreshing,
                                onRefresh = animeExtensionsViewModel::refreshAvailable,
                            )
                        }
                        animeExtensionItems(
                            state = animeState,
                            onTrust = animeExtensionsViewModel::trust,
                            onInstall = animeExtensionsViewModel::install,
                            onUninstall = animeExtensionsViewModel::uninstall,
                        )
                    }
                }
            }

            trustState?.let { untrusted ->
                ExtensionTrustDialog(
                    onClickConfirm = {
                        extensionsViewModel.trustExtension(untrusted)
                        trustState = null
                    },
                    onClickDismiss = {
                        extensionsViewModel.uninstallExtension(untrusted)
                        trustState = null
                    },
                    onDismissRequest = { trustState = null },
                )
            }

            if (showAddStore) {
                AddStoreDialog(
                    onDismiss = { showAddStore = false },
                    onConfirm = { url, onFailed ->
                        animeExtensionsViewModel.addStore(url) { ok ->
                            if (ok) showAddStore = false else onFailed()
                        }
                    },
                )
            }

            privateExtensionToUninstall?.let { extension ->
                ExtensionUninstallConfirmation(
                    extensionName = extension.name,
                    onClickConfirm = { extensionsViewModel.uninstallExtension(extension) },
                    onDismissRequest = { privateExtensionToUninstall = null },
                )
            }
        },
    )
}

/**
 * The controls that only make sense for the anime half: its own language filter and a refresh
 * for the repository index. They ride inside the section rather than in the toolbar, which is
 * shared with the manga half and has no room to say which of the two an icon belongs to.
 */
@Composable
private fun AnimeExtensionControls(
    languages: List<String>,
    selectedLanguage: String?,
    onSelectLanguage: (String?) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        LanguageFilter(
            languages = languages,
            selected = selectedLanguage,
            onSelect = onSelectLanguage,
        )
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Refresh,
                contentDescription = stringResource(MR.strings.action_webview_refresh),
            )
        }
    }
}

@Composable
private fun ExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(MR.strings.ext_confirm_remove)) },
        text = { Text(stringResource(MR.strings.remove_private_extension_message, extensionName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
