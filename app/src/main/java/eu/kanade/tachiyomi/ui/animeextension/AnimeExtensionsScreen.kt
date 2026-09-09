package eu.kanade.tachiyomi.ui.animeextension

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Lists the anime extensions installed on the device.
 *
 * Deliberately narrow: it shows what the loader found and lets an extension be trusted
 * or removed. Browsing and installing from a repository needs the anime extension store,
 * which is still to be ported.
 */
class AnimeExtensionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<AnimeExtensionsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(ANMR.strings.label_anime_extensions),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isEmpty -> EmptyScreen(
                    stringRes = ANMR.strings.information_empty_anime_extensions,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> LazyColumn(contentPadding = contentPadding) {
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
                                        text = pluralStringResource(ANMR.plurals.num_anime_sources, extension.sources.size, extension.sources.size),
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
    }
}
