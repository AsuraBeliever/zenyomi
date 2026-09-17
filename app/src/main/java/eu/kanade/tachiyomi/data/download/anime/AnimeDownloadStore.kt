package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import androidx.core.content.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Keeps the anime download queue across process death.
 *
 * A video is large enough that losing the queue when Android kills the app would be a real
 * loss, so the pending entries are written out as they change. Only the ids are stored: the
 * anime and the episode are read back from the database, which is the copy that can have
 * changed in the meantime.
 *
 * Its own preference file rather than a shared one, so clearing the anime queue can never
 * disturb Mihon's.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeDownloadStore(context: Context) {

    private val preferences = context.getSharedPreferences("active_anime_downloads", Context.MODE_PRIVATE)

    fun save(queue: List<AnimeDownloadItem>) {
        preferences.edit {
            clear()
            queue.forEachIndexed { index, item ->
                putString(
                    index.toString(),
                    "${item.animeId}:${item.episodeId}:${item.quality ?: ""}:${item.estimatedBytes ?: ""}",
                )
            }
        }
    }

    /**
     * Restores in the order the entries were written, which is the order the user queued them.
     * Anything unparseable is dropped rather than failing the restore: a corrupt entry should
     * cost one download, not the whole queue.
     */
    fun restore(): List<AnimeDownloadItem> {
        return preferences.all
            .mapNotNull { (key, value) ->
                val index = key.toIntOrNull() ?: return@mapNotNull null
                val parts = (value as? String)?.split(':') ?: return@mapNotNull null
                val animeId = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val episodeId = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                // Absent in entries written before downloads had a quality to remember, and
                // absent again whenever the source offers nothing to choose between.
                val quality = parts.getOrNull(2)?.toIntOrNull()
                val estimatedBytes = parts.getOrNull(3)?.toLongOrNull()
                index to AnimeDownloadItem(animeId, episodeId, quality, estimatedBytes)
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    fun clear() = preferences.edit { clear() }
}

/**
 * One episode waiting to be downloaded.
 *
 * @param quality the vertical resolution the viewer asked for. Null when the source offered
 * nothing to choose between, or when the entry was queued by a version that did not ask.
 * @param estimatedBytes how big it was worked out to be when the viewer chose, carried along
 * so the progress bar has something to be a fraction of. A stream declares no length, and
 * measuring it a second time while downloading would be the same requests all over again.
 */
data class AnimeDownloadItem(
    val animeId: Long,
    val episodeId: Long,
    val quality: Int? = null,
    val estimatedBytes: Long? = null,
)
