package tachiyomi.core.common.torrent

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TorrentTrackersTest {

    private val opentrackr = "udp://tracker.opentrackr.org:1337/announce"
    private val nyaa = "http://nyaa.tracker.wf:7777/announce"

    @Test
    fun `parse keeps one tracker per line and drops what is not one`() {
        val text = """
            $nyaa

              $opentrackr
            not a tracker
            ftp://example.org/announce
            udp://
            $nyaa
        """.trimIndent()
        assertEquals(listOf(nyaa, opentrackr), TorrentTrackers.parse(text))
    }

    @Test
    fun `parse of an empty list is empty`() {
        assertEquals(emptyList<String>(), TorrentTrackers.parse(""))
    }

    @Test
    fun `a magnet gets the trackers it lacks, encoded`() {
        val magnet = "magnet:?xt=urn:btih:abc&dn=Episode"
        assertEquals(
            "$magnet&tr=http%3A%2F%2Fnyaa.tracker.wf%3A7777%2Fannounce",
            TorrentTrackers.addToMagnet(magnet, listOf(nyaa)),
        )
    }

    @Test
    fun `a tracker the magnet already carries is not repeated`() {
        val magnet = "magnet:?xt=urn:btih:abc&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        assertEquals(magnet, TorrentTrackers.addToMagnet(magnet, listOf(opentrackr)))
    }

    @Test
    fun `trackers go before a fragment and keep the index the source asked for`() {
        val magnet = "magnet:?xt=urn:btih:abc&index=3#episode"
        val result = TorrentTrackers.addToMagnet(magnet, listOf(nyaa))
        assertEquals(
            "magnet:?xt=urn:btih:abc&index=3&tr=http%3A%2F%2Fnyaa.tracker.wf%3A7777%2Fannounce#episode",
            result,
        )
        assertEquals(3, TorrentEpisodeFile.requestedIndex(result))
    }

    @Test
    fun `no trackers leaves the magnet alone`() {
        val magnet = "magnet:?xt=urn:btih:abc"
        assertEquals(magnet, TorrentTrackers.addToMagnet(magnet, emptyList()))
    }

    @Test
    fun `a torrent file gets a new tier and keeps its own tracker and its hash`() {
        val original = resource("test-torrent-file.torrent")
        val before = TorrentHelpers.parseTorrentDetailsFromTorrentFileContent(original.inputStream())

        val updated = TorrentTrackers.addToTorrentFile(original, listOf(opentrackr, nyaa))
        val after = TorrentHelpers.parseTorrentDetailsFromTorrentFileContent(updated.inputStream())

        assertEquals(before.hash, after.hash)
        assertEquals(before.fileStats, after.fileStats)
        // announce, then the announce-list: its own tier first, ours after, nothing twice.
        assertEquals(listOf(opentrackr, opentrackr, nyaa), after.trackers)
    }

    @Test
    fun `a torrent file that lists every tracker already is returned as it was`() {
        val original = resource("test-torrent-file.torrent")
        assertArrayEquals(original, TorrentTrackers.addToTorrentFile(original, listOf(opentrackr)))
    }

    @Test
    fun `bytes that are not a torrent are returned as they were`() {
        val junk = "<html>not found</html>".toByteArray()
        assertArrayEquals(junk, TorrentTrackers.addToTorrentFile(junk, listOf(nyaa)))
    }

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("tachiyomi/core/common/torrent/$name")!!.readBytes()
}
