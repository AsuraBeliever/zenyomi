package eu.kanade.tachiyomi.data.download.anime

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
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
    private val remuxer: StreamRemuxer,
    private val prefetcher: SegmentPrefetcher,
    private val sizer: StreamSizer,
    private val probe: StreamProbe,
) {

    private val _progress = MutableStateFlow(emptyMap<Long, AnimeDownloadProgress>())

    /** Episode id to how far along it is, for rows that are downloading right now. */
    val progress: StateFlow<Map<Long, AnimeDownloadProgress>> = _progress.asStateFlow()

    /**
     * The download in flight, so it can be called off.
     *
     * A map rather than a single reference because nothing here promises the queue will
     * always run one at a time, and a cancel that hit the wrong episode would be worse than
     * one that did nothing.
     */
    private val active = ConcurrentHashMap<Long, Deferred<Result<Unit>>>()

    /**
     * Stops the download of [episodeId] if it is the one running.
     *
     * @return whether there was one to stop. The queue is the caller's to tidy either way:
     * an episode still waiting its turn has no download to cancel, only a place in line.
     *
     * What has landed so far is thrown away rather than kept. A half-written video reads as a
     * finished download to every other part of the app, and resuming is not something this
     * downloader can do.
     */
    fun cancel(episodeId: Long): Boolean = active.remove(episodeId)?.let {
        it.cancel()
        true
    } ?: false

    /** Stops whatever is running, for "cancel everything". */
    fun cancelAll() = active.keys.toList().forEach { cancel(it) }

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
    ): Result<Unit> = supervisorScope {
        // Its own child job, kept where [cancel] can reach it, and a *supervisor* scope so
        // calling one episode off does not take the queue down with it. Cancelling the job
        // that runs the fetch is the only thing that actually stops a download: dropping the
        // row from the queue, which is all cancelling used to do, left the video coming down
        // to a file nobody was watching any more.
        val task = async(Dispatchers.IO) { fetch(anime, source, episode, video, quality, expectedBytes) }
        active[episode.id] = task
        try {
            task.await()
        } catch (e: CancellationException) {
            // Two different things throw this: the viewer cancelling this episode, and the
            // whole worker going down. Only the first is something to carry on from.
            ensureActive()
            // Let the fetch finish unwinding before sweeping up after it, so the file is not
            // deleted out from under something still writing to it.
            task.join()
            // Whatever landed before the stop goes with it: see [cancel].
            provider.findAnyEpisodeFile(anime, source, episode)?.delete()
            Result.failure(e)
        } finally {
            active.remove(episode.id, task)
            clearProgress(episode.id)
        }
    }

    private suspend fun fetch(
        anime: Anime,
        source: AnimeSource,
        episode: Episode,
        video: Video,
        quality: Int?,
        expectedBytes: Long?,
    ): Result<Unit> {
        return runCatching {
            // Whatever is already there for this episode goes first, including a playlist
            // left by a version of this that did not know the difference. Otherwise the
            // storage framework keeps it and names the new file "episode (1).mp4".
            provider.findAnyEpisodeFile(anime, source, episode)?.delete()

            val request = Request.Builder()
                .url(video.videoUrl)
                .apply { video.headers?.let { headers(it) } }
                .build()

            var written: UniFile? = null

            val manifest = networkHelper.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response")
                val input = body.byteStream()

                val head = ByteArray(StreamSniffer.SNIFF_BYTES)
                val headLength = input.readAtMost(head)

                when (val delivery = StreamSniffer.classify(head, headLength)) {
                    // Said out loud instead of saved. An expired link and a login wall both
                    // arrive as a perfectly successful response with a page in it, and writing
                    // that page to the downloads folder is how an episode came to look
                    // downloaded when nothing of it was there.
                    is StreamDelivery.Unusable -> error(delivery.reason)
                    // Small enough to hold: a few hundred lines of text. Read out so the
                    // connection is closed before ffmpeg opens its own.
                    is StreamDelivery.Manifest -> Manifest(
                        hls = delivery.hls,
                        text = String(head, 0, headLength, Charsets.UTF_8) + input.readBytes().decodeToString(),
                    )
                    // A real video, and its first bytes are already in hand: write them and
                    // keep going rather than asking for them a second time.
                    StreamDelivery.Container -> {
                        val target = provider.createEpisodeFile(anime, source, episode, extensionFor(video.videoUrl))
                            ?: error("Could not create the download file")
                        written = target
                        streamToFile(input, head, headLength, body.contentLength(), target, episode.id)
                        null
                    }
                }
            }

            if (manifest != null) {
                val target = provider.createEpisodeFile(anime, source, episode, REMUXED_EXTENSION)
                    ?: error("Could not create the download file")
                written = target
                if (manifest.hls) {
                    downloadPlaylist(episode, video, quality, expectedBytes, manifest.text, target)
                } else {
                    downloadManifest(episode, video, quality, expectedBytes, manifest.text, target)
                }
            }

            // Only now is it an episode rather than a download in flight — and only if it is
            // one. See [verify].
            verify(written ?: error("Nothing was downloaded"))
            provider.finish(written)
            clearProgress(episode.id)
        }.onFailure {
            // A cancellation is not a failure: the file is swept and the news is broken by
            // whoever asked for it, not by an error in the log.
            if (it is CancellationException) throw it
            // Said out loud. A download that fails silently and leaves a notification with
            // no reason in it is one nobody can diagnose, here or from a bug report.
            logcat(LogPriority.ERROR, it) { "Could not download ${episode.name}" }
            // A partial file would read as a finished download, so it goes.
            provider.findAnyEpisodeFile(anime, source, episode)?.delete()
            clearProgress(episode.id)
        }
    }

    /** A manifest and which kind it is, on its way from the sniff to the path that handles it. */
    private data class Manifest(val hls: Boolean, val text: String)

    /**
     * An HLS stream, fetched by this app rather than by ffmpeg.
     *
     * The one format with a reader of its own, and it earns it: reading the playlist is what
     * lets the segments be pulled down several at a time, which is the difference between a
     * four-minute download and a seventy-second one. Everything else goes through
     * [downloadThroughFfmpeg], which is slower and needs no reader at all.
     */
    private suspend fun downloadPlaylist(
        episode: Episode,
        video: Video,
        quality: Int?,
        expectedBytes: Long?,
        playlist: String,
        target: UniFile,
    ) {
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
        val picture = expectedBytes
            ?: sizer.sizeOfMedia(chosen?.url ?: video.videoUrl, video.headers, chosen?.bandwidth, media)
        // The audio the source keeps apart from the picture is fetched too and lands
        // in the same file, so it belongs in the total — including when the number came
        // from the dialog, which measures the picture alone so that two qualities can be
        // compared without waiting on tracks that weigh the same in both. Leaving it out
        // is what made a download read "200 MB of 160 MB": the bytes counted every
        // stream and the total counted one.
        val estimated = picture?.plus(sideCarBytes(video))

        val report = reporter(episode.id, estimated)

        // The segments are fetched here, several at a time, rather than left to
        // ffmpeg, which asks for them one after another. See [SegmentPrefetcher].
        val workspace = prefetcher.workspace(episode.id)
        // The whole of it inside the cleanup, not just the muxing: a download called
        // off during the fetch never reaches the muxing, and used to leave its
        // workspace behind in the cache for good.
        try {
            val fetched = try {
                prefetchAll(chosen?.url ?: video.videoUrl, media, video, workspace, report)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Falling back to letting ffmpeg fetch the segments itself is slower
                // but works; being cancelled is neither, and must not land here.
                logcat(LogPriority.WARN, e) { "Could not prefetch ${episode.name}" }
                null
            }

            remuxer.remux(
                videoUrl = fetched?.video?.playlist?.absolutePath ?: chosen?.url ?: video.videoUrl,
                headers = video.headers,
                audioTracks = fetched?.audio ?: video.audioTracks,
                subtitleTracks = video.subtitleTracks,
                target = target,
                // One quality by this point, whether it was resolved above or handed over as
                // the only one there was, so there is nothing to leave out.
                selection = StreamRemuxer.Selection.Everything,
                hlsInput = true,
                // Nothing to report while muxing when the bytes are already on disk;
                // ffmpeg is only copying them into a container at that point.
                onBytes = if (fetched == null) report else { _ -> },
            )
        } finally {
            workspace.deleteRecursively()
        }
    }

    /**
     * Any manifest that is not HLS.
     *
     * Two ways to do it, in order of preference. If there is a reader for this format — DASH
     * has one — the segments are listed and fetched here, several at a time, exactly as the
     * playlist path does. If there is not, or the reader declines, or a segment cannot be had,
     * ffmpeg fetches the manifest itself: slower, and right without knowing the format.
     *
     * That ordering is the arrangement in ADR-0006 in one method. Correctness never depends on
     * a reader existing; speed is what a reader buys.
     */
    private suspend fun downloadManifest(
        episode: Episode,
        video: Video,
        quality: Int?,
        expectedBytes: Long?,
        manifest: String,
        target: UniFile,
    ) {
        val presentation = DashManifest.read(manifest, video.videoUrl)
        if (presentation != null &&
            downloadDash(episode, video, quality, expectedBytes, presentation, target)
        ) {
            return
        }
        downloadThroughFfmpeg(episode, video, quality, expectedBytes, target)
    }

    /**
     * A DASH stream, fetched by this app rather than by ffmpeg.
     *
     * Its segments join end to end into an ordinary fragmented MP4, one file per stream, so
     * there is no playlist to rewrite: ffmpeg is handed the picture as input 0 and each sound
     * track beside it, and only has to mux them.
     *
     * @return whether it worked. False sends the caller back to ffmpeg, which is slower and
     * fetches everything again — the same bargain the playlist path makes, and the reason
     * anything unfamiliar is declined early rather than half-done.
     */
    private suspend fun downloadDash(
        episode: Episode,
        video: Video,
        quality: Int?,
        expectedBytes: Long?,
        presentation: DashPresentation,
        target: UniFile,
    ): Boolean {
        val chosen = presentation.videos.pickByHeight(quality) ?: return false
        val audio = presentation.streams.filter { it.kind == DashStream.Kind.AUDIO }
        // The bitrates the manifest declares, over the length it declares. Better than asking
        // ffprobe, and free: this is arithmetic on numbers already read.
        val picture = expectedBytes ?: presentation.estimatedBytes(chosen)
        val estimated = picture?.plus(presentation.audioBytes)?.plus(sideCarBytes(video))
        val report = reporter(episode.id, estimated)

        val workspace = prefetcher.workspace(episode.id)
        try {
            val fetched = fetchDash(chosen, audio, video.headers, workspace, report) ?: return false
            remuxer.remux(
                videoUrl = fetched.video.absolutePath,
                headers = video.headers,
                // The sound out of the manifest, then whatever the source keeps apart from it.
                audioTracks = fetched.audio + video.audioTracks,
                subtitleTracks = video.subtitleTracks,
                target = target,
                // One stream per file by this point; there is nothing to leave out.
                selection = StreamRemuxer.Selection.Everything,
                hlsInput = false,
                // Nothing to report while muxing: the bytes are already on disk.
                onBytes = { _ -> },
            )
            return true
        } finally {
            workspace.deleteRecursively()
        }
    }

    /** A DASH stream's picture and sound, all now on local disk. */
    private data class PrefetchedDash(val video: File, val audio: List<Track>)

    /**
     * Fetches the picture and every sound track, at the same time and within one budget.
     *
     * The same arrangement [prefetchAll] makes for HLS, and for the same reason: a stream that
     * queues behind another stream's several hundred requests is concurrent on paper and
     * consecutive in fact.
     *
     * @return null if any of them could not be had. All of it or none: an episode missing a
     * language is worse than one that took the slow path.
     */
    private suspend fun fetchDash(
        picture: DashStream,
        sound: List<DashStream>,
        headers: Headers?,
        workspace: File,
        onBytes: (Long) -> Unit,
    ): PrefetchedDash? = coroutineScope {
        val budget = Semaphore(SegmentPrefetcher.PARALLELISM)
        val perStream = ConcurrentHashMap<String, Long>()
        val report = { key: String, bytes: Long ->
            perStream[key] = bytes
            onBytes(perStream.values.sum())
        }

        val videoTask = async {
            prefetcher.concatenate(
                picture.urls,
                headers,
                File(workspace, "video"),
                SegmentPrefetcher.share(budget, SegmentPrefetcher.VIDEO_SHARE),
            ) { report("video", it) }
        }
        val audioTasks = sound.mapIndexed { index, stream ->
            async {
                prefetcher.concatenate(
                    stream.urls,
                    headers,
                    File(workspace, "audio$index"),
                    SegmentPrefetcher.share(budget, SegmentPrefetcher.AUDIO_SHARE),
                ) { report("audio$index", it) }
                    ?.let { Track(it.absolutePath, stream.lang.orEmpty()) }
            }
        }

        val localPicture = videoTask.await() ?: return@coroutineScope null
        val tracks = audioTasks.awaitAll()
        if (tracks.any { it == null }) return@coroutineScope null
        PrefetchedDash(localPicture, tracks.filterNotNull())
    }

    /**
     * Any other manifest, fetched by ffmpeg.
     *
     * The general path, and the reason a source moving from HLS to DASH is not a source
     * needing code. ffmpeg opens the manifest, and what has to be decided here is the same
     * thing the HLS path decides for itself: which quality, out of the several a manifest
     * usually carries. It matters for more than the file size — ffmpeg fetches every stream
     * something maps and discards the rest, so an unmapped 1080p representation is one that
     * never comes down the wire.
     *
     * ffprobe is what answers "which qualities are in here", for this format and for every
     * other one it knows, which is the whole point of asking it rather than writing a reader.
     */
    private suspend fun downloadThroughFfmpeg(
        episode: Episode,
        video: Video,
        quality: Int?,
        expectedBytes: Long?,
        target: UniFile,
    ) {
        val probed = probe.probe(video.videoUrl, video.headers)
        val chosen = probed?.videos?.pickByHeight(quality)
        // Bitrate times length, the same estimate the HLS path makes from the numbers in a
        // playlist. The picture from the dialog if it was shown one, plus the sound in the
        // manifest and the sound beside it, because all of it ends up in the one file.
        val picture = expectedBytes ?: probed?.pictureBytes(chosen)
        val estimated = picture
            ?.plus(probed?.audioBytes ?: 0L)
            ?.plus(sideCarBytes(video))

        remuxer.remux(
            videoUrl = video.videoUrl,
            headers = video.headers,
            audioTracks = video.audioTracks,
            subtitleTracks = video.subtitleTracks,
            target = target,
            selection = when (chosen) {
                // Nothing came back from the probe: ffmpeg is opening something ffprobe could
                // not, which it might still manage. Taking everything is what it used to do
                // and is better than refusing.
                null -> StreamRemuxer.Selection.Everything
                else -> StreamRemuxer.Selection.OneVideo(chosen.index, probed?.audioStreams ?: 0)
            },
            hlsInput = false,
            onBytes = reporter(episode.id, estimated),
        )
    }

    /**
     * Refuses to call something an episode until it is one.
     *
     * The net under every path above, and the lesson of the two ways this has gone wrong: a
     * playlist saved as an mp4, and a DASH manifest saved as an mp4 after the playlist case
     * was fixed. Both left a few kilobytes on disk wearing the downloaded tick, and both were
     * only found when somebody tried to watch offline. Whatever route the bytes took, and
     * whatever format nobody has thought of yet, the file has to contain a video and have a
     * length before it is finished — so the next surprise is a failed download, which is
     * visible, rather than an empty one, which is not.
     */
    private suspend fun verify(file: UniFile) {
        when (probe.isPlayable(file)) {
            false -> error("what arrived is not a playable video")
            // The check could not be made. Not a reason to throw away a download that may well
            // be fine — but worth saying, because a check that silently stops checking is
            // worse than no check.
            null -> logcat(LogPriority.WARN) { "Could not check what was downloaded for ${file.name}" }
            true -> Unit
        }
    }

    /** The tracks the source keeps apart from the picture, which land in the same file. */
    private suspend fun sideCarBytes(video: Video): Long =
        video.audioTracks.sumOf { sizer.sizeOfMedia(it.url, video.headers, null) ?: 0L }

    /** The progress callback both manifest paths report through. */
    private fun reporter(episodeId: Long, estimated: Long?): (Long) -> Unit {
        val rate = DownloadRate()
        return { bytes ->
            setProgress(
                episodeId,
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
    }

    /** The video and its separate audio tracks, all now on local disk. */
    private data class Prefetched(val video: SegmentPrefetcher.Local, val audio: List<Track>)

    /**
     * Fetches the picture and every audio track that goes with it, at the same time.
     *
     * One connection budget across all of them rather than one each: the audio streams are a
     * tenth the size of the video and finish early, and giving each its own budget would only
     * mean more connections to the same four hosts for no gain.
     *
     * Sharing that budget is not the same as queueing for it, which is what this used to do
     * and why a download ran fast and then crawled. Every stream threw all of its segments at
     * one semaphore at once, and a semaphore hands permits out in the order they were asked
     * for: the video got its hundreds of requests in first, so the audio's waited behind every
     * last one of them. The two streams were nominally concurrent and in practice consecutive
     * — picture at full speed, then a long slow tail of sound, which is exactly what it looked
     * like. So each stream now holds a cap of its own and only then queues for the shared
     * budget, and no stream can have more than its cap of requests waiting in that queue.
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
        val budget = Semaphore(SegmentPrefetcher.PARALLELISM)
        val perStream = ConcurrentHashMap<String, Long>()
        val report = { key: String, bytes: Long ->
            perStream[key] = bytes
            onBytes(perStream.values.sum())
        }

        val videoTask = async {
            prefetcher.prefetch(
                videoUrl,
                video.headers,
                media,
                File(workspace, "video"),
                SegmentPrefetcher.share(budget, SegmentPrefetcher.VIDEO_SHARE),
            ) { report("video", it) }
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
                    SegmentPrefetcher.share(budget, SegmentPrefetcher.AUDIO_SHARE),
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

    /**
     * Copies a plain video file to disk.
     *
     * Suspending, and checking as it goes, only so that it can be stopped: a `read`/`write`
     * loop is blocking from end to end, and a coroutine cancelled in the middle of one carries
     * on to the last byte. A cancelled download of an mp4 kept downloading.
     */
    private suspend fun streamToFile(
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
                    currentCoroutineContext().ensureActive()
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

        /**
         * What a remuxed episode ends up as, whatever the manifest was called.
         *
         * Matroska rather than mp4 because an episode arrives in pieces — picture here,
         * Japanese and English audio there, eight subtitle languages somewhere else — and mkv
         * is the container that takes all of them side by side without converting anything.
         */
        private const val REMUXED_EXTENSION = "mkv"

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
