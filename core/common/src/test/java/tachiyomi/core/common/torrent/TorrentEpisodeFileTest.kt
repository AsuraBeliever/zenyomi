package tachiyomi.core.common.torrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.torrent.model.FileStats

class TorrentEpisodeFileTest {

    // What TorrServer lists for WebTorrent's Big Buck Bunny torrent: the video is not first.
    private val bigBuckBunny = listOf(
        FileStats(1, "Big Buck Bunny/Big Buck Bunny.en.srt", 140),
        FileStats(2, "Big Buck Bunny/Big Buck Bunny.mp4", 276_134_947),
        FileStats(3, "Big Buck Bunny/poster.jpg", 310_380),
    )

    private val season = listOf(
        FileStats(1, "Show/Show - 01.mkv", 400_000_000),
        FileStats(2, "Show/Show - 02.mkv", 410_000_000),
        FileStats(3, "Show/Show - 03.mkv", 390_000_000),
    )

    @Test
    fun `a real torrent file parses to the files TorrServer lists, and the video is chosen`() {
        // webtorrent.io/torrents/big-buck-bunny.torrent (Blender Foundation, CC BY 3.0).
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "tachiyomi/core/common/torrent/big-buck-bunny.torrent",
        )!!
        val torrent = TorrentHelpers.parseTorrentDetailsFromTorrentFileContent(stream)

        assertEquals("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c", torrent.hash)
        // Same ids as TorrServer's list, which is what the choice goes by. The paths lack the
        // torrent's root folder that TorrServer puts in front; Aniyomi's parser has always
        // returned them that way to extensions, so it is left as ported.
        assertEquals(
            bigBuckBunny.map { it.id to it.path.substringAfterLast('/') },
            torrent.fileStats!!.map { it.id to it.path },
        )
        assertEquals(2, TorrentEpisodeFile.choose(torrent.fileStats!!, requestedIndex = null)?.id)
    }

    @Test
    fun `without an index the video is chosen, not the first file`() {
        assertEquals(2, TorrentEpisodeFile.choose(bigBuckBunny, requestedIndex = null)?.id)
    }

    @Test
    fun `the index a source asks for wins over size`() {
        assertEquals(3, TorrentEpisodeFile.choose(season, requestedIndex = 3)?.id)
    }

    @Test
    fun `an index that names no file falls back to the largest video`() {
        assertEquals(2, TorrentEpisodeFile.choose(season, requestedIndex = 0)?.id)
    }

    @Test
    fun `a torrent with no video still plays its largest file`() {
        val files = listOf(FileStats(1, "a.txt", 10), FileStats(2, "b.bin", 20))
        assertEquals(2, TorrentEpisodeFile.choose(files, requestedIndex = null)?.id)
    }

    @Test
    fun `video extensions are matched regardless of case`() {
        val files = listOf(FileStats(1, "cover.png", 900), FileStats(2, "EP01.MKV", 500))
        assertEquals(2, TorrentEpisodeFile.choose(files, requestedIndex = null)?.id)
    }

    @Test
    fun `an empty torrent has no episode`() {
        assertNull(TorrentEpisodeFile.choose(emptyList(), requestedIndex = null))
    }

    @Test
    fun `the index is read from a magnet link`() {
        val magnet = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&tr=udp%3A%2F%2Fa&index=7"
        assertEquals(7, TorrentEpisodeFile.requestedIndex(magnet))
    }

    @Test
    fun `no index when the magnet has none or it is not a magnet`() {
        assertNull(TorrentEpisodeFile.requestedIndex("magnet:?xt=urn:btih:dd8255ec&dn=x"))
        assertNull(TorrentEpisodeFile.requestedIndex("magnet:?xt=urn:btih:dd8255ec&index=abc"))
        assertNull(TorrentEpisodeFile.requestedIndex("https://example.org/show.torrent?index=2"))
    }
}
