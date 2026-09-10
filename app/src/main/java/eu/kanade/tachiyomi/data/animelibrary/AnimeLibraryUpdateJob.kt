package eu.kanade.tachiyomi.data.animelibrary

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.RefreshAnimeLibrary
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import java.util.concurrent.TimeUnit

/**
 * Periodically refetches episodes for every anime in the library.
 *
 * A separate worker from Mihon's [eu.kanade.tachiyomi.data.library.LibraryUpdateJob] rather than
 * an extra branch inside it: the manga job is the one piece of Mihon that must keep working
 * untouched, and the two libraries can fail independently without taking each other down.
 *
 * The schedule and device restrictions are read from the same [LibraryPreferences] the user
 * already sets for manga. Anime does not get a second copy of that setting — one "update the
 * library every N hours" preference governing both is what a user expects from one app.
 */
class AnimeLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var libraryPreferences: LibraryPreferences

    @Inject private lateinit var refreshAnimeLibrary: RefreshAnimeLibrary

    @Inject private lateinit var notifier: AnimeLibraryUpdateNotifier

    override suspend fun doWork(): Result {
        graph.inject(this)

        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val restrictions = libraryPreferences.autoUpdateDeviceRestrictions.get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }

            // A manual refresh is already doing this work; let it finish.
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        setForegroundSafely()

        return withIOContext {
            try {
                val result = refreshAnimeLibrary.await { current, total, title ->
                    notifier.showProgressNotification(current, total, title)
                }
                if (result.updates.isNotEmpty()) {
                    notifier.showUpdateNotifications(result.updates)
                }
                notifier.showUpdateErrorNotification(result.failed)
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e) { "Anime library update failed" }
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_ANIME_LIBRARY_PROGRESS,
            notifier.progressNotification(0, 0, null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG = "AnimeLibraryUpdate"
        private const val WORK_NAME_AUTO = "AnimeLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "AnimeLibraryUpdate-manual"

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = context.appGraph.libraryPreferences
            val interval = prefInterval ?: preferences.autoUpdateInterval.get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions.get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequest = NetworkRequest.Builder().apply {
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    if (DEVICE_ONLY_ON_WIFI in restrictions) {
                        addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    }
                    if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                }.build()
                val constraints = Constraints.Builder()
                    .setRequiredNetworkRequest(networkRequest, networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<AnimeLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(workManager: WorkManager): Boolean {
            if (workManager.isRunning(TAG)) return false

            val request = OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val workManager = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            workManager.getWorkInfos(workQuery).get().forEach {
                workManager.cancelWorkById(it.id)

                if (it.tags.contains(WORK_NAME_AUTO)) {
                    setupTask(context)
                }
            }
        }
    }
}
