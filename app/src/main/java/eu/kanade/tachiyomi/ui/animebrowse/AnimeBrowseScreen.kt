package eu.kanade.tachiyomi.ui.animebrowse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Settings
import eu.kanade.tachiyomi.ui.animeextension.AnimeExtensionsContent
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Anime browse: the sources the loaded extensions provide, and the extensions themselves.
 *
 * Mirrors the shape of Mihon's Browse rather than adding a second bottom-bar entry, so
 * the anime side keeps one entry point from its library.
 */
class AnimeBrowseScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(ANMR.strings.label_anime_sources),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(Modifier.padding(contentPadding)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(ANMR.strings.label_anime_sources)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(ANMR.strings.label_anime_extensions)) },
                    )
                }
                when (selectedTab) {
                    0 -> AnimeSourcesContent()
                    else -> AnimeExtensionsContent()
                }
            }
        }
    }
}

@Composable
private fun AnimeSourcesContent() {
    val navigator = LocalNavigator.currentOrThrow
    val viewModel = metroViewModel<AnimeSourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingScreen()
        state.isEmpty -> EmptyScreen(stringRes = MR.strings.empty_screen)
        else -> LazyColumn {
            items(state.sources, key = { it.id }) { source ->
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = {
                        Text(
                            text = source.lang,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        if (source is ConfigurableAnimeSource) {
                            IconButton(
                                onClick = { navigator.push(AnimeSourcePreferencesScreen(source.id)) },
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Settings,
                                    contentDescription = stringResource(MR.strings.label_settings),
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        navigator.push(AnimeCatalogScreen(source.id))
                    },
                )
            }
        }
    }
}
