package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.torrentutils.model.DeadTorrentException
import eu.kanade.tachiyomi.torrentutils.model.TorrentFile
import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import tachiyomi.core.common.torrent.TorrentHelpers
import tachiyomi.core.common.torrent.model.Torrent
import uy.kohesive.injekt.injectLazy

/**
 * Lets an anime extension look inside a torrent, to list one episode per file.
 *
 * Part of the API extensions are compiled against: Aniyomi's torrent extensions call this, and
 * the name, package and signatures have to stay exactly as they are there or those extensions
 * fail to load. Behind it the difference is that TorrServer runs in the add-on, reached through
 * [TorrentMagnetResolver], instead of inside this process.
 */
object TorrentUtils {
    private val resolver: TorrentMagnetResolver by injectLazy()
    private val network: NetworkHelper by injectLazy()

    suspend fun getTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        val torrent: Torrent = if (url.startsWith("magnet")) {
            // A magnet carries no file list: TorrServer has to fetch it from the peers.
            try {
                resolver.describeMagnet(url, title)
            } catch (_: TimeoutCancellationException) {
                throw DeadTorrentException()
            }
        } else {
            // A .torrent file has the list inside, so no TorrServer is needed.
            network.client.newCall(GET(url)).awaitSuccess().use { response ->
                TorrentHelpers.parseTorrentDetailsFromTorrentFileContent(response.body.byteStream())
            }
        }
        return torrentToTorrentInfo(torrent, title)
    }

    // A suspend function has a different signature in the JVM than a regular function (an additional Continuation
    // parameter is added by the Kotlin compiler). We add another overload of getTorrentInfo that is not a suspend
    // function so that extensions targetting other forks where getTorrentInfo was not a suspend function can still
    // work.
    @Deprecated(
        message = "This overload of getTorrentInfo exists only for binary compatibility with extensions targeting" +
            " other forks where getTorrentInfo was not a suspend function",
        level = DeprecationLevel.HIDDEN,
    )
    @JvmName("getTorrentInfo")
    fun blockingShimForGetTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        return runBlocking {
            getTorrentInfo(url, title)
        }
    }

    private fun torrentToTorrentInfo(torrent: Torrent, overrideTitle: String?): TorrentInfo {
        return TorrentInfo(
            overrideTitle ?: torrent.title,
            torrent.fileStats?.map { file ->
                TorrentFile(file.path, file.id ?: 0, file.length, torrent.hash!!, torrent.trackers ?: emptyList())
            } ?: emptyList(),
            torrent.hash!!,
            torrent.torrentSize ?: -1,
            torrent.trackers ?: emptyList(),
        )
    }
}
