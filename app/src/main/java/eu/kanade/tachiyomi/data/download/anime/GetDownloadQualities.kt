package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.episode.model.Episode

/**
 * One quality an episode can be downloaded at.
 *
 * @param height the vertical resolution, or null when there is nowhere to read a number from.
 * The label is what the viewer sees either way.
 * @param estimatedBytes roughly what the picture at this quality weighs, and always null
 * unless somebody asked to be shown sizes — measuring costs network. Shown as an estimate,
 * never as a fact.
 *
 * The picture alone, deliberately: this number exists so two qualities can be compared, and
 * audio a source keeps in a separate stream is the same weight whichever one is picked, so
 * adding it to every row moves them all equally and only costs a round trip per track while
 * somebody waits on the dialog. The downloader adds it back for the progress bar, which is
 * measuring something else — everything that is being fetched.
 */
data class DownloadQuality(
    val height: Int?,
    val label: String,
    val estimatedBytes: Long? = null,
)

/**
 * What an episode can be downloaded at.
 *
 * Three shapes to cover, because sources disagree on where the qualities live. Some return one
 * entry per quality and the app picks between them. Others return a single stream whose
 * qualities are listed inside it, in a master playlist, so the list has to be opened. And some
 * return a manifest of another format, where the answer comes from a reader if there is one for
 * it — DASH has one — and otherwise from ffprobe, which can answer it for any format ffmpeg
 * opens, at the cost of a round trip.
 *
 * Sizes are a second and dearer question, answered only when asked for. Finding out what
 * exists is one round trip the download was going to make anyway; measuring every quality is
 * a playlist and five HEAD requests apiece, and doing both on the tap put a long, silent wait
 * between pressing download and anything happening.
 */
@Inject
class GetDownloadQualities(
    private val getEpisodeVideos: GetEpisodeVideos,
    private val sizer: StreamSizer,
    private val probe: StreamProbe,
) {

    /**
     * @param measure whether to work out how big each one is. Only worth it when the viewer is
     * about to be shown the list.
     * @return the qualities, best first, or empty when the episode resolves to nothing. A
     * single entry means there is nothing to choose between.
     */
    suspend fun await(sourceId: Long, episode: Episode, measure: Boolean): List<DownloadQuality> = withIOContext {
        val videos = runCatching { getEpisodeVideos.await(sourceId, episode) }.getOrDefault(emptyList())
        val video = getEpisodeVideos.playable(sourceId, videos) ?: return@withIOContext emptyList()

        // Only opened when there is a reason to: with several videos the source has already
        // named the qualities, and the playlist has nothing to add unless sizes are wanted.
        val playlist = if (measure || videos.size <= 1) {
            runCatching { sizer.fetchText(video.videoUrl, video.headers) }.getOrNull()
        } else {
            null
        }
        val variants = playlist
            ?.takeIf { HlsPlaylist.isMaster(it) }
            ?.let { HlsPlaylist.variants(it, video.videoUrl) }
            .orEmpty()

        val delivery = playlist?.let { StreamSniffer.classify(it) }
        // A manifest this app can read. Costs nothing — the text is already here — and the
        // numbers are the ones the manifest itself declares, which beats measuring.
        val dash = playlist
            ?.takeIf { delivery is StreamDelivery.Manifest && !delivery.hls }
            ?.let { DashManifest.read(it, video.videoUrl) }

        when {
            // The qualities are inside the one stream.
            variants.isNotEmpty() -> variants.take(MAX_PROBED).measuring { variant ->
                DownloadQuality(
                    height = variant.height,
                    label = variant.height?.let { "${it}p" } ?: bitrateLabel(variant.bandwidth),
                    estimatedBytes = if (measure) {
                        sizer.sizeOfMedia(variant.url, video.headers, variant.bandwidth)
                    } else {
                        null
                    },
                )
            }
            // The source listed the qualities itself, each its own stream. Its own labels are
            // kept: a source that calls something "1080p (Server 2)" is saying something the
            // number alone does not.
            videos.size > 1 ->
                videos
                    .sortedByDescending { it.heightOrNull() ?: 0 }
                    .take(MAX_PROBED)
                    .measuring {
                        DownloadQuality(
                            height = it.heightOrNull(),
                            label = it.label(),
                            estimatedBytes = if (measure) sizer.sizeOf(it) else null,
                        )
                    }
            dash != null ->
                dash.videos
                    .sortedByDescending { it.height ?: 0 }
                    .take(MAX_PROBED)
                    .map {
                        DownloadQuality(
                            height = it.height,
                            label = it.height?.let { height -> "${height}p" } ?: bitrateLabel(it.bandwidth),
                            // The picture alone, like every other row: see [DownloadQuality].
                            estimatedBytes = dash.estimatedBytes(it),
                        )
                    }
            // A manifest with no reader here. Asked of ffprobe rather than parsed, which is
            // what keeps a new streaming format from being a new piece of code: the same call
            // lists the qualities inside a DASH manifest, a Smooth Streaming one, and anything
            // else ffmpeg learns to open. Only when somebody is about to read the answer —
            // it costs a real request and several seconds.
            measure && delivery is StreamDelivery.Manifest && !delivery.hls ->
                probed(video) ?: single(video, playlist, measure)
            // Nothing to choose between, but the size is still worth knowing.
            else -> single(video, playlist, measure)
        }
    }

    /**
     * The qualities ffprobe found inside a manifest, or null if it found nothing to choose
     * between — in which case the caller falls back to treating it as the one stream it is.
     */
    private suspend fun probed(video: Video): List<DownloadQuality>? {
        val media = probe.probe(video.videoUrl, video.headers) ?: return null
        return media.videos
            .sortedByDescending { it.height ?: 0 }
            .take(MAX_PROBED)
            .map {
                DownloadQuality(
                    height = it.height,
                    label = it.height?.let { height -> "${height}p" } ?: bitrateLabel(it.bitrate),
                    // The picture alone, like every other row: see [DownloadQuality].
                    estimatedBytes = media.pictureBytes(it),
                )
            }
            .takeIf { it.isNotEmpty() }
    }

    private suspend fun single(video: Video, playlist: String?, measure: Boolean) = listOf(
        DownloadQuality(
            height = video.heightOrNull(),
            label = video.label(),
            estimatedBytes = if (measure) sizer.sizeOf(video, playlist) else null,
        ),
    )

    /**
     * Weighs every quality at the same time rather than one after another.
     *
     * Each one costs a playlist and a handful of HEAD requests, and doing six of those in a row
     * is six round trips end to end — which is most of the wait between holding the download
     * button and the dialog appearing. They do not depend on each other, so there is no reason
     * for them to queue; the client's own per-host limit is what keeps the source from being
     * asked for thirty things at once.
     */
    private suspend fun <T> List<T>.measuring(
        measure: suspend (T) -> DownloadQuality,
    ): List<DownloadQuality> = coroutineScope {
        map { async { measure(it) } }.awaitAll()
    }

    private fun Video.label(): String =
        videoTitle.ifBlank { heightOrNull()?.let { "${it}p" } ?: UNNAMED }

    private fun bitrateLabel(bandwidth: Long?): String =
        bandwidth?.let { "${it / 1000} kbps" } ?: UNNAMED

    private companion object {
        const val UNNAMED = "—"

        /** Every option costs requests to measure; a source offering twenty is not worth that. */
        const val MAX_PROBED = 6
    }
}
