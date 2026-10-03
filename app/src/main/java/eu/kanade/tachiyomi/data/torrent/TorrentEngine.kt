package eu.kanade.tachiyomi.data.torrent

import android.content.Context
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.torrentutils.TorrentMagnetResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import tachiyomi.core.common.torrent.TorrServerClient
import tachiyomi.core.common.torrent.TorrentEpisodeFile
import tachiyomi.core.common.torrent.TorrentTrackers
import tachiyomi.core.common.torrent.model.Torrent
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * The one TorrServer the whole app shares, running in the torrent add-on (docs/adr/0008).
 *
 * Whoever needs it — the player opening an episode, an extension asking what is inside a
 * magnet — borrows it, and the add-on stays bound while anyone holds it. Once nobody has for
 * [IDLE_TIMEOUT] the binding is released and TorrServer stops, so it does not keep the phone's
 * radio busy behind the user's back, while stepping to the next episode does not pay for a
 * restart.
 *
 * The binding is made in this object's own scope rather than in the caller's. A caller that is
 * cancelled while the add-on starts — the viewer leaving the player during those two seconds —
 * then cannot lose a connection that nobody would ever close.
 */
@Inject
@SingleIn(AppScope::class)
class TorrentEngine(
    private val context: Context,
    private val addon: TorrentAddon,
    private val network: NetworkHelper,
    private val preferences: TorrentPreferences,
) : TorrentMagnetResolver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    // Guarded by [mutex].
    private var connection: Deferred<TorrentAddon.Connection>? = null
    private var users = 0
    private var idle: Job? = null
    private val openStreams = mutableMapOf<String, Int>()

    // Not the app's client: its interceptors and DNS-over-HTTPS are for the internet, and this
    // only ever talks to 127.0.0.1. Reads are slow while TorrServer is still finding peers.
    private val http = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Runs [block] against TorrServer, starting it if it is not running. */
    suspend fun <T> use(block: suspend (TorrServerClient) -> T): T {
        val client = acquire()
        try {
            return block(client)
        } finally {
            release()
        }
    }

    /**
     * Opens the episode behind [link] — a magnet, a link to a `.torrent`, or a `.torrent` on
     * the device — and returns the url mpv plays. TorrServer stays up, and keeps the torrent,
     * until the stream is closed.
     */
    suspend fun openStream(link: String, title: String, headers: Map<String, String> = emptyMap()): TorrentStream {
        val client = acquire()
        var added: String? = null
        try {
            val trackers = trackers()
            val torrent = if (link.startsWith("magnet:", ignoreCase = true)) {
                val pending = client.add(TorrentTrackers.addToMagnet(link, trackers), title).also { added = it.hash }
                try {
                    client.awaitFiles(pending)
                } catch (_: TimeoutCancellationException) {
                    // Metadata comes from peers; a minute without it means there are none.
                    throw TorrentNoPeersException()
                }
            } else {
                val file = TorrentTrackers.addToTorrentFile(readTorrentFile(link, headers), trackers)
                client.upload(file, title).also { added = it.hash }
            }
            val hash = requireNotNull(torrent.hash) { "TorrServer returned a torrent without a hash" }
            val file = TorrentEpisodeFile.choose(torrent.fileStats.orEmpty(), TorrentEpisodeFile.requestedIndex(link))
                ?: throw EmptyTorrentException()
            mutex.withLock { openStreams[hash] = (openStreams[hash] ?: 0) + 1 }
            logcat { "Torrent $hash: playing file ${file.id} (${file.path})" }
            return TorrentStream(client.streamUrl(torrent, file)) { closeStream(client, hash) }
        } catch (e: Throwable) {
            // Added but never handed out: nobody else will remove it.
            added?.let { hash -> scope.launch { removeUnlessOpen(client, hash) } }
            release()
            throw e
        }
    }

    /** What an extension asks for: the files of a magnet, without keeping it. */
    override suspend fun describeMagnet(link: String, title: String): Torrent = use { client ->
        val added = client.add(TorrentTrackers.addToMagnet(link, trackers()), title)
        try {
            client.awaitFiles(added)
        } finally {
            // Also when the wait is cancelled — the viewer left the entry while the extension
            // listed episodes — or times out on a magnet nobody shares. In the engine's scope,
            // because the caller's may be the one being cancelled.
            added.hash?.let { hash -> scope.launch { removeUnlessOpen(client, hash) } }
        }
    }

    /**
     * Forgets every torrent TorrServer holds that is not playing right now, with whatever it
     * had downloaded of them, and returns how many there were. Our own torrents are removed
     * when their episode closes; what this clears is what an add-on killed mid-episode left
     * in its database.
     */
    suspend fun clearCache(): Int = use { client ->
        val playing = mutex.withLock { openStreams.keys.toSet() }
        client.list()
            .mapNotNull { it.hash }
            .filter { it !in playing }
            .count { hash ->
                runCatching { client.remove(hash) }
                    .onFailure { logcat(LogPriority.WARN, it) { "Could not remove torrent $hash" } }
                    .isSuccess
            }
    }

    /**
     * Lets TorrServer go now instead of after [IDLE_TIMEOUT], unless something is using it.
     * For when the viewer turns torrents off: nothing should keep running after that.
     */
    fun stopWhenUnused() {
        scope.launch {
            mutex.withLock {
                if (users > 0) return@withLock
                idle?.cancel()
                idle = null
                shutDown()
            }
        }
    }

    private fun trackers(): List<String> = TorrentTrackers.parse(preferences.trackers.get())

    private suspend fun readTorrentFile(link: String, headers: Map<String, String>): ByteArray = withIOContext {
        if (link.startsWith("http://") || link.startsWith("https://")) {
            // The source's headers travel with it, as they do to mpv: the host may refuse a bare request.
            val request = if (headers.isEmpty()) GET(link) else GET(link, headers.toHeaders())
            network.client.newCall(request).awaitSuccess().use { it.body.bytes() }
        } else {
            context.contentResolver.openInputStream(link.toUri())?.use { it.readBytes() }
                ?: error("Cannot read $link")
        }
    }

    private fun closeStream(client: TorrServerClient, hash: String) {
        scope.launch {
            removeUnlessOpen(client, hash, closing = true)
            release()
        }
    }

    private suspend fun removeUnlessOpen(client: TorrServerClient, hash: String, closing: Boolean = false) {
        val remove = mutex.withLock {
            val open = (openStreams[hash] ?: 0) - if (closing) 1 else 0
            if (open > 0) {
                openStreams[hash] = open
                false
            } else {
                openStreams.remove(hash)
                true
            }
        }
        // Another episode of the same torrent may still be playing; only the last one removes it.
        if (remove) {
            runCatching { client.remove(hash) }
                .onFailure { logcat(LogPriority.WARN, it) { "Could not remove torrent $hash" } }
        }
    }

    private suspend fun acquire(): TorrServerClient {
        // Checked on every use rather than when the setting changes: an extension asking for
        // a magnet's files goes through here too, and it must not start TorrServer either.
        if (!preferences.enabled.get()) throw TorrentDisabledException()
        val pending = mutex.withLock {
            users++
            idle?.cancel()
            idle = null
            connection?.takeIf { it.usable() }
                ?: scope.async { addon.connect() }.also { connection = it }
        }
        val connected = try {
            pending.await()
        } catch (e: Throwable) {
            release()
            throw e
        }
        return TorrServerClient(http, connected.baseUrl)
    }

    private fun release() {
        scope.launch {
            mutex.withLock {
                users--
                if (users > 0) return@withLock
                idle = scope.launch {
                    delay(IDLE_TIMEOUT)
                    mutex.withLock { if (users == 0) shutDown() }
                }
            }
        }
    }

    /** Lets go of the add-on, which stops TorrServer. Called with [mutex] held. */
    private fun shutDown() {
        val current = connection ?: return
        connection = null
        openStreams.clear()
        if (current.isCompleted && current.getCompletionExceptionOrNull() == null) {
            current.getCompleted().close()
        } else {
            current.cancel()
        }
        logcat { "TorrServer released" }
    }

    private fun Deferred<TorrentAddon.Connection>.usable(): Boolean = when {
        !isCompleted -> true
        getCompletionExceptionOrNull() != null -> false
        else -> getCompleted().isAlive
    }

    companion object {
        private val IDLE_TIMEOUT = 30.seconds

        fun isTorrentLink(url: String): Boolean =
            url.startsWith("magnet:", ignoreCase = true) ||
                url.substringBefore('#').substringBefore('?').endsWith(".torrent", ignoreCase = true)
    }
}

/** A playing torrent file. Closing it lets TorrServer forget the torrent. */
class TorrentStream(val url: String, private val onClose: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

/** The viewer has torrents turned off in the player settings. */
class TorrentDisabledException : Exception("Torrents are turned off")

/** A magnet whose metadata never arrived: nobody is sharing it. */
class TorrentNoPeersException : Exception("No peers for this torrent")

/** A torrent whose file list came back empty. */
class EmptyTorrentException : Exception("The torrent has no files")
