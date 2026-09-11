package eu.kanade.tachiyomi.ui.animeextension

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.anime.ANMR

/**
 * The anime half of Browse's extension tabs.
 *
 * A tab of its own rather than a section inside Mihon's: the two have separate repositories,
 * separate trust and separate loaders, and mixing them would mean touching the manga tab that
 * has to keep merging from upstream.
 */
@Composable
fun animeExtensionsTab(): TabContent {
    return TabContent(
        titleRes = ANMR.strings.label_anime_extensions_tab,
        content = { contentPadding, _: SnackbarHostState ->
            AnimeExtensionsContent(modifier = Modifier.padding(contentPadding))
        },
    )
}
