package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import androidx.core.app.NotificationCompat
import dev.zacsweers.metro.Inject
import eu.kanade.presentation.anime.AnimeSourceError
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.torrent.EmptyTorrentException
import eu.kanade.tachiyomi.data.torrent.TorrentAddonException
import eu.kanade.tachiyomi.data.torrent.TorrentDisabledException
import eu.kanade.tachiyomi.data.torrent.TorrentNoPeersException
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.anime.ANMR

/**
 * Notifications for the anime download queue.
 *
 * The progress notification doubles as the foreground-service notification for
 * [AnimeDownloadJob], which is what keeps a long video download alive when the app is in the
 * background.
 */
@Inject
class AnimeDownloadNotifier(private val context: Context) {

    private val progressBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_ANIME_DOWNLOADER_PROGRESS) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
    }

    fun progressNotification(title: String?, progress: AnimeDownloadProgress?, remaining: Int) = progressBuilder
        .setContentTitle(
            title?.chop(40) ?: context.stringResource(ANMR.strings.anime_download_notifier_downloading),
        )
        // How much has arrived and how fast, which is what somebody watching a slow download
        // actually wants to know. The queue count moves to the line below rather than being
        // dropped: both fit.
        .setContentText(progress?.describe(context))
        .setSubText(
            if (remaining > 0) {
                context.stringResource(ANMR.strings.anime_download_notifier_queue, remaining)
            } else {
                null
            },
        )
        // A stream that never said how big it is leaves the percentage unknown; an
        // indeterminate bar is honest about that instead of one that never moves.
        .setProgress(100, progress?.percent ?: 0, progress?.percent == null)
        .build()

    fun showProgress(title: String?, progress: AnimeDownloadProgress?, remaining: Int) {
        context.notify(Notifications.ID_DOWNLOAD_EPISODE_PROGRESS, progressNotification(title, progress, remaining))
    }

    /**
     * @param error what went wrong, when it is something the viewer can fix. Only a torrent's
     * reasons are spelled out — torrents turned off, no add-on, nobody sharing — because those
     * have a next step. Anything else stays a bare "could not download", as it always was.
     */
    fun showError(episodeName: String, error: Throwable? = null) {
        val reason = error?.takeIf { it.isTorrentFailure() }
            ?.let { AnimeSourceError.describe(it) }
            ?.let { message -> message.text ?: message.res?.let { res -> context.stringResource(res) } }
        context.notify(
            Notifications.ID_DOWNLOAD_EPISODE_ERROR,
            Notifications.CHANNEL_ANIME_DOWNLOADER_ERROR,
        ) {
            setContentTitle(
                context.stringResource(ANMR.strings.anime_download_notifier_episode_error, episodeName.chop(40)),
            )
            if (reason != null) {
                setContentText(reason)
                setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            }
            setSmallIcon(R.drawable.ic_zenyomi)
            setAutoCancel(true)
        }
    }

    private fun Throwable.isTorrentFailure(): Boolean =
        this is TorrentDisabledException ||
            this is TorrentAddonException ||
            this is TorrentNoPeersException ||
            this is EmptyTorrentException

    fun dismissProgress() = context.cancelNotification(Notifications.ID_DOWNLOAD_EPISODE_PROGRESS)
}
