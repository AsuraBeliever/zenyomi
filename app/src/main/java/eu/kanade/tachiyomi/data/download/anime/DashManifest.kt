package eu.kanade.tachiyomi.data.download.anime

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/** One stream a DASH manifest offers: a quality of the picture, a language of the sound. */
data class DashStream(
    val kind: Kind,
    val lang: String?,
    val bandwidth: Long?,
    val width: Int?,
    val height: Int?,
    /**
     * The initialisation segment first, then the media segments in order.
     *
     * Concatenated in this order they *are* the stream — a fragmented MP4 that ffmpeg demuxes
     * like any other file. That is what makes the fast path possible: the segments can be
     * fetched in any order, several at a time, and joined afterwards.
     */
    val urls: List<String>,
) {
    enum class Kind { VIDEO, AUDIO, TEXT }
}

/** A manifest that could be read, and everything worth knowing from it. */
data class DashPresentation(
    val durationSeconds: Double?,
    val streams: List<DashStream>,
) {
    val videos: List<DashStream> get() = streams.filter { it.kind == DashStream.Kind.VIDEO }

    /** Bitrate times length, the same estimate every other path makes. */
    fun estimatedBytes(stream: DashStream?): Long? {
        val seconds = durationSeconds?.takeIf { it > 0 } ?: return null
        val bits = stream?.bandwidth?.takeIf { it > 0 } ?: return null
        return (bits / BITS_PER_BYTE * seconds).toLong()
    }

    /** What the sound weighs, all languages, since all of them land in the file. */
    val audioBytes: Long
        get() = streams.filter { it.kind == DashStream.Kind.AUDIO }
            .sumOf { estimatedBytes(it) ?: 0L }

    private companion object {
        const val BITS_PER_BYTE = 8
    }
}

/**
 * The parts of an MPD this app needs.
 *
 * The same bargain [HlsPlaylist] strikes, for the other format: not a general DASH reader and
 * not trying to be. It exists for one reason — a stream whose segment urls can be listed is one
 * whose segments can be fetched several at a time, which is the difference between a
 * seventy-second download and a seven-minute one.
 *
 * And it is allowed to give up. Anything it does not recognise returns null and the download
 * goes back to letting ffmpeg fetch the manifest itself, which is slower and always works. That
 * is the whole point of the arrangement in ADR-0006: correctness belongs to ffmpeg, speed
 * belongs to readers like this one, and a reader that declines costs a download nothing.
 */
object DashManifest {

    /**
     * @return what the manifest describes, or null when it uses something not handled here —
     * several periods, segments that repeat until a live stream ends, an addressing scheme
     * nobody has written down yet.
     */
    fun read(manifest: String, manifestUrl: String): DashPresentation? = runCatching {
        val root = parse(manifest) ?: return null
        if (root.tagName != "MPD") return null
        // A live manifest describes something still being made; there is no "all of it" to
        // download and the numbers keep moving.
        if (root.attr("type") == "dynamic") return null

        val duration = isoSeconds(root.attr("mediaPresentationDuration"))
        // One period is what a downloadable episode looks like. Several is an ad break or a
        // concatenation, and joining them is a different job from this one.
        val period = root.children("Period").singleOrNull() ?: return null

        val mpdBase = resolve(manifestUrl, root) ?: return null
        val periodBase = resolve(mpdBase, period) ?: return null

        val streams = period.children("AdaptationSet").flatMap { adaptation ->
            val base = resolve(periodBase, adaptation) ?: return null
            val kind = kindOf(adaptation) ?: return@flatMap emptyList()
            adaptation.children("Representation").map { representation ->
                read(representation, adaptation, kind, base, duration) ?: return null
            }
        }
        streams.takeIf { it.any { stream -> stream.kind == DashStream.Kind.VIDEO } }
            ?.let { DashPresentation(duration, it) }
    }.getOrNull()

    private fun read(
        representation: Element,
        adaptation: Element,
        kind: DashStream.Kind,
        adaptationBase: String,
        durationSeconds: Double?,
    ): DashStream? {
        val base = resolve(adaptationBase, representation) ?: return null
        val id = representation.attr("id").orEmpty()
        val bandwidth = representation.attr("bandwidth")?.toLongOrNull()
        val urls = urlsFor(representation, adaptation, id, bandwidth, base, durationSeconds) ?: return null
        return DashStream(
            kind = kind,
            lang = adaptation.attr("lang") ?: representation.attr("lang"),
            bandwidth = bandwidth,
            width = (representation.attr("width") ?: adaptation.attr("maxWidth"))?.toIntOrNull(),
            height = (representation.attr("height") ?: adaptation.attr("maxHeight"))?.toIntOrNull(),
            urls = urls,
        )
    }

    /**
     * Where a representation's bytes are, by whichever of the three ways it says so.
     *
     * A template that names segments by number or by timestamp, an explicit list of them, or no
     * segments at all — a representation that is simply one file, which is what a `SegmentBase`
     * amounts to once you are downloading the whole thing rather than seeking around it.
     */
    private fun urlsFor(
        representation: Element,
        adaptation: Element,
        id: String,
        bandwidth: Long?,
        base: String,
        durationSeconds: Double?,
    ): List<String>? {
        val template = representation.child("SegmentTemplate") ?: adaptation.child("SegmentTemplate")
        if (template != null) return fromTemplate(template, id, bandwidth, base, durationSeconds)

        val list = representation.child("SegmentList") ?: adaptation.child("SegmentList")
        if (list != null) {
            val init = list.child("Initialization")?.attr("sourceURL")?.let { resolve(base, it) }
            val segments = list.children("SegmentURL").map { it.attr("media")?.let { url -> resolve(base, url) } }
            if (segments.any { it == null }) return null
            return (listOfNotNull(init) + segments.filterNotNull()).takeIf { it.isNotEmpty() }
        }

        // One file, named by the representation's own BaseURL. Downloading it whole is the
        // same bytes a byte-range reader would end up with.
        return representation.child("BaseURL")?.let { listOf(base) }
    }

    private fun fromTemplate(
        template: Element,
        id: String,
        bandwidth: Long?,
        base: String,
        durationSeconds: Double?,
    ): List<String>? {
        val media = template.attr("media") ?: return null
        val start = template.attr("startNumber")?.toLongOrNull() ?: 1L
        val segments = timeline(template, start) ?: byDuration(template, start, durationSeconds) ?: return null

        val init = template.attr("initialization")
            ?.let { fill(it, id, bandwidth, number = null, time = null) }
            ?.let { resolve(base, it) }
        val urls = segments.map { (number, time) ->
            fill(media, id, bandwidth, number, time)?.let { resolve(base, it) } ?: return null
        }
        return (listOfNotNull(init) + urls).takeIf { it.isNotEmpty() }
    }

    /** Segment number and start time, walked out of an explicit timeline. */
    private fun timeline(template: Element, startNumber: Long): List<Pair<Long, Long>>? {
        val entries = template.child("SegmentTimeline")?.children("S") ?: return null
        if (entries.isEmpty()) return null
        val segments = mutableListOf<Pair<Long, Long>>()
        var number = startNumber
        var time = 0L
        entries.forEach { entry ->
            entry.attr("t")?.toLongOrNull()?.let { time = it }
            val length = entry.attr("d")?.toLongOrNull() ?: return null
            // A negative repeat means "and so on until the period ends", which is a live idea:
            // there is no count to enumerate and nothing to download all of.
            val repeats = entry.attr("r")?.toLongOrNull() ?: 0L
            if (repeats < 0 || repeats > MAX_REPEATS) return null
            repeat((repeats + 1).toInt()) {
                segments += number to time
                number++
                time += length
            }
        }
        return segments
    }

    /** The same, counted out of a fixed segment length when there is no timeline. */
    private fun byDuration(template: Element, startNumber: Long, durationSeconds: Double?): List<Pair<Long, Long>>? {
        val length = template.attr("duration")?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val timescale = template.attr("timescale")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val total = durationSeconds?.takeIf { it > 0 } ?: return null
        val count = ceil(total / (length / timescale)).toLong()
        if (count <= 0 || count > MAX_SEGMENTS) return null
        return (0 until count).map { (startNumber + it) to (it * length.toLong()) }
    }

    /**
     * Fills in a template's `$…$` placeholders.
     *
     * `$$` is the spec's way of writing a literal dollar, which is why it is matched first and
     * separately — a url containing one is not a url containing an identifier.
     */
    private fun fill(template: String, id: String, bandwidth: Long?, number: Long?, time: Long?): String? {
        var failed = false
        val filled = IDENTIFIER.replace(template) { match ->
            if (match.value == "$$") return@replace "$"
            val format = match.groupValues[2]
            val value = when (match.groupValues[1]) {
                "RepresentationID" -> return@replace id
                "Bandwidth" -> bandwidth
                "Number" -> number
                "Time" -> time
                else -> null
            }
            if (value == null) {
                failed = true
                return@replace match.value
            }
            if (format.isEmpty()) value.toString() else String.format(Locale.ROOT, format, value)
        }
        return filled.takeUnless { failed }
    }

    private fun kindOf(adaptation: Element): DashStream.Kind? {
        val type = adaptation.attr("contentType")
            ?: adaptation.attr("mimeType")?.substringBefore('/')
            ?: adaptation.child("Representation")?.attr("mimeType")?.substringBefore('/')
        return when (type?.lowercase()) {
            "video" -> DashStream.Kind.VIDEO
            "audio" -> DashStream.Kind.AUDIO
            "text" -> DashStream.Kind.TEXT
            // Thumbnail strips and whatever else a manifest carries alongside. Skipped rather
            // than refused: they are not part of the episode.
            else -> null
        }
    }

    /** This element's `BaseURL` resolved against what it inherited, or that if it has none. */
    private fun resolve(parent: String, element: Element): String? =
        element.child("BaseURL")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { resolve(parent, it) }
            ?: parent

    private fun resolve(base: String, reference: String): String? =
        base.toHttpUrlOrNull()?.resolve(reference)?.toString()

    /** `PT23M59.9S` and friends, in seconds. */
    fun isoSeconds(duration: String?): Double? {
        val match = ISO_DURATION.matchEntire(duration?.trim().orEmpty()) ?: return null
        val (hours, minutes, seconds) = match.destructured
        val total = (hours.toDoubleOrNull() ?: 0.0) * 3600 +
            (minutes.toDoubleOrNull() ?: 0.0) * 60 +
            (seconds.toDoubleOrNull() ?: 0.0)
        return total.takeIf { it > 0 }
    }

    /**
     * Parsed with entity expansion and doctypes off.
     *
     * This is XML fetched from whatever host an extension points at, which is as untrusted as
     * input gets. A parser left at its defaults will follow a doctype out to the network, or
     * expand an entity into gigabytes of memory.
     */
    private fun parse(manifest: String): Element? {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature(DISALLOW_DOCTYPE, true) }
        factory.isExpandEntityReferences = false
        factory.isNamespaceAware = false
        return factory.newDocumentBuilder().parse(manifest.byteInputStream()).documentElement
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .filter { it.tagName == name }

    private fun Element.child(name: String): Element? = children(name).firstOrNull()

    private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"

    private val IDENTIFIER = Regex("""\$\$|\$(RepresentationID|Number|Bandwidth|Time)(%[0-9]*[dxu])?\$""")

    private val ISO_DURATION = Regex(
        """P(?:\d+Y)?(?:\d+M)?(?:\d+D)?T(?:([\d.]+)H)?(?:([\d.]+)M)?(?:([\d.]+)S)?""",
    )

    /** Guards against a manifest that would enumerate into millions of urls. */
    private const val MAX_SEGMENTS = 100_000L
    private const val MAX_REPEATS = 100_000L
}

/**
 * The picture to download for a requested [height], by the rule every other path follows: the
 * one asked for, else the closest below it, else the smallest there is.
 */
fun List<DashStream>.pickByHeight(height: Int?): DashStream? {
    if (isEmpty()) return null
    if (height == null) return maxByOrNull { it.height ?: 0 }
    return firstOrNull { it.height == height }
        ?: filter { (it.height ?: 0) < height }.maxByOrNull { it.height ?: 0 }
        ?: minByOrNull { it.height ?: Int.MAX_VALUE }
}
