package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
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

    fun progressNotification(title: String?, percent: Int, remaining: Int) = progressBuilder
        .setContentTitle(
            title?.chop(40) ?: context.stringResource(ANMR.strings.anime_download_notifier_downloading),
        )
        .setContentText(
            if (remaining > 0) {
                context.stringResource(ANMR.strings.anime_download_notifier_queue, remaining)
            } else {
                null
            },
        )
        // A source that does not send a length leaves percent at 0; an indeterminate bar is
        // honest about that instead of showing a bar that never moves.
        .setProgress(100, percent, percent <= 0)
        .build()

    fun showProgress(title: String?, percent: Int, remaining: Int) {
        context.notify(Notifications.ID_DOWNLOAD_EPISODE_PROGRESS, progressNotification(title, percent, remaining))
    }

    fun showError(episodeName: String) {
        context.notify(
            Notifications.ID_DOWNLOAD_EPISODE_ERROR,
            Notifications.CHANNEL_ANIME_DOWNLOADER_ERROR,
        ) {
            setContentTitle(
                context.stringResource(ANMR.strings.anime_download_notifier_episode_error, episodeName.chop(40)),
            )
            setSmallIcon(R.drawable.ic_zenyomi)
            setAutoCancel(true)
        }
    }

    fun dismissProgress() = context.cancelNotification(Notifications.ID_DOWNLOAD_EPISODE_PROGRESS)
}
