package eu.kanade.tachiyomi.ui.animeplayer

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.net.toUri
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import eu.kanade.tachiyomi.animesource.model.Track as SourceTrack

/**
 * Surface that hosts an mpv instance.
 *
 * Written against the MPVLib API rather than ported from Aniyomi's AniyomiMPVView, which
 * carries its gesture handling, picture-in-picture and track selection along with it.
 * This is the core only: create mpv, hand it a surface, play a file.
 */
class ZenyomiMPVView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs),
    SurfaceHolder.Callback {

    private var initialised = false

    /**
     * mpv's Android video output asserts on a window id, so loading a file before the
     * surface exists aborts the process:
     *   android_common.c: assertion "vo->opts->WinID != 0 && vo->opts->WinID != -1" failed
     * The file is held here until surfaceCreated attaches the surface.
     */
    private var pendingFile: String? = null
    private var surfaceReady = false
    private var pendingResumeAt = 0

    /**
     * Subtitles and audio that live in their own files rather than in the container.
     *
     * They cannot be handed over with the file: mpv rejects `sub-add` until something is
     * loaded, so they are kept here and added when mpv reports the file open.
     */
    private var externalSubtitles: List<SourceTrack> = emptyList()
    private var externalAudio: List<SourceTrack> = emptyList()

    /**
     * Called when mpv gives up on a file, with whatever it said about why.
     *
     * A dead mirror is the single most common way an episode fails, and `keep-open` means mpv
     * simply sits on a black frame when it happens. Without this the screen is identical to a
     * player that is still loading, forever.
     */
    var onPlaybackError: ((String?) -> Unit)? = null

    /** The last "HTTP error" mpv logged, which is the useful half of a failure. */
    private var lastHttpError: String? = null

    /** Held open for as long as mpv reads from them. */
    private val openFds = mutableListOf<ParcelFileDescriptor>()

    /** Observers are registered against MPVLib globally, so keep ours to remove them later. */
    private val observers = mutableListOf<MPVLib.EventObserver>()

    /**
     * @param audioLanguages / [subtitleLanguages] mpv's `alang` and `slang`: comma separated
     * codes in order of preference. Empty is left unset, because mpv reads an empty list as
     * "prefer no track at all".
     * @param speedPercent playback speed, 100 being normal.
     * @param httpHeaders "Name: value" pairs for mpv's `http-header-fields`.
     */
    fun initialise(
        configDir: File,
        audioLanguages: String = "",
        subtitleLanguages: String = "",
        speedPercent: Int = 100,
        httpHeaders: List<String> = emptyList(),
    ) {
        if (initialised) return
        initialised = true
        configDir.mkdirs()
        copyAssets(configDir)
        holder.addCallback(this)

        // All of libmpv is driven from one thread; see runOnMpvThread.
        postToMpv {
            MPVLib.create(context, "v")
            // Registered before anything else: without it a rejected option or a failed
            // loadfile is silent, and mpv only ever complains on its own log.
            MPVLib.setOptionString("msg-level", "all=v")
            MPVLib.addLogObserver { prefix, _, text ->
                if (text.contains("HTTP error")) lastHttpError = text.trim()
                logcat(LogPriority.DEBUG) { "mpv [$prefix] $text".trim() }
            }
            MPVLib.setOptionString("config", "yes")
            MPVLib.setOptionString("config-dir", configDir.path)
            // Hardware decoding where the device offers it, falling back to software.
            MPVLib.setOptionString("hwdec", "auto-safe")
            MPVLib.setOptionString("vo", "gpu")
            MPVLib.setOptionString("gpu-context", "android")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.setOptionString("keep-open", "always")
            MPVLib.setOptionString("ao", "audiotrack")
            // mpv picks its subtitle font up from the config dir by name. Without it libass
            // reports "failed to find any fallback with glyph" and draws nothing at all, so a
            // correctly selected subtitle track was still an episode with no subtitles.
            MPVLib.setOptionString("sub-font-provider", "none")
            // mpv sizes and places subtitles against the *window* by default, which is right
            // on a desktop where the window is the video. Here the window is a whole portrait
            // phone screen and the video a band across the middle of it, so the defaults drew
            // text tall enough to run off the bottom edge and across the seek bar. Both are
            // tied to the video rectangle instead.
            MPVLib.setOptionString("sub-scale-by-window", "no")
            MPVLib.setOptionString("sub-use-margins", "no")
            MPVLib.setOptionString("sub-ass-force-margins", "no")
            // Android ships no CA bundle mpv can read, so without this its TLS is unverified.
            MPVLib.setOptionString("tls-verify", "yes")
            MPVLib.setOptionString("tls-ca-file", File(configDir, CA_BUNDLE).path)
            // There is no youtube-dl here for its hook to call, and it runs on every file.
            MPVLib.setOptionString("ytdl", "no")
            // mpv's desktop defaults buffer far more than a phone should hold.
            MPVLib.setOptionString("demuxer-max-bytes", DEMUXER_CACHE_BYTES.toString())
            MPVLib.setOptionString("demuxer-max-back-bytes", DEMUXER_CACHE_BYTES.toString())
            // These have to be options rather than properties: mpv applies them while opening
            // a file, so setting them after init does nothing until the *next* file.
            if (audioLanguages.isNotBlank()) MPVLib.setOptionString("alang", audioLanguages)
            if (subtitleLanguages.isNotBlank()) MPVLib.setOptionString("slang", subtitleLanguages)
            MPVLib.setOptionString("speed", (speedPercent.coerceIn(25, 400) / 100.0).toString())
            // Video hosts that check the Referer answer mpv's bare request with 403, so the
            // headers the source resolved the video with have to travel with it.
            if (httpHeaders.isNotEmpty()) {
                MPVLib.setOptionString("http-header-fields", httpHeaders.toMpvList())
            }
            MPVLib.init()

            MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        }

        addObserver(
            PlaybackObserver(
                onFileLoaded = {
                    lastHttpError = null
                    addExternalTracks()
                },
                onFailure = { reason ->
                    onPlaybackError?.invoke(lastHttpError ?: reason)
                },
            ),
        )

        // AndroidView hands over a SurfaceView whose surface may already exist, and
        // surfaceCreated only fires for surfaces created after the callback is added.
        // Without this the pending file waits forever and the screen stays black.
        if (holder.surface?.isValid == true) {
            attach(holder)
        }
    }

    /**
     * Puts mpv's own data files where it looks for them.
     *
     * Both ship inside the mpv library's aar and end up in the app's assets, but mpv reads
     * them from its config directory as plain files: it has no idea Android assets exist.
     */
    private fun copyAssets(configDir: File) {
        listOf(SUBTITLE_FONT, CA_BUNDLE).forEach { name ->
            val target = File(configDir, name)
            if (target.exists() && target.length() > 0) return@forEach
            runCatching {
                context.assets.open(name).use { source ->
                    target.outputStream().use { source.copyTo(it) }
                }
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Could not unpack $name for mpv" }
            }
        }
    }

    fun addObserver(observer: MPVLib.EventObserver) {
        observers += observer
        MPVLib.addObserver(observer)
    }

    /**
     * @param mpvArgs options the source asked for, applied to this file only. They are set
     * rather than passed to loadfile because a source is free to name any mpv option, and a
     * single rejected one in the loadfile argument makes mpv refuse the whole command.
     */
    fun playFile(
        uri: String,
        resumeAt: Int = 0,
        subtitleTracks: List<SourceTrack> = emptyList(),
        audioTracks: List<SourceTrack> = emptyList(),
        mpvArgs: List<Pair<String, String>> = emptyList(),
    ) {
        pendingResumeAt = resumeAt
        externalSubtitles = subtitleTracks
        externalAudio = audioTracks
        if (mpvArgs.isNotEmpty()) {
            postToMpv {
                mpvArgs.forEach { (name, value) ->
                    val code = MPVLib.setOptionString(name, value)
                    if (code < 0) logcat(LogPriority.WARN) { "mpv refused source option $name=$value" }
                }
            }
        }
        if (surfaceReady) {
            load(uri, resumeAt)
        } else {
            pendingFile = uri
        }
    }

    /**
     * Adds the side-car tracks, once mpv has a file open to attach them to.
     *
     * Added with `auto` rather than `select` so mpv's own `slang`/`alang` preference still
     * decides. When that leaves nothing selected — which is the common case, because a source
     * labels a track "English" where mpv expects "eng" — the first external subtitle is
     * selected by hand, since a stream with Japanese audio and no subtitle on screen is not
     * something anyone asked for.
     */
    private fun addExternalTracks() {
        val subtitles = externalSubtitles
        val audio = externalAudio
        if (subtitles.isEmpty() && audio.isEmpty()) return
        externalSubtitles = emptyList()
        externalAudio = emptyList()

        postToMpv {
            subtitles.forEach { track ->
                MPVLib.command(arrayOf("sub-add", track.url, "auto", track.lang, track.lang))
            }
            audio.forEach { track ->
                MPVLib.command(arrayOf("audio-add", track.url, "auto", track.lang, track.lang))
            }
            // Asked of the track list rather than of `sid`: that property is a choice — it
            // holds "no" and "auto" as well as a number — so reading it as an integer always
            // fails, and the failure is indistinguishable from "nothing is selected".
            val subtitleTracks = tracks().filter { it.isSubtitle }
            if (subtitles.isNotEmpty() && subtitleTracks.none { it.selected }) {
                subtitleTracks.firstOrNull()?.let { selectSubtitle(it.id) }
            }
        }
    }

    /**
     * mpv takes the start position as a loadfile option, which avoids a visible seek.
     *
     * The command is `loadfile <url> [<flags> [<index> [<options>]]]`, so the options belong in
     * the *fourth* argument. Passing them third made mpv try to read "start=71" as the integer
     * index and refuse the whole command — the file simply never loaded, so every episode with
     * a saved position opened to a black screen. INSERT_AT_END is the documented default index.
     */
    private fun load(uri: String, resumeAt: Int) {
        val target = resolve(uri) ?: return
        postToMpv {
            if (resumeAt > 0) {
                MPVLib.command(arrayOf("loadfile", target, "replace", INSERT_AT_END, "start=$resumeAt"))
            } else {
                MPVLib.command(arrayOf("loadfile", target))
            }
        }
    }

    /**
     * mpv opens paths and network urls, but not Android's content:// scheme, which is
     * what the storage framework hands back for a file the user granted access to. Those
     * are resolved to a file descriptor and passed as fd://, the form mpv understands.
     */
    private fun resolve(uri: String): String? {
        if (!uri.startsWith("content://")) return uri
        return runCatching {
            val parcelFd = context.contentResolver.openFileDescriptor(uri.toUri(), "r")
                ?: return@runCatching null
            openFds += parcelFd
            "fd://${parcelFd.fd}"
        }.getOrNull()
    }

    /**
     * Everything a tap can reach queues onto the mpv thread instead of calling libmpv here.
     *
     * libmpv's property reads, property writes and commands are all synchronous against mpv's
     * core, so one made while the core is busy blocks the caller — and the core is busy for as
     * long as a network stream takes to answer, which can be forever when a host stalls. These
     * are invoked from gesture and button handlers, which run on the main thread, and a main
     * thread blocked past five seconds is an ANR: a tap on pause became "Zenyomi isn't
     * responding". Nothing here needs an answer, so nothing here waits for one.
     */
    fun togglePause() = postToMpv {
        val paused = MPVLib.getPropertyBoolean("pause") ?: return@postToMpv
        MPVLib.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(seconds: Int) = postToMpv {
        MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))
    }

    /** Relative, so a double tap never has to read time-pos to know where it started. */
    fun seekBy(seconds: Int) = postToMpv {
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative"))
    }

    val isReady: Boolean get() = initialised

    val timePos: Int? get() = MPVLib.getPropertyInt("time-pos")
    val duration: Int? get() = MPVLib.getPropertyInt("duration")
    val paused: Boolean? get() = MPVLib.getPropertyBoolean("pause")

    /**
     * Width over height of the video actually being decoded, or null before it is known.
     * Picture-in-picture needs this to size its window; guessing 16:9 would letterbox
     * anything that is not.
     *
     * A blocking read, like [timePos] and the rest: call it from the polling loop, never from
     * a button.
     */
    val videoAspect: Float?
        get() {
            val width = MPVLib.getPropertyInt("width") ?: return null
            val height = MPVLib.getPropertyInt("height") ?: return null
            if (width <= 0 || height <= 0) return null
            return width.toFloat() / height
        }

    /**
     * The tracks mpv found in the current file.
     *
     * mpv exposes track-list as a node, which MPVLib cannot hand over directly, so the
     * entries are read one property at a time through the track-list/N/... paths.
     */
    fun tracks(): List<Track> {
        val count = MPVLib.getPropertyInt("track-list/count") ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val type = MPVLib.getPropertyString("track-list/$index/type") ?: return@mapNotNull null
            val id = MPVLib.getPropertyInt("track-list/$index/id") ?: return@mapNotNull null
            Track(
                id = id,
                type = type,
                lang = MPVLib.getPropertyString("track-list/$index/lang"),
                title = MPVLib.getPropertyString("track-list/$index/title"),
                selected = MPVLib.getPropertyBoolean("track-list/$index/selected") ?: false,
            )
        }
    }

    fun selectAudio(trackId: Int?) = postToMpv {
        MPVLib.setPropertyString("aid", trackId?.toString() ?: "no")
    }

    fun selectSubtitle(trackId: Int?) = postToMpv {
        MPVLib.setPropertyString("sid", trackId?.toString() ?: "no")
    }

    data class Track(
        val id: Int,
        val type: String,
        val lang: String?,
        val title: String?,
        val selected: Boolean,
    ) {
        val isAudio: Boolean get() = type == "audio"
        val isSubtitle: Boolean get() = type == "sub"

        /** What to show in a picker: the track's own title, else its language, else its id. */
        val label: String get() = title ?: lang ?: "#$id"
    }

    /**
     * Tears mpv down without letting it hang the caller.
     *
     * Every libmpv call queues behind mpv's own core thread, so tearing the player down while
     * it is still opening a file blocks for as long as that takes. Doing that from the main
     * thread — which is where a screen is disposed — is what made backing out of a
     * just-opened episode freeze the UI until Android raised an ANR.
     *
     * The work is handed to a background thread and waited on with a cap. mpv has to finish
     * detaching before the surface goes, so this cannot simply be fired and forgotten, but a
     * bounded wait can never reach the ANR threshold.
     */
    fun release() {
        if (!initialised) return
        initialised = false
        holder.removeCallback(this)
        val fds = openFds.toList()
        openFds.clear()
        val toRemove = observers.toList()
        observers.clear()

        runOnMpvThread {
            toRemove.forEach { MPVLib.removeObserver(it) }
            MPVLib.destroy()
            fds.forEach { runCatching { it.close() } }
        }
    }

    /** Queues [block] on the mpv thread without waiting. Ordering is what matters here. */
    private fun postToMpv(block: () -> Unit) {
        if (!initialised) return
        mpvThread.execute(block)
    }

    /**
     * Runs [block] on the single mpv thread and waits up to [TEARDOWN_TIMEOUT_MS].
     *
     * One thread, so create and destroy can never race each other: libmpv is a process-wide
     * singleton and interleaving those corrupts it.
     */
    private fun runOnMpvThread(block: () -> Unit) {
        val done = mpvThread.submit(block)
        runCatching { done.get(TEARDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
            .onFailure { logcat(LogPriority.WARN) { "mpv teardown did not finish in time" } }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = attach(holder)

    private fun attach(holder: SurfaceHolder) {
        if (surfaceReady) return
        surfaceReady = true
        val surface = holder.surface
        val file = pendingFile
        pendingFile = null
        postToMpv {
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setOptionString("vo", "gpu")
        }
        file?.let { load(it, pendingResumeAt) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        postToMpv { MPVLib.setPropertyString("android-surface-size", "${width}x$height") }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        if (!initialised) return
        // Same reasoning as release(): these are blocking libmpv calls and this runs on the
        // main thread, but mpv must stop drawing before the surface is gone, so the wait is
        // bounded rather than skipped.
        runOnMpvThread {
            MPVLib.setOptionString("vo", "null")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
        }
    }

    /**
     * mpv's list syntax for `http-header-fields`: comma separated, commas inside an item
     * escaped with a backslash.
     *
     * Not the `%<length>%` prefix form mpv documents for its path lists. That form is *not*
     * parsed for this option: mpv took the prefix as part of the field name and sent a header
     * literally called `%8%Origin`, so every host that checks one answered 403 and the player
     * opened to a black screen. Verified against a local server that echoes what arrives.
     */
    private fun List<String>.toMpvList(): String =
        joinToString(",") { it.replace(",", "\\,") }

    /**
     * Watches for the two moments that matter: a file opening, and mpv giving up on one.
     *
     * MPVLib's observer is one wide interface rather than a set of callbacks, so listening for
     * two of them means implementing all seven members. `efEvent` is the library's own "this
     * file ended badly" signal; the plain end-of-file event does not say whether it failed.
     */
    private class PlaybackObserver(
        private val onFileLoaded: () -> Unit,
        private val onFailure: (String?) -> Unit,
    ) : MPVLib.EventObserver {
        override fun event(eventId: Int) {
            if (eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED) onFileLoaded()
        }

        override fun efEvent(error: String?) = onFailure(error)

        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Boolean) = Unit
        override fun eventProperty(property: String, value: String) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
    }

    companion object {
        /** The name mpv looks for in its config dir; it has no other font on Android. */
        private const val SUBTITLE_FONT = "subfont.ttf"
        private const val CA_BUNDLE = "cacert.pem"

        /** 64 MB, matching what Aniyomi settled on for phones. */
        private const val DEMUXER_CACHE_BYTES = 64L * 1024 * 1024

        /** mpv's default playlist index for loadfile, meaning "append". */
        private const val INSERT_AT_END = "-1"

        /**
         * How long the UI thread will wait for mpv to finish tearing down. Well under
         * Android's five second input timeout, so a wedged mpv costs a dropped frame or two
         * rather than an ANR.
         */
        private const val TEARDOWN_TIMEOUT_MS = 1500L

        private val mpvThread: ExecutorService =
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "mpv-lifecycle") }
    }
}
