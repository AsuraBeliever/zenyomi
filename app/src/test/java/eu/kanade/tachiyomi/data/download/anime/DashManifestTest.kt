package eu.kanade.tachiyomi.data.download.anime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The reader that decides whether a DASH download takes the fast path.
 *
 * Worth pinning because both of its failure modes are quiet. Reading a manifest wrongly means
 * fetching the wrong urls, and the download fails somewhere in ffmpeg with an error about a
 * file it never names; declining a manifest it could have read means a download that quietly
 * takes seven minutes instead of one, which nobody reports as a bug.
 *
 * The manifests here are written by hand rather than taken from a source: they are the shapes
 * the format allows, and a real one belongs to whoever served it.
 */
class DashManifestTest {

    private val url = "https://example.invalid/media/episode.mpd"

    /** Read, or say plainly that the reader declined — which is a different failure. */
    private fun read(manifest: String) =
        DashManifest.read(manifest, url) ?: error("the reader declined this manifest")

    /** The shape a live-profile encoder produces: a template numbered off an explicit timeline. */
    private fun timelineManifest(repeats: Int = 2) = """
        <?xml version="1.0" encoding="utf-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-live:2011"
             type="static" mediaPresentationDuration="PT23M59.9S">
          <Period id="0" start="PT0.0S">
            <AdaptationSet id="0" contentType="video" maxWidth="1280" maxHeight="720" lang="jpn">
              <Representation id="0" mimeType="video/mp4" bandwidth="740469" width="1280" height="720">
                <SegmentTemplate timescale="24000" initialization="stream_init${'$'}RepresentationID${'$'}.m4s"
                                 media="${'$'}RepresentationID${'$'}-seg_${'$'}Number%08d${'$'}.m4s" startNumber="1">
                  <SegmentTimeline>
                    <S t="0" d="120120" r="$repeats" />
                    <S d="85085" />
                  </SegmentTimeline>
                </SegmentTemplate>
              </Representation>
            </AdaptationSet>
            <AdaptationSet id="1" contentType="audio" lang="jpn">
              <Representation id="1" mimeType="audio/mp4" bandwidth="96000">
                <SegmentTemplate timescale="44100" initialization="stream_init${'$'}RepresentationID${'$'}.m4s"
                                 media="${'$'}RepresentationID${'$'}-seg_${'$'}Number%08d${'$'}.m4s" startNumber="1">
                  <SegmentTimeline>
                    <S t="0" d="219114" r="$repeats" />
                  </SegmentTimeline>
                </SegmentTemplate>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun `a timeline is walked into one url per segment, init first`() {
        val presentation = read(timelineManifest(repeats = 2))
        val video = presentation.videos.single()

        // Three from the repeated entry, one from the last: four segments, plus the init.
        assertEquals(5, video.urls.size)
        assertEquals("https://example.invalid/media/stream_init0.m4s", video.urls.first())
        assertEquals("https://example.invalid/media/0-seg_00000001.m4s", video.urls[1])
        assertEquals("https://example.invalid/media/0-seg_00000004.m4s", video.urls.last())
    }

    @Test
    fun `the picture and the sound are told apart, with their languages`() {
        val presentation = read(timelineManifest())
        assertEquals(1, presentation.videos.size)
        assertEquals(DashStream.Kind.AUDIO, presentation.streams.last().kind)
        assertEquals("jpn", presentation.streams.last().lang)
        assertEquals(1280, presentation.videos.single().width)
        assertEquals(720, presentation.videos.single().height)
    }

    @Test
    fun `the size comes from the declared bitrate and length`() {
        val presentation = read(timelineManifest())
        assertEquals(1439.9, presentation.durationSeconds!!, 0.01)
        // 740469 bits per second over 23:59.9, in bytes — about 133 MB, which is the number
        // that goes on the progress bar. The real file for this episode came to 155 MB.
        val picture = presentation.estimatedBytes(presentation.videos.single())!!
        assertEquals(133_274_264.0, picture.toDouble(), 1_000.0)
        assertEquals(17_278_800.0, presentation.audioBytes.toDouble(), 1_000.0)
    }

    @Test
    fun `a fixed segment length is counted out of the presentation length`() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v" mimeType="video/mp4" bandwidth="1000" width="640" height="360">
                    <SegmentTemplate timescale="1" duration="4" startNumber="1"
                                     initialization="init.mp4" media="seg-${'$'}Number${'$'}.m4s" />
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val video = read(manifest).videos.single()
        // Ten seconds in four-second segments is three of them, the last one short.
        assertEquals(
            listOf("init.mp4", "seg-1.m4s", "seg-2.m4s", "seg-3.m4s").map {
                "https://example.invalid/media/$it"
            },
            video.urls,
        )
    }

    @Test
    fun `an explicit list of segments is read as it stands`() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT8S">
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v" mimeType="video/mp4" bandwidth="1000" width="640" height="360">
                    <SegmentList>
                      <Initialization sourceURL="head.mp4"/>
                      <SegmentURL media="one.m4s"/>
                      <SegmentURL media="two.m4s"/>
                    </SegmentList>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val video = read(manifest).videos.single()
        assertEquals(
            listOf("head.mp4", "one.m4s", "two.m4s").map { "https://example.invalid/media/$it" },
            video.urls,
        )
    }

    @Test
    fun `a representation that is one file is that one file`() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT8S">
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v" mimeType="video/mp4" bandwidth="1000" width="640" height="360">
                    <BaseURL>whole.mp4</BaseURL>
                    <SegmentBase indexRange="0-900"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val video = read(manifest).videos.single()
        assertEquals(listOf("https://example.invalid/media/whole.mp4"), video.urls)
    }

    @Test
    fun `a base url moves where the segments are looked for`() {
        val manifest = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT8S">
              <BaseURL>https://cdn.example.invalid/x/</BaseURL>
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v" mimeType="video/mp4" bandwidth="1000" width="640" height="360">
                    <SegmentTemplate timescale="1" duration="4" initialization="i.mp4" media="s-${'$'}Number${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val video = read(manifest).videos.single()
        assertEquals("https://cdn.example.invalid/x/i.mp4", video.urls.first())
    }

    @Test
    fun `a live manifest is declined`() {
        assertNull(DashManifest.read(timelineManifest().replace("""type="static"""", """type="dynamic""""), url))
    }

    @Test
    fun `a segment that repeats until the stream ends is declined`() {
        assertNull(DashManifest.read(timelineManifest(repeats = -1), url))
    }

    @Test
    fun `several periods are declined rather than guessed at`() {
        val manifest = timelineManifest().replace("</Period>", "</Period><Period id=\"1\"></Period>")
        assertNull(DashManifest.read(manifest, url))
    }

    @Test
    fun `a manifest with no picture in it is declined`() {
        val audioOnly = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT8S">
              <Period>
                <AdaptationSet contentType="audio">
                  <Representation id="a" mimeType="audio/mp4" bandwidth="96000">
                    <SegmentTemplate timescale="1" duration="4" initialization="i.m4s" media="a-${'$'}Number${'$'}.m4s"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        assertNull(DashManifest.read(audioOnly, url))
    }

    @Test
    fun `something that is not a manifest at all is declined`() {
        assertNull(DashManifest.read("#EXTM3U\n#EXTINF:4.0,\n000.ts\n", url))
        assertNull(DashManifest.read("<html><body>Sign in</body></html>", url))
    }

    @Test
    fun `durations are read in the shapes a manifest writes them`() {
        assertEquals(1439.9, DashManifest.isoSeconds("PT23M59.9S")!!, 0.001)
        assertEquals(3600.0, DashManifest.isoSeconds("PT1H")!!, 0.001)
        assertEquals(5025.5, DashManifest.isoSeconds("PT1H23M45.5S")!!, 0.001)
        assertNull(DashManifest.isoSeconds(null))
        assertNull(DashManifest.isoSeconds("not a duration"))
    }

    @Test
    fun `the quality asked for is the one picked, or the closest below it`() {
        val streams = listOf(720, 480, 360).map {
            DashStream(DashStream.Kind.VIDEO, null, bandwidth = null, width = null, height = it, urls = emptyList())
        }
        assertEquals(480, streams.pickByHeight(480)?.height)
        assertEquals(480, streams.pickByHeight(600)?.height)
        assertEquals(720, streams.pickByHeight(null)?.height)
        // Below everything on offer: the smallest there is, rather than nothing.
        assertEquals(360, streams.pickByHeight(144)?.height)
    }
}
