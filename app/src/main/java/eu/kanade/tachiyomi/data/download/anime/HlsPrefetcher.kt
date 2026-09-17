package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Fetches an HLS stream's segments to disk, several at a time, and hands back a playlist that
 * points at the local copies.
 *
 * ffmpeg can fetch a stream perfectly well on its own, and that is what this used to leave it
 * to. The problem is that its hls demuxer asks for one segment, waits, then asks for the next,
 * and the sources this app talks to spread an episode across several CDN hosts in rotation —
 * so every single segment pays a fresh DNS lookup, TCP handshake and TLS handshake, in series.
 *
 * Measured on the client's phone: segments arriving every 490 ms like clockwork, the interval
 * the same whichever segment it was. An interval that does not move with the size of the thing
 * being fetched is latency, not bandwidth — roughly 60 ms of transfer and 430 ms of waiting,
 * with the connection idle for most of a four-minute download.
 *
 * Fetching them concurrently overlaps all that waiting. ffmpeg then reads finished files off
 * local disk and only has to mux them, which costs no network at all.
 */
@Inject
@SingleIn(AppScope::class)
class HlsPrefetcher(
    private val context: Context,
    private val networkHelper: NetworkHelper,
) {

    /** A stream whose segments now live on disk. */
    data class Local(val playlist: File, val bytes: Long)

    /**
     * Downloads every segment [playlistUrl] names into [directory].
     *
     * @param permits shared across every stream of one episode — the video and its audio
     * tracks — so the whole download keeps to one connection budget rather than one each.
     * @param onBytes called as segments land, with the running total for this stream.
     * @return the local playlist, or null when this is not a media playlist or a segment could
     * not be fetched. A null sends the caller back to letting ffmpeg do it the slow way, which
     * is worse but works.
     */
    suspend fun prefetch(
        playlistUrl: String,
        headers: Headers?,
        media: String,
        directory: File,
        permits: Semaphore,
        onBytes: (Long) -> Unit,
    ): Local? {
        val segments = HlsPlaylist.segmentUrls(media, playlistUrl)
        if (segments.isEmpty()) return null
        if (!directory.mkdirs() && !directory.isDirectory) return null

        val downloaded = AtomicLong()
        val files = try {
            coroutineScope {
                segments.mapIndexed { index, url ->
                    async {
                        val host = url.toHttpUrlOrNull()?.host.orEmpty()
                        permits.withPermit {
                            hostPermit(host).withPermit {
                                val file = File(directory, "%05d$SEGMENT_SUFFIX".format(index))
                                val size = fetchTo(url, headers, host, file)
                                onBytes(downloaded.addAndGet(size))
                                file
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not prefetch the segments; leaving it to ffmpeg" }
            directory.deleteRecursively()
            return null
        }

        val local = File(directory, LOCAL_PLAYLIST)
        local.writeText(rewrite(media, playlistUrl, files))
        return Local(local, downloaded.get())
    }

    /**
     * The same playlist with its segments pointing at the files just downloaded.
     *
     * Everything else is left exactly as it was, because the rest of a playlist is what tells
     * ffmpeg how to put the stream back together. Encryption keys are the one thing that has
     * to change: they stay remote, so a relative key url that used to resolve against the
     * playlist's own address would resolve against a folder on the phone instead.
     */
    private fun rewrite(media: String, playlistUrl: String, files: List<File>): String {
        val base = playlistUrl.toHttpUrlOrNull()
        var index = 0
        return media.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                trimmed.startsWith(KEY_TAG) -> absoluteKey(trimmed, base)
                trimmed.startsWith("#") -> line
                else -> files.getOrNull(index++)?.name ?: line
            }
        }
    }

    private fun absoluteKey(line: String, base: HttpUrl?): String {
        if (base == null) return line
        return KEY_URI.replace(line) { match ->
            val resolved = base.resolve(match.groupValues[1])?.toString() ?: match.groupValues[1]
            """URI="$resolved""""
        }
    }

    /**
     * How many segments may be in flight against one host.
     *
     * The budget that matters is per host, not overall. These sources spread an episode over
     * four CDN hosts in rotation, and asking any one of them for eight things at once is what
     * a rate limiter is for: the first attempt at this took 429s within seconds. Two apiece
     * across four hosts still overlaps the handshakes, which is the whole point, without
     * looking like an attack to any single one of them.
     */
    private val hostPermits = ConcurrentHashMap<String, Semaphore>()

    private fun hostPermit(host: String): Semaphore = hostPermits.getOrPut(host) { Semaphore(PER_HOST) }

    /**
     * When a host said it had had enough, and when it is worth asking again.
     *
     * A 429 is not a failure of that one segment: it is the host saying the whole conversation
     * is going too fast. Retrying just that request a few hundred milliseconds later spends the
     * attempts inside the same window and fails an episode that was 80% downloaded — which is
     * exactly what happened, and the fallback then threw away 227 segments already on disk and
     * started over at one request at a time.
     *
     * So the host is put on a cooldown that every request to it waits out, and the other three
     * carry on. Slowing one host down is much cheaper than losing the work.
     */
    private val hostCooldown = ConcurrentHashMap<String, Long>()

    private suspend fun awaitCooldown(host: String) {
        val until = hostCooldown[host] ?: return
        val wait = until - System.currentTimeMillis()
        if (wait > 0) delay(wait)
    }

    /**
     * @return how many bytes landed.
     *
     * Retries with a growing wait rather than straight away. A segment failing is ordinary on
     * these hosts, but the reason is usually that they want a moment — three immediate retries
     * spend all the attempts inside the same rate-limit window and fail an episode that would
     * have been fine a second later.
     */
    private suspend fun fetchTo(url: String, headers: Headers?, host: String, file: File): Long {
        var lastFailure: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) delay(BACKOFF_MS * (1L shl minOf(attempt - 1, MAX_BACKOFF_SHIFT)))
            awaitCooldown(host)
            try {
                val request = Request.Builder().url(url).apply { headers?.let { headers(it) } }.build()
                networkHelper.client.newCall(request).execute().use { response ->
                    if (response.code == TOO_MANY_REQUESTS) {
                        hostCooldown[host] = System.currentTimeMillis() + COOLDOWN_MS
                        error("HTTP ${response.code}")
                    }
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty response")
                    file.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                }
                return file.length()
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        throw lastFailure ?: IllegalStateException("Could not fetch a segment")
    }

    /** Where an episode's segments live while it is being fetched. */
    fun workspace(episodeId: Long): File =
        File(context.externalCacheDir ?: context.cacheDir, "$WORKSPACE/$episodeId")

    companion object {
        /**
         * How many segments to fetch at once.
         *
         * Enough to hide the handshakes without hammering a source that is already serving the
         * episode from four hosts. Beyond this the gain flattens, because the transfer rather
         * than the waiting becomes the limit — which is the point.
         */
        const val PARALLELISM = 8

        /** In flight against any one host. See [hostPermits]. */
        private const val PER_HOST = 2

        /**
         * Patient on purpose. Giving up sends the whole stream back to being fetched one
         * segment at a time, which costs far more than waiting out a host having a moment.
         */
        private const val ATTEMPTS = 8
        private const val BACKOFF_MS = 400L
        private const val MAX_BACKOFF_SHIFT = 4

        private const val TOO_MANY_REQUESTS = 429

        /** How long a host that said 429 is left alone. */
        private const val COOLDOWN_MS = 4_000L
        private const val SEGMENT_SUFFIX = ".seg"
        private const val LOCAL_PLAYLIST = "local.m3u8"
        private const val WORKSPACE = "anime-download"
        private const val KEY_TAG = "#EXT-X-KEY"
        private val KEY_URI = Regex("""URI="([^"]+)"""")
    }
}
