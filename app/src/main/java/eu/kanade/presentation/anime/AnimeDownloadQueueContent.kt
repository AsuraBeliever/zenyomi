package eu.kanade.presentation.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.anime.describe
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueItem
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Close
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * The anime downloads waiting their turn.
 *
 * Deliberately plainer than the manga list beside it, which can reorder and pause because
 * Mihon's downloader supports both. This queue runs one episode at a time and start to finish,
 * so the only thing worth offering is taking something out of it — anything else would be a
 * control that does not do what it says.
 */
@Composable
fun AnimeDownloadQueueContent(
    items: List<AnimeDownloadQueueItem>,
    onCancel: (episodeId: Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.information_no_downloads,
            modifier = modifier.padding(contentPadding),
        )
        return
    }

    val context = LocalContext.current
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        items(items = items, key = { it.episodeId }) { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.small,
                        top = MaterialTheme.padding.small,
                        bottom = MaterialTheme.padding.small,
                    ),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.animeTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.episodeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The one being fetched says how it is going; the rest say where they came
                    // from, which is what tells two identically named episodes apart.
                    Text(
                        text = item.progress?.describe(context) ?: item.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.progress?.let { progress ->
                        // Indeterminate while nothing has said how big the episode is, which
                        // is honest: a bar sitting at zero looks like a download that stalled.
                        val percent = progress.percent
                        if (percent == null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = MaterialTheme.padding.extraSmall),
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = MaterialTheme.padding.extraSmall),
                            )
                        }
                    }
                }
                IconButton(onClick = { onCancel(item.episodeId) }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(ANMR.strings.anime_download_queue_cancel),
                    )
                }
            }
        }
    }
}
