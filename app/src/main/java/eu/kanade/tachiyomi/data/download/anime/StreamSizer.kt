package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.Request

/**
 * Works out roughly how big a stream will be.
 *
 * Costs network — a playlist and a handful of HEAD requests per quality — which is why it is
 * its own thing rather than part of finding out what qualities exist. Asking both questions at
 * once put some thirty requests between tapping download and the download starting, and made
 * the button feel broken. What exists is cheap; what it weighs is not, and it is only worth
 * paying for when somebody is about to be shown the numbers.
 */
@Inject
class StreamSizer(
    private val networkHelper: NetworkHelper,
) {

    /** The size of whatever [video] points at, playlist or plain file. */
    suspend fun sizeOf(video: Video, known: String? = null): Long? {
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
     * Five of them, spread through the episode, never the first, and weighed at the same time
     * so the extra samples cost no extra waiting. An episode opens on a title card and a few
     * seconds of near-still frames, which compress to about the same handful of kilobytes
     * whatever the resolution: sizing 1080p and 720p off their first segment made them come out
     * within half a percent of each other when one is really twice the other.
     */
    suspend fun sizeOfMedia(
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

    fun fetchText(url: String, headers: Headers?): String {
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

    private companion object {
        const val PLAYLIST_MARKER = "#EXTM3U"
        const val MAX_PLAYLIST_BYTES = 2L * 1024 * 1024

        /** Where through the episode to weigh a segment. Never the start: see [sizeOfMedia]. */
        val SAMPLE_POINTS = listOf(0.15, 0.3, 0.5, 0.7, 0.85)
    }
}
