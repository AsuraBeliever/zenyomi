package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.model.Track
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Turns a stream into a real file on disk.
 *
 * Most anime sources do not serve a video file: they serve an m3u8 playlist, which is a few
 * kilobytes of text naming a few hundred segments that are still on the internet. Fetching
 * that url the way you would fetch an mp4 writes the *playlist* to the downloads folder — it
 * finishes in a second, reports success, and leaves nothing of the episode on the device. It
 * only looks downloaded until the network goes away.
 *
 * Sources also hand audio and subtitles over separately from the picture, which is how the
 * player ends up offering Japanese and English on the same episode. Those are their own urls
 * and their own downloads, and an episode saved without them is a silent film.
 *
 * ffmpeg is what puts all of it back together, and it is already in the build: the player
 * links against it. Nothing is re-encoded — the pieces are copied into one Matroska file as
 * they arrive, so this costs bandwidth and disk, not battery.
 */
@Inject
@SingleIn(AppScope::class)
class HlsRemuxer(
    private val context: Context,
) {

    /**
     * Writes [videoUrl] and every track that belongs with it into [target].
     *
     * @param onBytes called with how many bytes are on disk so far. Bytes rather than a
     * percentage because ffmpeg is the only thing that knows, and the caller is the only
     * thing that knows how big the episode was supposed to be.
     * @throws IllegalStateException if ffmpeg fails, carrying its last words.
     */
    suspend fun remux(
        videoUrl: String,
        headers: Headers?,
        audioTracks: List<Track>,
        subtitleTracks: List<Track>,
        target: UniFile,
        onBytes: (Long) -> Unit,
    ) {
        val output = ffmpegPathFor(target.uri)

        try {
            run(buildArguments(videoUrl, headers, audioTracks, subtitleTracks, output), onBytes)
        } catch (e: IllegalStateException) {
            // One dead subtitle url fails the whole command, and an episode you can watch
            // without subtitles beats an episode you cannot watch. The picture and the sound
            // are not negotiable, so only the subtitles are dropped.
            if (subtitleTracks.isEmpty()) throw e
            logcat(LogPriority.WARN, e) { "Retrying the download without its subtitles" }
            run(buildArguments(videoUrl, headers, audioTracks, emptyList(), output), onBytes)
        }
    }

    private suspend fun run(arguments: Array<String>, onBytes: (Long) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            val session: FFmpegSession = FFmpegKit.executeWithArgumentsAsync(
                arguments,
                { completed ->
                    val code = completed.returnCode
                    when {
                        ReturnCode.isSuccess(code) -> continuation.resume(Unit)
                        // A cancelled download is not a failed one. The coroutine is already
                        // on its way out; resuming with an error would report a problem the
                        // user caused on purpose.
                        ReturnCode.isCancel(code) -> Unit
                        else -> continuation.resumeWithException(
                            IllegalStateException(ffmpegFailure(completed.allLogsAsString)),
                        )
                    }
                },
                { },
                // What ffmpeg has written, which with a straight copy is what it has fetched.
                { statistics -> onBytes(statistics.size) },
            )
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }
    }

    /**
     * Where ffmpeg should write.
     *
     * The downloads folder is usually one the user picked through the storage framework, and
     * what comes back from it is a `content://` handle rather than a path. ffmpeg cannot open
     * one, so it is handed a file descriptor instead, which is what ffmpeg-kit's `saf:`
     * protocol is for. A plain `file://` folder needs none of that.
     */
    private fun ffmpegPathFor(uri: Uri): String = when (uri.scheme) {
        "content" -> FFmpegKitConfig.getSafParameterForWrite(context, uri)
        else -> uri.path ?: uri.toString()
    }

    private fun buildArguments(
        videoUrl: String,
        headers: Headers?,
        audioTracks: List<Track>,
        subtitleTracks: List<Track>,
        output: String,
    ): Array<String> = buildList {
        add("-y")

        // Input 0 is the picture, and whatever sound the stream already carries. It reached
        // here because its first bytes said it was a playlist, so there is no guessing.
        addInput(videoUrl, headers, isPlaylist = true)
        // Then one input per track the source keeps apart. Their order here is their index
        // in the maps below.
        (audioTracks + subtitleTracks).forEach { addInput(it.url, headers, isPlaylist = looksLikePlaylistUrl(it.url)) }

        // Everything from the video input: for the media playlist this is handed, that is the
        // picture plus any audio muxed into the same segments.
        add("-map")
        add("0")
        (audioTracks + subtitleTracks).forEachIndexed { index, _ ->
            add("-map")
            add("${index + 1}")
        }

        // Copy, never re-encode. Transcoding a 24-minute episode on a phone is minutes of
        // full load for a result nobody asked for, and Matroska takes every codec these
        // streams arrive in — which is why the file is an mkv and not an mp4. An mp4 cannot
        // hold the subtitle formats sources use without converting and flattening them.
        add("-c")
        add("copy")

        // What the player shows in the track pickers. The source names its tracks in plain
        // words rather than language codes — "Japanese", not "jpn" — so they go in as titles,
        // which is the field that takes free text.
        audioTracks.forEachIndexed { index, track ->
            add("-metadata:s:a:$index")
            add("title=${track.lang}")
        }
        subtitleTracks.forEachIndexed { index, track ->
            add("-metadata:s:s:$index")
            add("title=${track.lang}")
        }

        add("-f")
        add("matroska")
        add(output)
    }.toTypedArray()

    private fun MutableList<String>.addInput(url: String, headers: Headers?, isPlaylist: Boolean) {
        // Per input, because ffmpeg applies these to whichever -i follows. Without them a
        // segment request is a stranger's request, and the hosts that check Referer say 403.
        //
        // Only for one that is actually fetched: a stream whose segments were already
        // downloaded is handed to ffmpeg as a path, and the file protocol has no use for HTTP
        // headers — it rejects the whole command with "Option headers not found".
        if (url.startsWith("http")) {
            headers?.takeIf { it.size > 0 }?.let {
                add("-headers")
                add(it.joinToString("") { (name, value) -> "$name: $value\r\n" })
            }
        }
        if (isPlaylist) {
            // Segments are not always named like video. The playlist that prompted this fix
            // serves its parts as `000.jpg`, and ffmpeg's hls demuxer refuses unknown
            // extensions by default — it would open the playlist and then download nothing.
            //
            // Only for a playlist: this belongs to the hls demuxer, and ffmpeg rejects the
            // whole command with "Option allowed_extensions not found" if it is handed for an
            // input it does not demux that way. A subtitle file is one of those.
            add("-allowed_extensions")
            add("ALL")
        }
        add("-protocol_whitelist")
        add("file,http,https,tcp,tls,crypto")
        add("-i")
        add(url)
    }

    companion object {
        private val PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8")

        /**
         * Whether a side-car track is itself an HLS playlist.
         *
         * Guessing from the url, which is unreliable in general — but not here. These urls
         * come from inside a playlist, where a track delivered as HLS is always named as the
         * m3u8 it is, and anything else is the subtitle or audio file itself.
         */
        fun looksLikePlaylistUrl(url: String): Boolean = url
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .lowercase() in PLAYLIST_EXTENSIONS

        /**
         * The last thing ffmpeg said, rather than all of it.
         *
         * Its logs run to hundreds of lines of stream metadata, and the reason a download
         * failed is on the final one. The whole thing in a message helps nobody.
         */
        private fun ffmpegFailure(logs: String?): String {
            val lastLine = logs?.trim()?.lines()?.lastOrNull { it.isNotBlank() }
            return "ffmpeg could not write the episode" + lastLine?.let { ": $it" }.orEmpty()
        }
    }
}
