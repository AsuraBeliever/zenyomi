package eu.kanade.tachiyomi.ui.animelibrary

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.animebrowse.AnimeBrowseScreen
import eu.kanade.tachiyomi.ui.animedetails.AnimeDetailsScreen
import eu.kanade.tachiyomi.ui.animehistory.AnimeHistoryScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Explore
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Schedule
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

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(ANMR.strings.label_anime_library),
                    actions = {
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = !state.isRefreshing,
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Refresh,
                                contentDescription = stringResource(MR.strings.action_update_library),
                            )
                        }
                        IconButton(onClick = { navigator.push(AnimeHistoryScreen()) }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Schedule,
                                contentDescription = stringResource(ANMR.strings.label_anime_history),
                            )
                        }
                        IconButton(onClick = { navigator.push(AnimeBrowseScreen()) }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Explore,
                                contentDescription = stringResource(ANMR.strings.label_anime_extensions),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isEmpty -> EmptyScreen(
                    stringRes = MR.strings.information_empty_library,
                    modifier = Modifier.padding(contentPadding),
                )
                else -> AnimeLibraryContent(
                    library = state.library,
                    contentPadding = contentPadding,
                    onAnimeClick = { navigator.push(AnimeDetailsScreen(it)) },
                )
            }
        }
    }
}
