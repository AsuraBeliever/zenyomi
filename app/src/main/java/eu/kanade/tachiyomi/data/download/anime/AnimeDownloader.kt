package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

/**
 * Downloads one episode's video for offline watching.
 *
 * Deliberately narrower than Mihon's downloader, which runs a persistent queue with
 * notifications, a cache and pending deletion across some two thousand lines. This does
 * the part that matters first: fetch a video to the downloads folder and report progress.
 * A queue can be built around it.
 *
 * Progress is keyed by episode so several downloads can be watched at once even though
 * they are started one at a time.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeDownloader(
    private val provider: AnimeDownloadProvider,
    private val networkHelper: NetworkHelper,
) {

    private val _progress = MutableStateFlow(emptyMap<Long, Int>())

    /** Episode id to percentage, for rows that are downloading right now. */
    val progress: StateFlow<Map<Long, Int>> = _progress.asStateFlow()

    /**
     * Whether an episode can be downloaded at all.
     *
     * Local entries already live on the device, and their urls are content:// handles
     * from the storage framework, which an HTTP client cannot fetch. Offering a download
     * for them would fail and would be pointless if it worked.
     */
    fun isDownloadable(source: AnimeSource, video: Video?): Boolean =
        source.id != LOCAL_ANIME_SOURCE_ID && video?.videoUrl?.startsWith("http") == true

    fun isDownloadableSource(source: AnimeSource): Boolean = source.id != LOCAL_ANIME_SOURCE_ID

    fun isDownloaded(anime: Anime, source: AnimeSource, episode: Episode): Boolean =
        provider.findEpisodeFile(anime, source, episode) != null

    fun downloadedUri(anime: Anime, source: AnimeSource, episode: Episode): String? =
        provider.findEpisodeFile(anime, source, episode)?.uri?.toString()

    suspend fun download(
        anime: Anime,
        source: AnimeSource,
        episode: Episode,
        video: Video,
    ): Result<Unit> = withIOContext {
        runCatching {
            val extension = video.videoUrl.substringAfterLast('.', "mp4").take(4).ifBlank { "mp4" }
            val target = provider.createEpisodeFile(anime, source, episode, extension)
                ?: error("Could not create the download file")

            val request = Request.Builder()
                .url(video.videoUrl)
                .apply { video.headers?.let { headers(it) } }
                .build()

            networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response")
                val total = body.contentLength()

                target.openOutputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                setProgress(episode.id, ((downloaded * 100) / total).toInt())
                            }
                        }
                    }
                }
            }
            clearProgress(episode.id)
        }.onFailure {
            // A partial file would read as a finished download, so it goes.
            provider.findEpisodeFile(anime, source, episode)?.delete()
            clearProgress(episode.id)
        }
    }

    private fun setProgress(episodeId: Long, percent: Int) =
        _progress.update { it + (episodeId to percent) }

    private fun clearProgress(episodeId: Long) =
        _progress.update { it - episodeId }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        /** Matches tachiyomi.source.local.anime.LocalAnimeSource.ID without depending on it. */
        private const val LOCAL_ANIME_SOURCE_ID = 0L
    }
}
