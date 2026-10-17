package eu.kanade.tachiyomi.torrentutils

import tachiyomi.core.common.torrent.model.Torrent

/**
 * What [TorrentUtils] needs from the app to read a magnet link: a running TorrServer, which
 * only the app can reach (it lives in the torrent add-on, docs/adr/0008). Registered in Injekt
 * so this module does not depend on the app.
 */
interface TorrentMagnetResolver {

    /** The magnet's files, once its peers have sent the metadata. Does not keep the torrent. */
    suspend fun describeMagnet(link: String, title: String): Torrent
}
