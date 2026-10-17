package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.data.torrent.TorrentEngine
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.torrent.TorrServerClient
import tachiyomi.core.common.torrent.model.FileStats
import tachiyomi.core.common.torrent.model.Torrent

/**
 * The two decisions a torrent download hangs on before any byte arrives: that the link is a
 * torrent at all, which is what sends it through TorrServer instead of the app's client, and
 * what the file is called once it lands — which comes from TorrServer's url, not the magnet.
 */
class TorrentDownloadTest {

    private val server = TorrServerClient(OkHttpClient(), "http://127.0.0.1:40123")

    @Test
    fun `a magnet and a link to a torrent file go through TorrServer`() {
        assertTrue(TorrentEngine.isTorrentLink("magnet:?xt=urn:btih:abc&dn=Episode%2001&index=2"))
        assertTrue(TorrentEngine.isTorrentLink("https://nyaa.si/download/1234.torrent"))
        assertTrue(TorrentEngine.isTorrentLink("https://example.org/get/ep01.torrent?token=xyz"))
    }

    @Test
    fun `an ordinary video does not`() {
        assertFalse(TorrentEngine.isTorrentLink("https://cdn.example.org/ep01.mp4"))
        assertFalse(TorrentEngine.isTorrentLink("https://cdn.example.org/master.m3u8?torrent=1"))
    }

    @Test
    fun `the episode keeps the extension of its file inside the torrent`() {
        val torrent = Torrent(title = "Show", hash = "abc")
        val mkv = server.streamUrl(torrent, FileStats(id = 2, path = "Show/[Group] Show - 01 (1080p).mkv", length = 1))
        val mp4 = server.streamUrl(torrent, FileStats(id = 1, path = "Episode 1.mp4", length = 1))

        assertEquals("mkv", AnimeDownloader.extensionFor(mkv))
        assertEquals("mp4", AnimeDownloader.extensionFor(mp4))
    }
}
