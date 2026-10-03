package eu.kanade.tachiyomi.data.torrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TorrentAddonDownloadTest {

    private val template = "https://example.org/v1.0/zenyomi-torrent-{abi}-v1.0.apk"

    @Test
    fun `fills in the device's preferred abi`() {
        assertEquals(
            "https://example.org/v1.0/zenyomi-torrent-arm64-v8a-v1.0.apk",
            TorrentAddonDownload.url(template, listOf("arm64-v8a", "armeabi-v7a", "armeabi")),
        )
    }

    @Test
    fun `an x86_64 emulator gets the x86_64 build, not the x86 one`() {
        assertEquals(
            "https://example.org/v1.0/zenyomi-torrent-x86_64-v1.0.apk",
            TorrentAddonDownload.url(template, listOf("x86_64", "x86", "arm64-v8a")),
        )
    }

    @Test
    fun `skips abis the release has no build for`() {
        assertEquals(
            "https://example.org/v1.0/zenyomi-torrent-armeabi-v7a-v1.0.apk",
            TorrentAddonDownload.url(template, listOf("riscv64", "armeabi-v7a")),
        )
    }

    @Test
    fun `no build for the device means nothing to download`() {
        assertNull(TorrentAddonDownload.url(template, listOf("riscv64", "armeabi")))
    }

    @Test
    fun `a build without a url has nothing to download`() {
        assertNull(TorrentAddonDownload.url("", listOf("arm64-v8a")))
    }
}
