package eu.kanade.tachiyomi.ui.animelibrary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibraryDisplayMode
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibrarySort
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ArrowDownward
import mihon.icons.materialsymbols.rounded.ArrowUpward
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Filter, sort and display, in one sheet.
 *
 * One sheet with three tabs rather than three toolbar menus: they are all answers to "show me
 * the library differently", and they are adjusted together far more often than separately.
 * This is the shape Mihon uses for the manga library, so the two sides feel the same.
 */
@Composable
fun AnimeLibrarySettingsSheet(
    settings: AnimeLibraryViewModel.Settings,
    onDismiss: () -> Unit,
    onSort: (AnimeLibrarySort) -> Unit,
    onDisplayMode: (AnimeLibraryDisplayMode) -> Unit,
    onFilter: (AnimeLibraryViewModel.Filter) -> Unit,
    onClearFilters: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(
                    stringResource(MR.strings.action_filter),
                    stringResource(MR.strings.action_sort),
                    stringResource(MR.strings.action_display_mode),
                ).forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (tab) {
                0 -> FilterTab(settings.filters, onFilter, onClearFilters)
                1 -> SortTab(settings, onSort)
                else -> DisplayTab(settings.displayMode, onDisplayMode)
            }
        }
    }
}

@Composable
private fun FilterTab(
    filters: AnimeLibraryViewModel.Filters,
    onFilter: (AnimeLibraryViewModel.Filter) -> Unit,
    onClearFilters: () -> Unit,
) {
    val labels = mapOf(
        AnimeLibraryViewModel.Filter.UNSEEN to ANMR.strings.anime_filter_unseen,
        AnimeLibraryViewModel.Filter.STARTED to ANMR.strings.anime_filter_started,
        AnimeLibraryViewModel.Filter.BOOKMARKED to ANMR.strings.anime_filter_bookmarked,
        AnimeLibraryViewModel.Filter.COMPLETED to ANMR.strings.anime_filter_completed,
    )

    Column {
        labels.forEach { (filter, label) ->
            val state = filters.of(filter)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFilter(filter) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                // Three states, not two: "is", "is not", and don't care. A plain checkbox
                // cannot express "hide everything I have started", which is half the point.
                TriStateCheckbox(
                    state = when (state) {
                        TriState.DISABLED -> ToggleableState.Off
                        TriState.ENABLED_IS -> ToggleableState.On
                        TriState.ENABLED_NOT -> ToggleableState.Indeterminate
                    },
                    onClick = null,
                )
                Text(
                    text = stringResource(label),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        if (filters.any) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(MR.strings.action_reset))
                }
            }
        }
    }
}

@Composable
private fun SortTab(
    settings: AnimeLibraryViewModel.Settings,
    onSort: (AnimeLibrarySort) -> Unit,
) {
    Column {
        AnimeLibrarySort.entries.forEach { sort ->
            val selected = settings.sort == sort
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSort(sort) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // The arrow is the affordance: it marks the active sort and says which way it
                // runs, and tapping that row again flips it.
                if (selected) {
                    Icon(
                        imageVector = if (settings.sortAscending) {
                            MaterialSymbols.Rounded.ArrowUpward
                        } else {
                            MaterialSymbols.Rounded.ArrowDownward
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(sort.label),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(start = if (selected) 12.dp else 36.dp),
                )
            }
        }
    }
}

@Composable
private fun DisplayTab(
    mode: AnimeLibraryDisplayMode,
    onDisplayMode: (AnimeLibraryDisplayMode) -> Unit,
) {
    Column {
        AnimeLibraryDisplayMode.entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDisplayMode(entry) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                androidx.compose.material3.RadioButton(
                    selected = mode == entry,
                    onClick = null,
                )
                Text(
                    text = stringResource(entry.label),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
