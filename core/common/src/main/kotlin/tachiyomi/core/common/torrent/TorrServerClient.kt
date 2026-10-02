package tachiyomi.core.common.torrent

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.torrent.model.FileStats
import tachiyomi.core.common.torrent.model.Torrent
import tachiyomi.core.common.torrent.model.TorrentRequest
import java.io.File
import java.net.URLEncoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * TorrServer's HTTP API, as much of it as playing an episode needs.
 *
 * Adapted from Aniyomi's `TorrentServerApi`, which kept the port in a mutable singleton
 * because it ran TorrServer inside its own process. Here TorrServer lives in the add-on and
 * every client is made for the address the add-on handed back.
 */
class TorrServerClient(
    private val client: OkHttpClient,
    val baseUrl: String,
) {

    /**
     * Adds a magnet link or a link to a `.torrent`. TorrServer answers at once, before it
     * knows what files there are; see [awaitFiles].
     */
    suspend fun add(link: String, title: String): Torrent =
        torrents(TorrentRequest(action = "add", link = link, title = title, saveToDb = false))

    /** Adds a torrent from the bytes of a `.torrent` file. Its files are known immediately. */
    suspend fun upload(torrentFile: ByteArray, title: String): Torrent {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", title, torrentFile.toRequestBody(BITTORRENT))
            .addFormDataPart("save", "false")
            .addFormDataPart("title", title)
            .build()
        return client.newCall(POST("$baseUrl/torrent/upload", body = body)).awaitSuccess()
            .use { json.decodeFromStream<Torrent>(it.body.byteStream()) }
    }

    suspend fun get(hash: String): Torrent = torrents(TorrentRequest(action = "get", hash = hash))

    /** Stops the torrent and forgets it: nothing more is downloaded or kept for it. */
    suspend fun drop(hash: String) {
        client.newCall(POST("$baseUrl/torrents", body = encode(TorrentRequest(action = "drop", hash = hash))))
            .awaitSuccess()
            .close()
    }

    /**
     * Waits until TorrServer knows the torrent's files. For a magnet link that means finding
     * peers and fetching its metadata from them, which is the slow part of opening one.
     */
    suspend fun awaitFiles(torrent: Torrent, timeout: Duration = METADATA_TIMEOUT): Torrent {
        if (!torrent.fileStats.isNullOrEmpty()) return torrent
        val hash = requireNotNull(torrent.hash) { "TorrServer returned a torrent without a hash" }
        return withTimeout(timeout) {
            var current = get(hash)
            while (current.fileStats.isNullOrEmpty()) {
                delay(POLL_INTERVAL)
                current = get(hash)
            }
            current
        }
    }

    /** The url mpv opens. `play` makes TorrServer serve the file instead of a playlist. */
    fun streamUrl(torrent: Torrent, file: FileStats): String {
        val name = URLEncoder.encode(File(file.path).name, "UTF-8").replace("+", "%20")
        return "$baseUrl/stream/$name?link=${torrent.hash}&index=${file.id ?: 1}&play"
    }

    private suspend fun torrents(request: TorrentRequest): Torrent =
        client.newCall(POST("$baseUrl/torrents", body = encode(request))).awaitSuccess()
            .use { json.decodeFromStream<Torrent>(it.body.byteStream()) }

    private fun encode(request: TorrentRequest) = json.encodeToString(request).toRequestBody(JSON)

    companion object {
        val METADATA_TIMEOUT = 60.seconds
        private val POLL_INTERVAL = 500.milliseconds

        private val JSON = "application/json".toMediaType()
        private val BITTORRENT = "application/x-bittorrent".toMediaType()

        // TorrServer answers with a dozen fields — speeds, peers, poster — that nothing here reads.
        private val json = Json { ignoreUnknownKeys = true }
    }
}
