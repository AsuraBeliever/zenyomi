package eu.kanade.tachiyomi.data.animelibrary

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * Notifications for the anime library update job.
 *
 * Deliberately thinner than [eu.kanade.tachiyomi.data.library.LibraryUpdateNotifier]: there is
 * no per-anime grouped notification with a cover and read/download actions yet, because the
 * anime side has no download manager to wire those actions to. What is here is the part that
 * is useful on its own — progress while it runs, and a summary of what turned up.
 */
@Inject
class AnimeLibraryUpdateNotifier(private val context: Context) {

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        roundingMode = RoundingMode.DOWN
        maximumFractionDigits = 0
    }

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_ANIME_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.app_name))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
    }

    fun progressNotification(current: Int, total: Int, title: String?) =
        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(
                    ANMR.strings.anime_notification_updating_progress,
                    percentFormatter.format(if (total == 0) 0f else current.toFloat() / total),
                ),
            )
            .setContentText(title?.chop(40))
            .setProgress(total, current, false)
            .build()

    fun showProgressNotification(current: Int, total: Int, title: String?) {
        context.notify(Notifications.ID_ANIME_LIBRARY_PROGRESS, progressNotification(current, total, title))
    }

    /**
     * Reports what actually appeared. Called only when [updates] is non-empty, so a quiet
     * update stays quiet instead of notifying "0 new episodes".
     */
    fun showUpdateNotifications(updates: List<Pair<Anime, List<Episode>>>) {
        val titles = updates.map { it.first.title }
        val text = when (titles.size) {
            1 -> titles.first().chop(40)
            else -> context.stringResource(
                ANMR.strings.anime_notification_new_episodes_text,
                titles.first().chop(30),
                titles.size - 1,
            )
        }

        context.notify(
            Notifications.ID_NEW_EPISODES,
            Notifications.CHANNEL_NEW_EPISODES,
        ) {
            setContentTitle(context.stringResource(ANMR.strings.anime_notification_new_episodes))
            setContentText(text)
            setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    updates.joinToString("\n") { (anime, episodes) ->
                        "${anime.title.chop(40)} (${episodes.size})"
                    },
                ),
            )
            setSmallIcon(R.drawable.ic_zenyomi)
            setLargeIcon(notificationBitmap)
            setGroup(Notifications.GROUP_NEW_EPISODES)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            setAutoCancel(true)
        }
    }

    fun showUpdateErrorNotification(failed: Int) {
        if (failed == 0) return
        context.notify(
            Notifications.ID_ANIME_LIBRARY_ERROR,
            Notifications.CHANNEL_ANIME_LIBRARY_ERROR,
        ) {
            setContentTitle(context.stringResource(ANMR.strings.anime_notification_update_error, failed))
            setSmallIcon(R.drawable.ic_zenyomi)
            setAutoCancel(true)
        }
    }

    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_ANIME_LIBRARY_PROGRESS)
    }
}
