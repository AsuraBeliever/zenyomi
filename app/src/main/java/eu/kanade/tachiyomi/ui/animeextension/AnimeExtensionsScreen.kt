package eu.kanade.tachiyomi.ui.animeextension

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.animeextension.model.displayName
import eu.kanade.tachiyomi.animeextension.model.isDead
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.FilterList
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * The anime extensions installed on the device, as a tab of the anime browse screen.
 *
 * Deliberately narrow: it shows what the loader found and lets an extension be trusted
 * or removed. Browsing and installing from a repository needs the anime extension store,
 * which is still to be ported.
 */
@Composable
fun AnimeExtensionsContent(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<AnimeExtensionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddStore by remember { mutableStateOf(false) }

    if (showAddStore) {
        AddStoreDialog(
            onDismiss = { showAddStore = false },
            onConfirm = { url, onFailed ->
                // Closing on failure would leave the user with no idea why nothing
                // happened, so the dialog stays open and says so.
                viewModel.addStore(url) { ok -> if (ok) showAddStore = false else onFailed() }
            },
        )
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAddStore = true }) {
                Text(stringResource(ANMR.strings.label_anime_extension_repos))
            }
            Spacer(Modifier.weight(1f))
            LanguageFilter(
                languages = state.languages,
                selected = state.selectedLanguage,
                onSelect = viewModel::setLanguage,
            )
            IconButton(
                onClick = viewModel::refreshAvailable,
                enabled = !state.isRefreshing,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Refresh,
                    contentDescription = stringResource(MR.strings.action_webview_refresh),
                )
            }
        }
        // A few hundred extensions is too many to find anything by eye.
        OutlinedTextField(
            value = state.searchQuery.orEmpty(),
            onValueChange = viewModel::search,
            singleLine = true,
            label = { Text(stringResource(MR.strings.action_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        AnimeExtensionsList(state, viewModel)
    }
}

@Composable
private fun AnimeExtensionsList(
    state: AnimeExtensionsViewModel.State,
    viewModel: AnimeExtensionsViewModel,
) {
    when {
        state.isLoading -> LoadingScreen()
        state.isEmpty -> EmptyScreen(stringRes = ANMR.strings.information_empty_anime_extensions)
        state.isFilteredEmpty -> EmptyScreen(stringRes = ANMR.strings.anime_extension_no_results)
        else -> LazyColumn {
            animeExtensionItems(
                state = state,
                onTrust = viewModel::trust,
                onInstall = viewModel::install,
                onUninstall = viewModel::uninstall,
            )
        }
    }
}

/**
 * The rows of the anime extension list, as list items.
 *
 * Extracted so the anime extensions can share one scrolling list with Mihon's manga ones
 * under collapsible headers.
 */
fun LazyListScope.animeExtensionItems(
    state: AnimeExtensionsViewModel.State,
    onTrust: (AnimeExtension.Untrusted) -> Unit,
    onInstall: (AnimeExtension.Available) -> Unit,
    onUninstall: (AnimeExtension.Installed) -> Unit,
) {
    items(state.untrusted, key = { "untrusted-" + it.pkgName }) { extension ->
        ListItem(
            headlineContent = { Text(extension.name) },
            supportingContent = {
                Text(
                    text = stringResource(MR.strings.ext_untrusted),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            trailingContent = {
                TextButton(onClick = { onTrust(extension) }) {
                    Text(stringResource(MR.strings.ext_trust))
                }
            },
        )
    }
    state.groupedAvailable.forEach { (lang, extensions) ->
        item(key = "lang-$lang") {
            Text(
                text = LocaleHelper.getSourceDisplayName(lang, LocalContext.current),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(extensions, key = { "available-" + it.pkgName }) { extension ->
            val dead = extension.isDead
            ListItem(
                headlineContent = { Text(extension.displayName) },
                supportingContent = {
                    Column {
                        Text(extension.versionName, style = MaterialTheme.typography.bodySmall)
                        // The repositories mark abandoned sources in the name itself.
                        // Saying so here saves installing one to find out.
                        if (dead) {
                            Text(
                                text = stringResource(ANMR.strings.anime_extension_dead),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                trailingContent = {
                    TextButton(onClick = { onInstall(extension) }) {
                        Text(stringResource(MR.strings.ext_install))
                    }
                },
            )
        }
    }
    items(state.installed, key = { "installed-" + it.pkgName }) { extension ->
        ListItem(
            headlineContent = { Text(extension.name) },
            supportingContent = {
                Column {
                    Text(extension.versionName)
                    Text(
                        text = pluralStringResource(
                            ANMR.plurals.num_anime_sources,
                            extension.sources.size,
                            extension.sources.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            trailingContent = {
                TextButton(onClick = { onUninstall(extension) }) {
                    Text(stringResource(MR.strings.ext_uninstall))
                }
            },
        )
    }
}

/**
 * Asks for an extension repository's index url.
 *
 * The anime repositories a user follows are their own choice, so this takes a url rather
 * than shipping a list.
 */
@Composable
fun AddStoreDialog(onDismiss: () -> Unit, onConfirm: (String, () -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ANMR.strings.label_anime_extension_repos)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    failed = false
                },
                singleLine = true,
                isError = failed,
                label = { Text("index.min.json") },
                supportingText = if (failed) {
                    { Text(stringResource(ANMR.strings.anime_extension_repo_invalid)) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) { failed = true } },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

/**
 * Narrows the list to one language, the way Mihon's manga extension list can be narrowed.
 *
 * Only the languages actually present are offered: a menu full of options that match nothing
 * is worse than no menu.
 */
@Composable
fun LanguageFilter(
    languages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = MaterialSymbols.Rounded.FilterList,
            contentDescription = stringResource(MR.strings.action_filter),
            tint = if (selected != null) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(ANMR.strings.anime_extension_all_languages)) },
            trailingIcon = {
                if (selected == null) Icon(MaterialSymbols.Rounded.Check, contentDescription = null)
            },
            onClick = {
                onSelect(null)
                expanded = false
            },
        )
        languages.forEach { lang ->
            DropdownMenuItem(
                text = { Text(LocaleHelper.getSourceDisplayName(lang, context)) },
                trailingIcon = {
                    if (selected == lang) Icon(MaterialSymbols.Rounded.Check, contentDescription = null)
                },
                onClick = {
                    onSelect(lang)
                    expanded = false
                },
            )
        }
    }
}
