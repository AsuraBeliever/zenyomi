package eu.kanade.presentation.anime.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.anime.DownloadQuality
import eu.kanade.tachiyomi.data.download.anime.StartAnimeDownload
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Asks which quality to download at.
 *
 * Not on every download, by design: that would be in the way of the common case, which is
 * downloading the next episode of something at the quality you always use. It opens the first
 * time anything is downloaded, to settle what that quality is and remember it; when the one
 * you settled on does not exist for a particular episode, where the honest thing is to show
 * what does; and whenever you hold the download button, which is how you ask for it. [reason]
 * is which of the three, and the wording follows from it — "this is remembered" is true of the
 * first and a lie of the third.
 *
 * Each option carries its size, because "1080p" and "1080p, 1.4 GB" are different decisions on
 * a phone. The size is the picture alone, worked out from the stream's own bitrate and length,
 * so it is an estimate and is labelled as one.
 */
@Composable
fun DownloadQualityDialog(
    qualities: List<DownloadQuality>,
    reason: StartAnimeDownload.Reason,
    onConfirm: (height: Int?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val title = when (reason) {
        StartAnimeDownload.Reason.FIRST_TIME -> ANMR.strings.anime_download_quality_first_title
        StartAnimeDownload.Reason.NOT_AVAILABLE -> ANMR.strings.anime_download_quality_missing_title
        StartAnimeDownload.Reason.ASKED -> ANMR.strings.anime_download_quality_once_title
    }
    val body = when (reason) {
        StartAnimeDownload.Reason.FIRST_TIME -> ANMR.strings.anime_download_quality_first_body
        StartAnimeDownload.Reason.NOT_AVAILABLE -> ANMR.strings.anime_download_quality_missing_body
        StartAnimeDownload.Reason.ASKED -> ANMR.strings.anime_download_quality_once_body
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                qualities.forEach { quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(quality.height) },
                    ) {
                        RadioButton(
                            selected = false,
                            onClick = { onConfirm(quality.height) },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(text = quality.label, style = MaterialTheme.typography.bodyLarge)
                            // Left out rather than guessed at: a stream that gives nothing to
                            // work from gets no number instead of a made-up one.
                            quality.estimatedBytes?.let {
                                Text(
                                    text = stringResource(
                                        ANMR.strings.anime_download_quality_estimated_size,
                                        Formatter.formatFileSize(context, it),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
