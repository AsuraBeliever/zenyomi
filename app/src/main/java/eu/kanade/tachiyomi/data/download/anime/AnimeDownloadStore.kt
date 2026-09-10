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
                putString(index.toString(), "${item.animeId}:${item.episodeId}")
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
                index to AnimeDownloadItem(animeId, episodeId)
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    fun clear() = preferences.edit { clear() }
}

data class AnimeDownloadItem(val animeId: Long, val episodeId: Long)
