package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.MediaInformationSession
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import kotlin.coroutines.resume

/**
 * Asks ffmpeg what is inside something, instead of working it out format by format.
 *
 * This is the piece that lets the downloader stop caring which streaming format a source
 * speaks. Reading an m3u8 by hand is worth it — it is what makes the segments fetchable in
 * parallel — but writing a reader for DASH, and then for Smooth Streaming, and then for
 * whatever a source switches to next, is the per-format treadmill this app is trying to get
 * off. ffprobe already reads all of them, it is already in the build, and it answers the same
 * two questions for every one of them:
 *
 *  - what qualities does this manifest offer, and how big is each — for the dialog;
 *  - is this file actually a video — for the check after a download.
 *
 * Both answers cost a round trip, so neither is asked on a path where somebody is waiting for
 * a button.
 */
@Inject
@SingleIn(AppScope::class)
class StreamProbe(
    private val context: Context,
) {

    /** What ffprobe found. Durations and bitrates are what the sizes are worked out from. */
    data class Media(
        val durationSeconds: Double?,
        val videos: List<VideoStream>,
        val audioStreams: Int,
        val audioBitrate: Long,
    ) {
        /**
         * Roughly what a stream at [bitrate] comes to over this input's length.
         *
         * Bitrate times duration, the same arithmetic the HLS path does with the numbers a
         * playlist declares, and just as much an estimate. Null when either number is missing:
         * no size at all beats one that was made up.
         */
        fun bytesOf(bitrate: Long?): Long? {
            val seconds = durationSeconds?.takeIf { it > 0 } ?: return null
            val bits = bitrate?.takeIf { it > 0 } ?: return null
            return (bits / BITS_PER_BYTE * seconds).toLong()
        }

        /** What one quality weighs, the sound left out — the number the dialog compares. */
        fun pictureBytes(stream: VideoStream?): Long? = bytesOf(stream?.bitrate)

        /** What the sound this input carries itself weighs. It lands in the same file. */
        val audioBytes: Long? get() = bytesOf(audioBitrate.takeIf { it > 0 })
    }

    /**
     * One quality the input offers.
     *
     * @param index this stream's place among the input's *video* streams, which is what
     * `-map 0:v:N` counts. Not its overall stream index.
     */
    data class VideoStream(
        val index: Int,
        val width: Int?,
        val height: Int?,
        val bitrate: Long?,
    )

    /**
     * What [url] contains, or null if ffprobe could not open it.
     *
     * Costs a real request and some of the stream, so it belongs in a download that is about
     * to run for minutes, or behind a dialog somebody asked for — not on a tap.
     */
    suspend fun probe(url: String, headers: Headers?): Media? {
        val arguments = buildList {
            addAll(REPORT_JSON)
            // Per input, like the muxing: without them a request for the manifest is a
            // stranger's request, and the hosts that check Referer answer 403.
            if (url.startsWith("http")) {
                headers?.takeIf { it.size > 0 }?.let {
                    add("-headers")
                    add(it.joinToString("") { (name, value) -> "$name: $value\r\n" })
                }
            }
            add("-i")
            add(url)
        }
        return run(arguments.toTypedArray(), NETWORK_TIMEOUT_MS)?.toMedia()
    }

    /**
     * Whether [file] is a video somebody could watch.
     *
     * The net under everything else. A downloader that decides for itself what a url contained
     * can be wrong, and the way it used to be wrong — writing a manifest to disk and marking it
     * downloaded — is the worst kind: the episode looks like it is there until the network goes
     * away. Whatever route the bytes took, the result has to be a container with a video in it
     * and a length, or it was not a download.
     *
     * The format check is the one that catches the failure this was written for. A manifest
     * saved to disk still probes as *something* — ffprobe opens a local `.mpd` quite happily
     * and goes off to the internet to describe what it points at — so "it has a video stream"
     * is not enough on its own. What decides it is that ffprobe names the format `dash` or
     * `hls` rather than a container: the downloads folder holds episodes, never directions to
     * one.
     *
     * @return null when the check could not be made at all, which is not the same as failing
     * it. A download that worked must not be thrown away because ffprobe could not be handed a
     * path to look at.
     *
     * Local file, so no network and no waiting: ffprobe reads the header and stops.
     */
    suspend fun isPlayable(file: UniFile): Boolean? {
        val path = pathFor(file) ?: return null
        val information = run((REPORT_JSON + listOf("-i", path)).toTypedArray(), LOCAL_TIMEOUT_MS)
            ?: return false
        if (information.format?.lowercase() in MANIFEST_FORMATS) return false
        val media = information.toMedia()
        return media.videos.isNotEmpty() && (media.durationSeconds ?: 0.0) > 0
    }

    private fun pathFor(file: UniFile): String? = runCatching {
        val uri = file.uri
        when (uri.scheme) {
            "content" -> FFmpegKitConfig.getSafParameterForRead(context, uri)
            else -> uri.path ?: uri.toString()
        }
    }.getOrNull()

    private suspend fun run(arguments: Array<String>, timeoutMs: Int): MediaInformation? =
        suspendCancellableCoroutine { continuation ->
            val session = MediaInformationSession.create(arguments) { completed ->
                if (continuation.isActive) continuation.resume(completed.mediaInformation)
            }
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
            runCatching { FFmpegKitConfig.asyncGetMediaInformationExecute(session, timeoutMs) }
                .onFailure {
                    logcat(LogPriority.WARN, it) { "Could not probe the stream" }
                    if (continuation.isActive) continuation.resume(null)
                }
        }

    private fun MediaInformation.toMedia(): Media {
        var videoIndex = 0
        val videos = mutableListOf<VideoStream>()
        var audioStreams = 0
        var audioBitrate = 0L
        streams.orEmpty().forEach { stream ->
            when (stream.type) {
                VIDEO -> {
                    videos += VideoStream(
                        index = videoIndex++,
                        width = stream.width?.toInt(),
                        height = stream.height?.toInt(),
                        bitrate = stream.bitrate?.toLongOrNull(),
                    )
                }
                AUDIO -> {
                    audioStreams++
                    audioBitrate += stream.bitrate?.toLongOrNull() ?: 0L
                }
                else -> Unit
            }
        }
        return Media(
            durationSeconds = duration?.toDoubleOrNull(),
            videos = videos,
            // Side-car tracks aside: these are the ones the input carries itself, and the
            // muxing has to know how many so a track's name lands on the right stream.
            audioStreams = audioStreams,
            // Summed, because all of them end up in the same file.
            audioBitrate = audioBitrate,
        )
    }

    private companion object {
        /** Say what is in there, as json, and nothing else. */
        val REPORT_JSON = listOf(
            "-v",
            "error",
            "-hide_banner",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
        )

        const val VIDEO = "video"
        const val AUDIO = "audio"
        const val BITS_PER_BYTE = 8

        /**
         * What ffprobe calls the streaming formats, as opposed to containers.
         *
         * A file in the downloads folder that probes as one of these is a manifest that was
         * saved instead of being followed.
         */
        val MANIFEST_FORMATS = setOf("dash", "hls", "applehttp", "ism", "m3u8")

        /** Opening a manifest means fetching it and the head of a segment. */
        const val NETWORK_TIMEOUT_MS = 30_000

        /** Reading a local header. Anything near this means something is wrong. */
        const val LOCAL_TIMEOUT_MS = 10_000
    }
}

/**
 * The video stream to fetch for a requested [height].
 *
 * The exact one when it is there, otherwise the closest below it, and the smallest when even
 * that does not exist — the same rule the HLS variants follow, for the same reason: somebody
 * who asked for 720p wants an episode that fits the phone, not one four times the size. Null
 * only for an input with no video at all, which is not something to download.
 */
fun List<StreamProbe.VideoStream>.pickByHeight(height: Int?): StreamProbe.VideoStream? {
    if (isEmpty()) return null
    val best = maxByOrNull { it.height ?: 0 }
    if (height == null) return best
    return firstOrNull { it.height == height }
        ?: filter { (it.height ?: 0) < height }.maxByOrNull { it.height ?: 0 }
        ?: minByOrNull { it.height ?: Int.MAX_VALUE }
}
