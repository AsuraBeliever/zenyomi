package eu.kanade.presentation.anime.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.anime.model.downloadedFilter
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Filter and sort for an entry's episodes.
 *
 * The twin of Mihon's [eu.kanade.presentation.manga.ChapterSettingsDialog], built from the same
 * generic pieces — [TabbedDialog], [TriStateItem], [SortItem] — so the two sheets are the same
 * sheet as far as anyone using them can tell. Mihon's own could not be reused because it reads
 * its current state straight off a Manga.
 *
 * Without the scanlator filter, which needs a per-entry list of scanlators the anime side does
 * not collect, and without the display tab, which chooses between showing a chapter's name and
 * its number — episodes are named by number already.
 */
@Composable
fun EpisodeSettingsDialog(
    onDismissRequest: () -> Unit,
    anime: Anime?,
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnseenFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    onSortModeChanged: (Long) -> Unit,
) {
    val context = LocalContext.current
    // The global "downloaded only" switch already forces this filter, so offering it here
    // would be a control that cannot change anything.
    val downloadedOnly = remember(context) { context.appGraph.basePreferences.downloadedOnly.get() }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = listOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    downloadFilter = anime?.downloadedFilter ?: TriState.DISABLED,
                    onDownloadFilterChanged = onDownloadFilterChanged.takeUnless { downloadedOnly },
                    unseenFilter = anime?.unseenFilter ?: TriState.DISABLED,
                    onUnseenFilterChanged = onUnseenFilterChanged,
                    bookmarkedFilter = anime?.bookmarkedFilter ?: TriState.DISABLED,
                    onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                )
                1 -> SortPage(
                    sortingMode = anime?.sorting ?: 0,
                    sortDescending = anime?.sortDescending() ?: false,
                    onItemSelected = onSortModeChanged,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    downloadFilter: TriState,
    onDownloadFilterChanged: ((TriState) -> Unit)?,
    unseenFilter: TriState,
    onUnseenFilterChanged: (TriState) -> Unit,
    bookmarkedFilter: TriState,
    onBookmarkedFilterChanged: (TriState) -> Unit,
) {
    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = downloadFilter,
        onClick = onDownloadFilterChanged,
    )
    TriStateItem(
        // The anime wording, not Mihon's "Unread": an episode is watched, not read.
        label = stringResource(ANMR.strings.action_filter_unseen),
        state = unseenFilter,
        onClick = onUnseenFilterChanged,
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = bookmarkedFilter,
        onClick = onBookmarkedFilterChanged,
    )
}

@Composable
private fun ColumnScope.SortPage(
    sortingMode: Long,
    sortDescending: Boolean,
    onItemSelected: (Long) -> Unit,
) {
    listOf(
        MR.strings.sort_by_source to Anime.EPISODE_SORTING_SOURCE,
        MR.strings.sort_by_number to Anime.EPISODE_SORTING_NUMBER,
        MR.strings.sort_by_upload_date to Anime.EPISODE_SORTING_UPLOAD_DATE,
        MR.strings.action_sort_alpha to Anime.EPISODE_SORTING_ALPHABET,
    ).map { (titleRes, mode) ->
        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = { onItemSelected(mode) },
        )
    }
}
