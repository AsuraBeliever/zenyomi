package eu.kanade.tachiyomi.ui.animeextension

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import mihon.icons.materialsymbols.MaterialSymbols
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
fun AnimeExtensionsContent() {
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

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAddStore = true }) {
                Text(stringResource(ANMR.strings.label_anime_extension_repos))
            }
            Spacer(Modifier.weight(1f))
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
        else -> LazyColumn {
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
                        TextButton(onClick = { viewModel.trust(extension) }) {
                            Text(stringResource(MR.strings.ext_trust))
                        }
                    },
                )
            }
            items(state.available, key = { "available-" + it.pkgName }) { extension ->
                ListItem(
                    headlineContent = { Text(extension.name) },
                    supportingContent = {
                        Text(extension.versionName, style = MaterialTheme.typography.bodySmall)
                    },
                    trailingContent = {
                        TextButton(onClick = { viewModel.install(extension) }) {
                            Text(stringResource(MR.strings.ext_install))
                        }
                    },
                )
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
                        TextButton(onClick = { viewModel.uninstall(extension) }) {
                            Text(stringResource(MR.strings.ext_uninstall))
                        }
                    },
                )
            }
        }
    }
}

/**
 * Asks for an extension repository's index url.
 *
 * The anime repositories a user follows are their own choice, so this takes a url rather
 * than shipping a list.
 */
@Composable
private fun AddStoreDialog(onDismiss: () -> Unit, onConfirm: (String, () -> Unit) -> Unit) {
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
