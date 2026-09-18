package eu.kanade.tachiyomi.data.download.anime

/**
 * What the far end of a video url turned out to be.
 *
 * The question a downloader has to answer before it can do anything, and the one this app used
 * to answer backwards. It asked "is this an HLS playlist?" and treated *everything else* as a
 * video file to be written to disk — so the day a source served a DASH manifest instead, eight
 * kilobytes of XML were saved as the episode and given the downloaded tick. The same would have
 * happened for Smooth Streaming, for a login page, and for every format nobody had thought of
 * yet: one silent failure per streaming format, fixed one extension at a time.
 *
 * So the question is asked the other way round. Media is binary; a manifest is text. Bytes that
 * are not text are written to disk, and text is handed to ffmpeg, which speaks HLS, DASH and
 * Smooth Streaming and can be given a format it has never been given before without this file
 * changing. What is left — a web page, an error, a login wall — is neither, and fails out loud.
 */
sealed interface StreamDelivery {

    /**
     * Media bytes, to be written to disk as they arrive.
     *
     * The cheap path: no muxing, no second connection, no ffmpeg.
     */
    data object Container : StreamDelivery

    /**
     * Text describing a stream that lives somewhere else. ffmpeg fetches what it names.
     *
     * @param hls whether it is HLS specifically. Not a classification so much as a key to the
     * fast path: an m3u8 is the one this app can read itself, and reading it is what lets the
     * segments be fetched in parallel. Everything else is correct but ordinary.
     */
    data class Manifest(val hls: Boolean) : StreamDelivery

    /** Neither. [reason] is for the log, and is what a failed download has to be able to say. */
    data class Unusable(val reason: String) : StreamDelivery
}

/**
 * Decides what arrived from its first bytes.
 *
 * The bytes, never the url or the content type. Sources serve playlists from paths ending in
 * .mp4 and label them as octet-streams, and the ones that do are not doing it by accident.
 */
object StreamSniffer {

    /**
     * @param head the first bytes of the response — [SNIFF_BYTES] of them is enough for every
     * test below.
     * @param length how many of [head] were actually filled.
     */
    fun classify(head: ByteArray, length: Int): StreamDelivery {
        if (length == 0) return StreamDelivery.Unusable("the response was empty")

        // A container that names itself. Decisive, and checked first: an mp4 with an unlucky
        // run of ASCII in its header is still an mp4.
        if (hasContainerMagic(head, length)) return StreamDelivery.Container
        if (!looksTextual(head, length)) return StreamDelivery.Container

        return ofText(String(head, 0, length, Charsets.UTF_8))
    }

    /**
     * The same question asked of something already read as text.
     *
     * For the paths that fetch a manifest as a string before the downloader ever sees it. A
     * string that came from binary is unreadable rather than wrong — the decoding leaves
     * control characters behind — so the same test rules it out.
     */
    fun classify(text: String): StreamDelivery {
        if (text.isEmpty()) return StreamDelivery.Unusable("the response was empty")
        val control = text.take(TEXT_WINDOW).any { it.code < 0x20 && it != '\t' && it != '\n' && it != '\r' }
        return if (control) StreamDelivery.Container else ofText(text)
    }

    private fun ofText(text: String): StreamDelivery {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith(PLAYLIST_MARKER) -> StreamDelivery.Manifest(hls = true)
            // A page rather than a stream, which is what a source hands over when a link has
            // expired, when it wants a login, or when the extension resolved to the wrong url
            // altogether. Saving it would be saving the error.
            looksLikeWebPage(trimmed) -> StreamDelivery.Unusable("the response was a web page, not a video")
            looksLikeJson(trimmed) -> StreamDelivery.Unusable("the response was JSON, not a video")
            // Everything else textual: XML manifests — DASH, Smooth Streaming — and whatever
            // comes next. ffmpeg is asked rather than guessed at, and says so if it cannot.
            else -> StreamDelivery.Manifest(hls = false)
        }
    }

    /**
     * Whether the first bytes are text.
     *
     * Control bytes are the tell. Every container in use starts with a length, a magic number
     * or a sync byte, and those put NULs and high bytes in the first few dozen bytes; no
     * manifest has one in its first line. Tabs, newlines and carriage returns are text.
     */
    private fun looksTextual(head: ByteArray, length: Int): Boolean =
        (0 until minOf(length, TEXT_WINDOW)).none { index ->
            val byte = head[index].toInt() and 0xFF
            byte < 0x20 && byte != 0x09 && byte != 0x0A && byte != 0x0D
        }

    private fun hasContainerMagic(head: ByteArray, length: Int): Boolean = when {
        // ISO base media: mp4, m4v, mov, and the fragmented ones a manifest points at.
        length >= 8 && head.matches(4, "ftyp") -> true
        // Matroska and WebM share EBML's magic.
        length >= 4 && head.matches(0, EBML_MAGIC) -> true
        length >= 3 && head.matches(0, "FLV") -> true
        length >= 4 && head.matches(0, "OggS") -> true
        length >= 4 && head.matches(0, "RIFF") -> true
        // MPEG program stream.
        length >= 4 && head.matches(0, MPEG_PS_MAGIC) -> true
        // MPEG transport stream: a sync byte every 188. One of them could be a coincidence.
        length > TS_PACKET_SIZE && head[0].toInt() == TS_SYNC && head[TS_PACKET_SIZE].toInt() == TS_SYNC -> true
        else -> false
    }

    private fun ByteArray.matches(offset: Int, ascii: String): Boolean =
        matches(offset, ascii.toByteArray(Charsets.US_ASCII))

    private fun ByteArray.matches(offset: Int, magic: ByteArray): Boolean =
        magic.indices.all { this[offset + it] == magic[it] }

    private fun looksLikeWebPage(trimmed: String): Boolean {
        val start = trimmed.take(WEB_PAGE_WINDOW).lowercase()
        return start.startsWith("<!doctype html") || start.startsWith("<html") || start.contains("<head>")
    }

    private fun looksLikeJson(trimmed: String): Boolean =
        trimmed.startsWith("{") || trimmed.startsWith("[")

    /** Enough to hold the first line of a manifest whatever whitespace precedes it, and two
     *  transport-stream packets' worth of sync bytes. */
    const val SNIFF_BYTES = 256

    /** EBML, which is what Matroska and WebM both open with. */
    private val EBML_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

    /** An MPEG program stream's pack header. */
    private val MPEG_PS_MAGIC = byteArrayOf(0x00, 0x00, 0x01, 0xBA.toByte())

    private const val PLAYLIST_MARKER = "#EXTM3U"
    private const val TEXT_WINDOW = 64
    private const val WEB_PAGE_WINDOW = 64
    private const val TS_PACKET_SIZE = 188
    private const val TS_SYNC = 0x47
}
