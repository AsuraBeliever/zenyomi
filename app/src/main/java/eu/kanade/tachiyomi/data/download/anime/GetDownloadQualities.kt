package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.episode.model.Episode

/**
 * One quality an episode can be downloaded at.
 *
 * @param height the vertical resolution, or null when there is nowhere to read a number from.
 * The label is what the viewer sees either way.
 * @param estimatedBytes roughly how big the file will be, and always null unless somebody
 * asked to be shown sizes — measuring costs network. Shown as an estimate, never as a fact.
 */
data class DownloadQuality(
    val height: Int?,
    val label: String,
    val estimatedBytes: Long? = null,
)

/**
 * What an episode can be downloaded at.
 *
 * Two shapes to cover, because sources disagree on where the qualities live. Some return one
 * entry per quality and the app picks between them. Others return a single stream whose
 * qualities are listed inside it, in a master playlist, so the list has to be opened.
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

        // The audio a source keeps apart from the picture ends up in the same file, so it
        // counts towards the size. Measured once: the same tracks go with every quality.
        val extras = if (measure) {
            video.audioTracks.sumOf { sizer.sizeOfMedia(it.url, video.headers, null) ?: 0L }
        } else {
            0L
        }

        when {
            // The qualities are inside the one stream.
            variants.isNotEmpty() -> variants.take(MAX_PROBED).map {
                DownloadQuality(
                    height = it.height,
                    label = it.height?.let { height -> "${height}p" } ?: bitrateLabel(it.bandwidth),
                    estimatedBytes = if (measure) {
                        sizer.sizeOfMedia(it.url, video.headers, it.bandwidth).plusExtras(extras)
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
                    .map {
                        DownloadQuality(
                            height = it.heightOrNull(),
                            label = it.label(),
                            estimatedBytes = if (measure) sizer.sizeOf(it).plusExtras(extras) else null,
                        )
                    }
            // Nothing to choose between, but the size is still worth knowing.
            else -> listOf(
                DownloadQuality(
                    height = video.heightOrNull(),
                    label = video.label(),
                    estimatedBytes = if (measure) sizer.sizeOf(video, playlist).plusExtras(extras) else null,
                ),
            )
        }
    }

    private fun Video.label(): String =
        videoTitle.ifBlank { heightOrNull()?.let { "${it}p" } ?: UNNAMED }

    /** Null stays null: a size that could not be measured is not improved by adding to it. */
    private fun Long?.plusExtras(extras: Long): Long? = this?.plus(extras)

    private fun bitrateLabel(bandwidth: Long?): String =
        bandwidth?.let { "${it / 1000} kbps" } ?: UNNAMED

    private companion object {
        const val UNNAMED = "—"

        /** Every option costs requests to measure; a source offering twenty is not worth that. */
        const val MAX_PROBED = 6
    }
}
