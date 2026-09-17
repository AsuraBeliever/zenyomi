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
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Asks which quality to download at.
 *
 * Opens twice in the life of the app, by design. The first time anything is downloaded, to
 * settle what quality you want and remember it — a dialog on every download would be in the
 * way of the common case. And after that only when the quality you settled on does not exist
 * for a particular episode, where the honest thing is to show what does.
 *
 * Each option carries its size, because "1080p" and "1080p, 1.4 GB" are different decisions on
 * a phone. The size is worked out from the stream's own bitrate and length, so it is an
 * estimate and is labelled as one.
 */
@Composable
fun DownloadQualityDialog(
    qualities: List<DownloadQuality>,
    firstTime: Boolean,
    onConfirm: (height: Int?, remember: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(
                    if (firstTime) {
                        ANMR.strings.anime_download_quality_first_title
                    } else {
                        ANMR.strings.anime_download_quality_missing_title
                    },
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        if (firstTime) {
                            ANMR.strings.anime_download_quality_first_body
                        } else {
                            ANMR.strings.anime_download_quality_missing_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                qualities.forEach { quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(quality.height, firstTime) },
                    ) {
                        RadioButton(
                            selected = false,
                            onClick = { onConfirm(quality.height, firstTime) },
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
