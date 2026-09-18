package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * The decision that says whether bytes are saved as an episode or refused.
 *
 * Pinned by tests because getting it wrong is not visible: a manifest written to the downloads
 * folder passes for a finished download until somebody tries to watch it offline, which is how
 * both of the bugs this replaced were found — an m3u8 saved as an episode, and then, once that
 * was fixed by name, an MPD saved as one. The rule is that only binary is saved, and that is
 * the thing worth holding still.
 */
class StreamSnifferTest {

    private fun classify(text: String) = StreamSniffer.classify(text.toByteArray(), text.toByteArray().size)

    private fun classify(bytes: ByteArray) = StreamSniffer.classify(bytes, bytes.size)

    @Test
    fun `an m3u8 is a manifest, and the one with a reader of its own`() {
        val delivery = classify("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:4.0,\n000.ts\n")
        assertEquals(StreamDelivery.Manifest(hls = true), delivery)
    }

    @Test
    fun `a leading blank line does not hide a playlist`() {
        assertEquals(StreamDelivery.Manifest(hls = true), classify("\n\n#EXTM3U\n#EXTINF:4.0,\n"))
    }

    @Test
    fun `a DASH manifest is a manifest, and ffmpeg's to fetch`() {
        val mpd = """<?xml version="1.0" encoding="utf-8"?>
            |<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT23M42S">
            |  <Period><AdaptationSet mimeType="video/mp4"><Representation bandwidth="467000"/></AdaptationSet></Period>
            |</MPD>
        """.trimMargin()
        assertEquals(StreamDelivery.Manifest(hls = false), classify(mpd))
    }

    @Test
    fun `a format nobody has written a reader for is still a manifest`() {
        // The point of the whole thing: an unknown text format goes to ffmpeg, which may well
        // know it, rather than to disk, where it is certainly wrong.
        assertEquals(
            StreamDelivery.Manifest(hls = false),
            classify("<SmoothStreamingMedia MajorVersion=\"2\" Duration=\"14220000000\"></SmoothStreamingMedia>"),
        )
    }

    @Test
    fun `an mp4 is saved as it arrives`() {
        // A length, then the brand. The NULs in the length are what mark it as binary.
        val mp4 = byteArrayOf(0x00, 0x00, 0x00, 0x18) + "ftypmp42".toByteArray()
        assertEquals(StreamDelivery.Container, classify(mp4))
    }

    @Test
    fun `matroska is saved as it arrives`() {
        val mkv = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00, 0x00, 0x00)
        assertEquals(StreamDelivery.Container, classify(mkv))
    }

    @Test
    fun `a transport stream is saved as it arrives`() {
        // Sync byte, 187 bytes of payload, sync byte. One of them could be coincidence.
        val ts = ByteArray(400).also {
            it[0] = 0x47
            it[188] = 0x47
            it[1] = 0x40
        }
        assertEquals(StreamDelivery.Container, classify(ts))
    }

    @Test
    fun `a web page is refused rather than saved`() {
        val delivery = classify("<!DOCTYPE html><html><head><title>Sign in</title></head></html>")
        assertInstanceOf(StreamDelivery.Unusable::class.java, delivery)
    }

    @Test
    fun `an error in json is refused rather than saved`() {
        assertInstanceOf(StreamDelivery.Unusable::class.java, classify("""{"error":"link expired"}"""))
    }

    @Test
    fun `an empty response is refused`() {
        assertInstanceOf(StreamDelivery.Unusable::class.java, StreamSniffer.classify(ByteArray(0), 0))
    }

    @Test
    fun `text already read as a string is classified the same way`() {
        assertEquals(StreamDelivery.Manifest(hls = true), StreamSniffer.classify("#EXTM3U\n#EXTINF:4.0,\n"))
        assertEquals(StreamDelivery.Manifest(hls = false), StreamSniffer.classify("<MPD></MPD>"))
    }

    @Test
    fun `binary decoded into a string is not mistaken for a manifest`() {
        // What reading an mp4 as text leaves behind. It must not be handed to ffmpeg as a
        // manifest, and it must certainly not be called a web page.
        val decoded = String(byteArrayOf(0x00, 0x00, 0x00, 0x18) + "ftypmp42".toByteArray(), Charsets.UTF_8)
        assertEquals(StreamDelivery.Container, StreamSniffer.classify(decoded))
    }
}
