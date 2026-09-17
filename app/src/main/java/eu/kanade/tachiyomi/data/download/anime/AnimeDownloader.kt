package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

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
    private val remuxer: HlsRemuxer,
    private val prefetcher: HlsPrefetcher,
    private val sizer: StreamSizer,
) {

    private val _progress = MutableStateFlow(emptyMap<Long, AnimeDownloadProgress>())

    /** Episode id to how far along it is, for rows that are downloading right now. */
    val progress: StateFlow<Map<Long, AnimeDownloadProgress>> = _progress.asStateFlow()

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

    /** The base names of what is on disk for this entry. Blocking I/O. */
    fun downloadedFileNames(anime: Anime, source: AnimeSource): Set<String> =
        provider.downloadedFileNames(anime, source)

    /**
     * Which of [episodes] already have a file, from one listing of the entry's directory.
     *
     * The per-episode question answered in bulk, because a screen asks it about every row it
     * has: on an entry with a thousand episodes, asking one at a time is a thousand listings
     * of the same directory. Blocking I/O, like the rest of this class.
     */
    fun downloadedEpisodeIds(
        anime: Anime,
        source: AnimeSource,
        episodes: List<Episode>,
    ): Set<Long> {
        if (episodes.isEmpty()) return emptySet()
        val names = provider.downloadedFileNames(anime, source)
        if (names.isEmpty()) return emptySet()
        return episodes
            .filter { provider.episodeFileName(it) in names }
            .mapTo(mutableSetOf()) { it.id }
    }

    fun downloadedUri(anime: Anime, source: AnimeSource, episode: Episode): String? =
        provider.findEpisodeFile(anime, source, episode)?.uri?.toString()

    /**
     * Fetches one episode into the downloads folder.
     *
     * What arrives at [Video.videoUrl] decides how: a video file is streamed straight to
     * disk, and an m3u8 playlist goes through ffmpeg, which fetches the segments it names
     * and writes the episode they add up to. Which one it is cannot be told from the url —
     * plenty of sources serve a playlist from a path that ends in neither .m3u8 nor .m3u —
     * so the answer comes from the first bytes of the response.
     */
    suspend fun download(
        anime: Anime,
        source: AnimeSource,
        episode: Episode,
        video: Video,
        quality: Int?,
        expectedBytes: Long?,
    ): Result<Unit> = withIOContext {
        runCatching {
            // Whatever is already there for this episode goes first, including a playlist
            // left by a version of this that did not know the difference. Otherwise the
            // storage framework keeps it and names the new file "episode (1).mp4".
            provider.findAnyEpisodeFile(anime, source, episode)?.delete()

            val request = Request.Builder()
                .url(video.videoUrl)
                .apply { video.headers?.let { headers(it) } }
                .build()

            var written: UniFile? = null

            val playlist = networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response")
                val input = body.byteStream()

                val head = ByteArray(SNIFF_BYTES)
                val headLength = input.readAtMost(head)

                if (looksLikePlaylist(head, headLength)) {
                    // Small enough to hold: a few hundred lines of text. Read out so the
                    // connection is closed before ffmpeg opens its own.
                    String(head, 0, headLength, Charsets.UTF_8) + input.readBytes().decodeToString()
                } else {
                    // A real video, and its first bytes are already in hand: write them and
                    // keep going rather than asking for them a second time.
                    val target = provider.createEpisodeFile(anime, source, episode, extensionFor(video.videoUrl))
                        ?: error("Could not create the download file")
                    written = target
                    streamToFile(input, head, headLength, body.contentLength(), target, episode.id)
                    null
                }
            }

            if (playlist != null) {
                // A master playlist names other playlists, one per quality, and ffmpeg left to
                // choose among them picks for itself. The quality is the viewer's, so the
                // variant is resolved here: the one they asked for, or the best on offer when
                // that request cannot be met.
                val variants = HlsPlaylist.variants(playlist, video.videoUrl)
                val chosen = variants.pick(quality)
                val media = chosen?.let { fetchText(it.url, video.headers) } ?: playlist
                // What was measured when the viewer chose, if anything was. A download queued
                // without a dialog has no number, and it is measured here instead: a handful of
                // HEAD requests at the start of something that runs for minutes costs nothing,
                // whereas doing it on the tap is the difference between a button and a wait.
                // The audio the source keeps apart from the picture is fetched too and lands
                // in the same file, so it belongs in the total. Leaving it out is what made a
                // download read "200 MB of 160 MB": the bytes counted every stream and the
                // total counted one.
                val estimated = expectedBytes ?: sizer
                    .sizeOfMedia(chosen?.url ?: video.videoUrl, video.headers, chosen?.bandwidth, media)
                    ?.plus(video.audioTracks.sumOf { sizer.sizeOfMedia(it.url, video.headers, null) ?: 0L })

                val target = provider.createEpisodeFile(anime, source, episode, REMUXED_EXTENSION)
                    ?: error("Could not create the download file")
                written = target
                val rate = DownloadRate()
                val report = { bytes: Long ->
                    setProgress(
                        episode.id,
                        AnimeDownloadProgress(
                            downloadedBytes = bytes,
                            // An estimate, and the only number available up front: a stream
                            // declares no length. The bar is honest about arriving a little
                            // before or after 100%.
                            estimatedTotalBytes = estimated,
                            bytesPerSecond = rate.sample(bytes),
                        ),
                    )
                }

                // The segments are fetched here, several at a time, rather than left to
                // ffmpeg, which asks for them one after another. See [HlsPrefetcher].
                val workspace = prefetcher.workspace(episode.id)
                val fetched = runCatching {
                    prefetchAll(chosen?.url ?: video.videoUrl, media, video, workspace, report)
                }.getOrNull()

                try {
                    remuxer.remux(
                        videoUrl = fetched?.video?.playlist?.absolutePath ?: chosen?.url ?: video.videoUrl,
                        headers = video.headers,
                        audioTracks = fetched?.audio ?: video.audioTracks,
                        subtitleTracks = video.subtitleTracks,
                        target = target,
                        // Nothing to report while muxing when the bytes are already on disk;
                        // ffmpeg is only copying them into a container at that point.
                        onBytes = if (fetched == null) report else { _ -> },
                    )
                } finally {
                    workspace.deleteRecursively()
                }
            }
            // Only now is it an episode rather than a download in flight.
            written?.let { provider.finish(it) }
            clearProgress(episode.id)
        }.onFailure {
            // Said out loud. A download that fails silently and leaves a notification with
            // no reason in it is one nobody can diagnose, here or from a bug report.
            logcat(LogPriority.ERROR, it) { "Could not download ${episode.name}" }
            // A partial file would read as a finished download, so it goes.
            provider.findAnyEpisodeFile(anime, source, episode)?.delete()
            clearProgress(episode.id)
        }
    }

    /** The video and its separate audio tracks, all now on local disk. */
    private data class Prefetched(val video: HlsPrefetcher.Local, val audio: List<Track>)

    /**
     * Fetches the picture and every audio track that goes with it, at the same time.
     *
     * One connection budget across all of them rather than one each: the audio streams are a
     * tenth the size of the video and finish early, and giving each its own budget would only
     * mean more connections to the same four hosts for no gain.
     *
     * Subtitles are left where they are. They are one small file apiece, so the round trip
     * ffmpeg pays for them is the only one there is.
     */
    private suspend fun prefetchAll(
        videoUrl: String,
        media: String,
        video: Video,
        workspace: File,
        onBytes: (Long) -> Unit,
    ): Prefetched? = coroutineScope {
        val permits = Semaphore(HlsPrefetcher.PARALLELISM)
        val perStream = ConcurrentHashMap<String, Long>()
        val report = { key: String, bytes: Long ->
            perStream[key] = bytes
            onBytes(perStream.values.sum())
        }

        val videoTask = async {
            prefetcher.prefetch(videoUrl, video.headers, media, File(workspace, "video"), permits) {
                report("video", it)
            }
        }
        val audioTasks = video.audioTracks.mapIndexed { index, track ->
            async {
                val playlist = runCatching { fetchText(track.url, video.headers) }.getOrNull()
                    ?: return@async track to null
                val local = prefetcher.prefetch(
                    track.url,
                    video.headers,
                    playlist,
                    File(workspace, "audio$index"),
                    permits,
                ) { report("audio$index", it) }
                track to local
            }
        }

        val localVideo = videoTask.await() ?: return@coroutineScope null
        val audio = audioTasks.awaitAll().map { (track, local) ->
            local?.let { track.copy(url = it.playlist.absolutePath) } ?: track
        }
        Prefetched(localVideo, audio)
    }

    private fun fetchText(url: String, headers: Headers?): String {
        val request = Request.Builder().url(url).apply { headers?.let { headers(it) } }.build()
        return networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("Empty response")
        }
    }

    private fun streamToFile(
        input: InputStream,
        head: ByteArray,
        headLength: Int,
        total: Long,
        target: UniFile,
        episodeId: Long,
    ) {
        val rate = DownloadRate()
        target.openOutputStream().use { output ->
            input.use {
                output.write(head, 0, headLength)
                var downloaded = headLength.toLong()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = it.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    setProgress(
                        episodeId,
                        AnimeDownloadProgress(
                            downloadedBytes = downloaded,
                            estimatedTotalBytes = total.takeIf { size -> size > 0 },
                            bytesPerSecond = rate.sample(downloaded),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Fills as much of [buffer] as the stream will give before its end.
     *
     * A single `read` is allowed to return one byte even when thousands are coming, and the
     * sniff below has to see the whole first line to decide anything.
     */
    private fun InputStream.readAtMost(buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = read(buffer, filled, buffer.size - filled)
            if (read == -1) break
            filled += read
        }
        return filled
    }

    private fun setProgress(episodeId: Long, progress: AnimeDownloadProgress) =
        _progress.update { it + (episodeId to progress) }

    private fun clearProgress(episodeId: Long) =
        _progress.update { it - episodeId }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        /** Matches tachiyomi.source.local.anime.LocalAnimeSource.ID without depending on it. */
        private const val LOCAL_ANIME_SOURCE_ID = 0L

        /** Enough to hold the first line of a playlist whatever whitespace precedes it. */
        private const val SNIFF_BYTES = 256

        /**
         * What a remuxed episode ends up as, whatever the playlist was called.
         *
         * Matroska rather than mp4 because an episode arrives in pieces — picture here,
         * Japanese and English audio there, eight subtitle languages somewhere else — and mkv
         * is the container that takes all of them side by side without converting anything.
         */
        private const val REMUXED_EXTENSION = "mkv"

        private const val PLAYLIST_MARKER = "#EXTM3U"

        /**
         * Whether the response is a playlist rather than a video.
         *
         * Decided on the bytes, not on the url or the content type: sources serve playlists
         * from paths ending in .mp4 and label them as octet-streams, and every m3u8 in
         * existence starts with this line.
         */
        fun looksLikePlaylist(head: ByteArray, length: Int): Boolean =
            String(head, 0, length, Charsets.UTF_8).trimStart().startsWith(PLAYLIST_MARKER)

        /**
         * The file extension for a progressive download.
         *
         * Taken from the path and only the path. Reading the url to its last dot used to pick
         * up whatever followed, so a perfectly ordinary `video.mp4?token=abc` was saved with
         * an extension of `mp4?`.
         */
        fun extensionFor(url: String): String {
            val path = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
            val extension = path.substringAfterLast('.', "")
            return extension.takeIf { it.isNotBlank() && it.length <= 4 && it.all(Char::isLetterOrDigit) }
                ?: REMUXED_EXTENSION
        }
    }
}
