package eu.kanade.tachiyomi.ui.animeextension

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
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
