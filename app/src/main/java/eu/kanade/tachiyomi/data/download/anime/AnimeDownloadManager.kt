package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

/**
 * The queue of episodes waiting to be downloaded, and what the UI talks to.
 *
 * The download itself happens in [AnimeDownloadJob] so it outlives the screen that started
 * it — a video takes long enough that tying it to a ViewModel's scope, as this used to, meant
 * navigating back cancelled it halfway.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeDownloadManager(
    private val context: Context,
    private val store: AnimeDownloadStore,
    private val provider: AnimeDownloadProvider,
    private val downloader: AnimeDownloader,
) {

    private val _queue = MutableStateFlow(store.restore())

    /** Episodes waiting or downloading, in queue order. The head is the one in flight. */
    val queue: StateFlow<List<AnimeDownloadItem>> = _queue.asStateFlow()

    val progress = downloader.progress

    fun enqueue(anime: Anime, episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        _queue.update { current ->
            val known = current.mapTo(mutableSetOf()) { it.episodeId }
            current + episodes
                .filter { it.id !in known }
                .map { AnimeDownloadItem(anime.id, it.id) }
        }
        persist()
        AnimeDownloadJob.start(context)
    }

    /** Removes an entry that has not started yet, or the running one after it is cancelled. */
    fun dequeue(episodeId: Long) {
        _queue.update { queue -> queue.filterNot { it.episodeId == episodeId } }
        persist()
    }

    fun clearQueue() {
        _queue.value = emptyList()
        persist()
        AnimeDownloadJob.stop(context)
    }

    internal fun nextItem(): AnimeDownloadItem? = _queue.value.firstOrNull()

    fun isDownloaded(anime: Anime, source: AnimeSource, episode: Episode): Boolean =
        downloader.isDownloaded(anime, source, episode)

    fun downloadedUri(anime: Anime, source: AnimeSource, episode: Episode): String? =
        downloader.downloadedUri(anime, source, episode)

    fun isDownloadableSource(source: AnimeSource): Boolean = downloader.isDownloadableSource(source)

    /**
     * Deletes a downloaded episode. Returns whether a file was actually removed, so a caller
     * can tell "deleted" from "there was nothing there".
     */
    fun deleteEpisode(anime: Anime, source: AnimeSource, episode: Episode): Boolean {
        val file = provider.findEpisodeFile(anime, source, episode) ?: return false
        return file.delete()
    }

    private fun persist() = store.save(_queue.value)
}
