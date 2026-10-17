package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.domain.anime.interactor.GetEpisodeVideos
import eu.kanade.tachiyomi.animesource.model.Video
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.torrent.TorrServerClient
import tachiyomi.core.common.torrent.model.FileStats
import tachiyomi.core.common.torrent.model.Torrent
import tachiyomi.domain.episode.model.Episode

/**
 * What a torrent download hangs on before any byte arrives: that the queue accepts it at all —
 * a magnet is not http, and used to be dropped as undownloadable — that the quality dialog does
 * not try to weigh it, and what the file is called once it lands.
 */
class TorrentDownloadTest {

    private val remote = 2L
    private val local = 0L

    @Test
    fun `a magnet and a link to a torrent file can be downloaded`() {
        assertTrue(AnimeDownloader.isDownloadableUrl(remote, "magnet:?xt=urn:btih:abc&dn=Episode%2001&index=2"))
        assertTrue(AnimeDownloader.isDownloadableUrl(remote, "https://nyaa.si/download/1234.torrent"))
    }

    @Test
    fun `so can an ordinary video`() {
        assertTrue(AnimeDownloader.isDownloadableUrl(remote, "https://cdn.example.org/ep01.m3u8"))
    }

    @Test
    fun `nothing from the local source, torrent or not, and nothing that is not fetchable`() {
        assertFalse(AnimeDownloader.isDownloadableUrl(local, "magnet:?xt=urn:btih:abc"))
        assertFalse(
            AnimeDownloader.isDownloadableUrl(local, "content://com.android.externalstorage.documents/ep01.mp4"),
        )
        assertFalse(
            AnimeDownloader.isDownloadableUrl(remote, "content://com.android.externalstorage.documents/ep01.mp4"),
        )
        assertFalse(AnimeDownloader.isDownloadableUrl(remote, null))
    }

    @Test
    fun `a single torrent is offered without being measured`() = runTest {
        val video = Video(videoUrl = "https://nyaa.si/download/1234.torrent", videoTitle = "1080p")
        val sizer = mockk<StreamSizer>()
        val qualities = qualitiesOf(listOf(video), sizer)

        assertEquals(1, qualities.size)
        assertEquals(1080, qualities.single().height)
        // Asked of the url, the size would have been the .torrent's, a few kilobytes.
        assertNull(qualities.single().estimatedBytes)
        verify(exactly = 0) { sizer.fetchText(any(), any()) }
    }

    @Test
    fun `several torrents are still offered to choose from, none of them measured`() = runTest {
        val videos = listOf(
            Video(videoUrl = "magnet:?xt=urn:btih:aaa", videoTitle = "1080p"),
            Video(videoUrl = "magnet:?xt=urn:btih:bbb", videoTitle = "720p"),
        )
        val sizer = mockk<StreamSizer>()
        val qualities = qualitiesOf(videos, sizer)

        assertEquals(listOf(1080, 720), qualities.map { it.height })
        assertTrue(qualities.all { it.estimatedBytes == null })
        verify(exactly = 0) { sizer.fetchText(any(), any()) }
    }

    @Test
    fun `the episode keeps the extension of its file inside the torrent`() {
        val server = TorrServerClient(OkHttpClient(), "http://127.0.0.1:40123")
        val torrent = Torrent(title = "Show", hash = "abc")
        val mkv = server.streamUrl(torrent, FileStats(id = 2, path = "Show/[Group] Show - 01 (1080p).mkv", length = 1))

        assertEquals("mkv", AnimeDownloader.extensionFor(mkv))
    }

    private suspend fun qualitiesOf(videos: List<Video>, sizer: StreamSizer): List<DownloadQuality> {
        val getEpisodeVideos = mockk<GetEpisodeVideos> {
            coEvery { await(remote, any()) } returns videos
            coEvery { playable(remote, videos) } returns videos.first()
        }
        return GetDownloadQualities(getEpisodeVideos, sizer, mockk())
            .await(remote, Episode.create(), measure = true)
    }
}
