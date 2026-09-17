package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.text.format.Formatter
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.anime.ANMR

/**
 * A download in progress, in words.
 *
 * "180 MB of 420 MB · 1.2 MB/s". The same line goes on the episode row and in the
 * notification, because they answer the same question and disagreeing with each other would
 * be worse than either.
 *
 * The parts that cannot be known are left out rather than filled in with a zero: a stream that
 * has not said how big it is reads "180 MB so far", and a download whose speed has not been
 * measured yet just shows what has arrived.
 */
fun AnimeDownloadProgress.describe(context: Context): String {
    val done = Formatter.formatFileSize(context, downloadedBytes)
    val size = when (val total = totalBytes) {
        null -> context.stringResource(ANMR.strings.anime_download_progress_unknown_total, done)
        else -> context.stringResource(
            ANMR.strings.anime_download_progress_of,
            done,
            Formatter.formatFileSize(context, total),
        )
    }
    if (bytesPerSecond <= 0) return size
    val speed = context.stringResource(
        ANMR.strings.anime_download_progress_speed,
        Formatter.formatFileSize(context, bytesPerSecond),
    )
    return "$size · $speed"
}
