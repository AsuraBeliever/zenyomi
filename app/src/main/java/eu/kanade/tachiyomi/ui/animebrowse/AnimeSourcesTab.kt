package eu.kanade.tachiyomi.ui.animebrowse

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.anime.ANMR

/**
 * Anime sources as a Browse tab, beside Mihon's manga ones.
 *
 * A separate tab rather than anime folded into Mihon's source list: the two are different
 * domain types with separate repositories and pinning, and merging them would mean rewriting
 * a screen that has to keep merging from upstream. Each list keeps its own language filter,
 * which is what the filtering was for.
 */
@Composable
fun animeSourcesTab(): TabContent {
    val viewModel = metroViewModel<AnimeSourcesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = ANMR.strings.label_anime_sources_tab,
        content = { contentPadding, _: SnackbarHostState ->
            AnimeSourcesList(
                state = state,
                contentPadding = contentPadding,
                onSelectLanguage = viewModel::setLanguage,
                onToggleShowHidden = viewModel::toggleShowHidden,
                onTogglePinned = viewModel::togglePinned,
                onToggleHidden = viewModel::toggleHidden,
            )
        },
    )
}
