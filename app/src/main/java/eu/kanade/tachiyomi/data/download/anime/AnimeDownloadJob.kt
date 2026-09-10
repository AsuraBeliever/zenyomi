package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Drains the anime download queue.
 *
 * One worker for the whole queue rather than one per episode: videos are big, and downloading
 * several at once on a phone connection makes every one of them slower without finishing any
 * sooner. It runs in the foreground so Android does not kill a download that takes minutes.
 */
class AnimeDownloadJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var downloadManager: AnimeDownloadManager

    @Inject private lateinit var downloader: AnimeDownloader

    @Inject private lateinit var notifier: AnimeDownloadNotifier

    @Inject private lateinit var sourceManager: AnimeSourceManager

    @Inject private lateinit var getAnime: GetAnime

    @Inject private lateinit var getEpisode: GetEpisode

    @Inject private lateinit var getEpisodeVideos: GetEpisodeVideos

    override suspend fun doWork(): Result {
        graph.inject(this)
        setForegroundSafely()

        return withIOContext {
            try {
                drainQueue()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e) { "Anime download queue failed" }
                    Result.failure()
                }
            } finally {
                notifier.dismissProgress()
            }
        }
    }

    private suspend fun drainQueue() {
        while (true) {
            val item = downloadManager.nextItem() ?: return

            val anime = getAnime.await(item.animeId)
            val episode = getEpisode.await(item.episodeId)
            val source = anime?.let { sourceManager.get(it.source) }

            if (anime == null || episode == null || source == null) {
                // The anime or episode is gone from the database; nothing to download.
                downloadManager.dequeue(item.episodeId)
                continue
            }

            notifier.showProgress(episode.name, 0, downloadManager.queue.value.size - 1)

            val video = runCatching { getEpisodeVideos.await(anime.source, episode) }
                .getOrDefault(emptyList())
                .let { with(getEpisodeVideos) { it.best() } }

            if (video == null || !downloader.isDownloadable(source, video)) {
                notifier.showError(episode.name)
                downloadManager.dequeue(item.episodeId)
                continue
            }

            // The downloader reports progress per episode; mirror it into the notification for
            // as long as this one is in flight.
            val result = coroutineScope {
                val mirror = launch {
                    downloader.progress.collect { byEpisode ->
                        byEpisode[episode.id]?.let {
                            notifier.showProgress(episode.name, it, downloadManager.queue.value.size - 1)
                        }
                    }
                }
                downloader.download(anime, source, episode, video).also { mirror.cancel() }
            }

            if (result.isFailure) {
                notifier.showError(episode.name)
            }
            downloadManager.dequeue(item.episodeId)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val queue = downloadManager.queue.first()
        return ForegroundInfo(
            Notifications.ID_DOWNLOAD_EPISODE_PROGRESS,
            notifier.progressNotification(null, 0, queue.size),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG = "AnimeDownload"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            // KEEP, not REPLACE: a running worker already picks up whatever was just added to
            // the queue, and replacing it would restart the video it is halfway through.
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}
