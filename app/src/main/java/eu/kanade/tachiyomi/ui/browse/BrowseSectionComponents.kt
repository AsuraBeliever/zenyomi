package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ExpandLess
import mihon.icons.materialsymbols.rounded.ExpandMore
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Manga / Anime / Both, as a segmented row at the top of a Browse list.
 *
 * A visible control rather than another icon in the toolbar: the toolbar already holds a
 * language filter, and two filter icons side by side say nothing about which is which.
 */
@Composable
fun BrowseMediaFilter(
    selected: BrowseMedia,
    onSelect: (BrowseMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        BrowseMedia.BOTH to ANMR.strings.browse_filter_both,
        BrowseMedia.MANGA to ANMR.strings.browse_filter_manga,
        BrowseMedia.ANIME to ANMR.strings.browse_filter_anime,
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        options.forEachIndexed { index, (media, label) ->
            SegmentedButton(
                selected = selected == media,
                onClick = { onSelect(media) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

/**
 * A section header that folds its contents away.
 *
 * The anime extension list runs to several hundred entries, which buries whatever is under
 * it; being able to fold it is the difference between one list and two screens.
 */
@Composable
fun BrowseSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = if (expanded) {
                MaterialSymbols.Rounded.ExpandLess
            } else {
                MaterialSymbols.Rounded.ExpandMore
            },
            contentDescription = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
