package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.episode.model.Episode

/**
 * One quality an episode can be downloaded at.
 *
 * @param height the vertical resolution, or null when the source names its qualities without
 * numbers. The label is what the viewer reads either way.
 * @param estimatedBytes roughly how big the file will be, or null when nothing in the stream
 * supports working it out. Shown as an estimate, never as a fact.
 */
data class DownloadQuality(
    val height: Int?,
    val label: String,
    val estimatedBytes: Long?,
)

/**
 * What an episode can be downloaded at, and roughly how big each option is.
 *
 * There are two shapes to cover, because sources disagree on where the qualities live. Some
 * return one entry per quality and the app picks between them. Others return a single stream
 * whose qualities are listed inside it, in a master playlist — so the list has to be opened to
 * find them.
 *
 * Both cost network at the moment the download button is tapped, which is the price of
 * answering "how big is this" before rather than after. It is a few small requests against a
 * download measured in hundreds of megabytes.
 */
@Inject
class GetDownloadQualities(
    private val getEpisodeVideos: GetEpisodeVideos,
    private val networkHelper: NetworkHelper,
) {

    /**
     * @return the qualities, best first, or null when the episode resolves to nothing. A
     * single entry means there is nothing to choose between.
     */
    suspend fun await(sourceId: Long, episode: Episode): List<DownloadQuality>? = withIOContext {
        val videos = runCatching { getEpisodeVideos.await(sourceId, episode) }.getOrDefault(emptyList())
        val video = getEpisodeVideos.playable(sourceId, videos) ?: return@withIOContext null

        val playlist = runCatching { fetchText(video.videoUrl, video.headers) }.getOrNull()
        val variants = playlist
            ?.takeIf { HlsPlaylist.isMaster(it) }
            ?.let { HlsPlaylist.variants(it, video.videoUrl) }
            .orEmpty()

        // The audio the source keeps apart from the picture ends up inside the same file, so
        // it counts towards the size. Measured once: the same tracks go with every quality.
        val extras = video.audioTracks.sumOf { sizeOfMedia(it.url, video.headers, null) ?: 0L }

        when {
            // The qualities are inside the one stream.
            variants.isNotEmpty() -> variants.take(MAX_PROBED).map {
                DownloadQuality(
                    height = it.height,
                    label = it.height?.let { height -> "${height}p" } ?: bitrateLabel(it.bandwidth),
                    estimatedBytes = sizeOfMedia(it.url, video.headers, it.bandwidth).plusExtras(extras),
                )
            }
            // The source listed the qualities itself, each its own stream. Its own labels are
            // kept: a source that calls something "1080p (Server 2)" is saying something the
            // number alone does not.
            videos.size > 1 ->
                videos
                    .sortedByDescending { it.heightOrNull() ?: 0 }
                    .take(MAX_PROBED)
                    .map { DownloadQuality(it.heightOrNull(), it.label(), sizeOf(it).plusExtras(extras)) }
            // Nothing to choose between, but the size is still worth knowing.
            else -> listOf(
                DownloadQuality(video.heightOrNull(), video.label(), sizeOf(video, playlist).plusExtras(extras)),
            )
        }
    }

    /** Null stays null: a size that could not be measured is not improved by adding to it. */
    private fun Long?.plusExtras(extras: Long): Long? = this?.plus(extras)

    private fun Video.label(): String =
        videoTitle.ifBlank { resolution?.let { "${it}p" } ?: UNNAMED }

    /** The size of whatever [video] points at, playlist or plain file. */
    private suspend fun sizeOf(video: Video, known: String? = null): Long? {
        val body = known ?: runCatching { fetchText(video.videoUrl, video.headers) }.getOrNull()
        // Not a playlist at all: the server already knows the length and will say so.
        if (body == null || !body.trimStart().startsWith(PLAYLIST_MARKER)) {
            return contentLength(video.videoUrl, video.headers)
        }
        if (!HlsPlaylist.isMaster(body)) {
            return sizeOfMedia(video.videoUrl, video.headers, video.bitrate?.toLong(), body)
        }
        val variant = HlsPlaylist.variants(body, video.videoUrl).pick(video.heightOrNull()) ?: return null
        return sizeOfMedia(variant.url, video.headers, variant.bandwidth)
    }

    /**
     * How big the stream a media playlist describes comes to.
     *
     * Bitrate times duration when the playlist declares a bitrate, which costs nothing extra.
     * Plenty of sources declare nothing — the one this was built against puts the numbers in
     * the title and leaves the playlist bare — and then segments are weighed instead.
     *
     * Five of them, spread through the episode, never the first, and weighed at the same
     * time so the extra samples cost no extra waiting. An episode opens on a title
     * card and a few seconds of near-still frames, which compress to about the same handful of
     * kilobytes whatever the resolution: sizing 1080p and 720p off their first segment made
     * them come out within half a percent of each other when one is really three times the
     * other. Sampling the middle measures the part of the episode that has motion in it.
     */
    private suspend fun sizeOfMedia(
        url: String,
        headers: Headers?,
        bandwidth: Long?,
        known: String? = null,
    ): Long? {
        val media = known ?: runCatching { fetchText(url, headers) }.getOrNull() ?: return null
        val duration = HlsPlaylist.durationSeconds(media)
        HlsPlaylist.estimatedBytes(bandwidth, duration)?.let { return it }

        val segments = HlsPlaylist.segmentUrls(media, url)
        if (segments.isEmpty()) return null
        val sampled = SAMPLE_POINTS
            .map { segments[((segments.size - 1) * it).toInt()] }
            .distinct()
        val sizes = coroutineScope {
            sampled.map { async { contentLength(it, headers) } }.awaitAll()
        }.filterNotNull()
        if (sizes.isEmpty()) return null
        return sizes.average().toLong() * segments.size
    }

    /** What the server says the resource weighs, without fetching it. */
    private fun contentLength(url: String, headers: Headers?): Long? {
        val request = Request.Builder().url(url).head().apply { headers?.let { headers(it) } }.build()
        return runCatching {
            networkHelper.client.newCall(request).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            }
        }.getOrNull()
    }

    private fun fetchText(url: String, headers: Headers?): String {
        val request = Request.Builder().url(url).apply { headers?.let { headers(it) } }.build()
        return networkHelper.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            // Capped: this is meant for playlists, and pointing it at an actual video would
            // otherwise pull the whole episode into memory to read its first line.
            val source = response.body?.source() ?: error("Empty response")
            source.use {
                it.request(MAX_PLAYLIST_BYTES)
                it.buffer.readUtf8(minOf(it.buffer.size, MAX_PLAYLIST_BYTES))
            }
        }
    }

    private fun bitrateLabel(bandwidth: Long?): String =
        bandwidth?.let { "${it / 1000} kbps" } ?: UNNAMED

    private companion object {
        const val UNNAMED = "—"
        const val PLAYLIST_MARKER = "#EXTM3U"
        const val MAX_PLAYLIST_BYTES = 2L * 1024 * 1024

        /** Every option costs a request or two; a source offering twenty is not worth that. */
        const val MAX_PROBED = 6

        /** Where through the episode to weigh a segment. Never the start: see [sizeOfMedia]. */
        val SAMPLE_POINTS = listOf(0.15, 0.3, 0.5, 0.7, 0.85)
    }
}
