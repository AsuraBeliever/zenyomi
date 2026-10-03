package tachiyomi.core.common.torrent

import tachiyomi.core.common.torrent.bencode.BencodeParser
import tachiyomi.core.common.torrent.bencode.BencodeValue
import tachiyomi.core.common.torrent.bencode.BencodeWriter
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Adds the viewer's trackers to a torrent before TorrServer sees it.
 *
 * Aniyomi hands its list to the TorrServer library it links. The add-on runs the stock
 * binary, which takes no such list, so the trackers go into the torrent itself: as `tr=`
 * parameters of a magnet, or as one more tier of a `.torrent`'s `announce-list`. Neither
 * touches the info dictionary, so the torrent's hash — and with it everything that refers to
 * the torrent — stays the same.
 */
object TorrentTrackers {

    private val SCHEMES = setOf("udp", "http", "https", "ws", "wss")

    /** The trackers in [text], one per line; blank lines and anything that is not a tracker url are skipped. */
    fun parse(text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.substringBefore("://", "").lowercase() in SCHEMES &&
                line.substringAfter("://").isNotEmpty()
        }
        .distinct()
        .toList()

    /** [magnet] with a `tr=` for each of [trackers] it does not carry yet. */
    fun addToMagnet(magnet: String, trackers: List<String>): String {
        if (trackers.isEmpty()) return magnet
        // A fragment ends the query; whatever is added goes before it.
        val fragment = magnet.substringAfter('#', "")
        val base = magnet.substringBefore('#')
        val existing = base.substringAfter('?', "")
            .split('&')
            .filter { it.startsWith("tr=") }
            // A badly escaped tracker is the source's to fix; it stays as written, and ours are
            // added next to it rather than the whole magnet failing to open.
            .mapNotNull { runCatching { URLDecoder.decode(it.substringAfter('='), "UTF-8") }.getOrNull() }
            .toSet()
        val missing = trackers.filter { it !in existing }
        if (missing.isEmpty()) return magnet

        val added = missing.joinToString("&") { "tr=" + URLEncoder.encode(it, "UTF-8") }
        val separator = when {
            '?' !in base -> "?"
            base.endsWith('?') || base.endsWith('&') -> ""
            else -> "&"
        }
        return base + separator + added + if (fragment.isNotEmpty()) "#$fragment" else ""
    }

    /**
     * The `.torrent` in [torrentFile] with [trackers] it does not list yet added as a new tier
     * of its `announce-list`. A file that cannot be read as a torrent comes back unchanged:
     * TorrServer is the one to say what is wrong with it.
     */
    fun addToTorrentFile(torrentFile: ByteArray, trackers: List<String>): ByteArray {
        if (trackers.isEmpty()) return torrentFile
        val root = runCatching { BencodeParser.parse(torrentFile.inputStream()) }.getOrNull()
            as? BencodeValue.Dictionary ?: return torrentFile

        val announce = (root.getByString("announce") as? BencodeValue.ByteString)?.toUTF8String()
        val tiers = (root.getByString("announce-list") as? BencodeValue.List)?.value
            ?.mapNotNull { tier ->
                (tier as? BencodeValue.List)?.value
                    ?.mapNotNull { (it as? BencodeValue.ByteString)?.toUTF8String() }
                    ?.takeIf { it.isNotEmpty() }
            }
            .orEmpty()
        // A client that finds an announce-list ignores announce (BEP 12): if the list is made
        // here, announce has to go in it or the torrent loses its own tracker.
        val ownTiers = tiers.ifEmpty { listOfNotNull(announce?.let(::listOf)) }

        val known = ownTiers.flatten().toSet()
        val missing = trackers.filter { it !in known }
        if (missing.isEmpty()) return torrentFile

        val announceList = BencodeValue.List(
            (ownTiers + listOf(missing)).map { tier ->
                BencodeValue.List(tier.map { BencodeValue.ByteString.fromUTF8String(it) })
            },
        )
        val updated = BencodeValue.Dictionary(
            root.value.toSortedMap().apply {
                put(BencodeValue.ByteString.fromUTF8String("announce-list"), announceList)
            },
        )
        return ByteArrayOutputStream().also { BencodeWriter.write(updated, it) }.toByteArray()
    }
}
