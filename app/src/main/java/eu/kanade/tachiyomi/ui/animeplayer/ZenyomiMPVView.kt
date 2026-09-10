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

    /** Held open for as long as mpv reads from them. */
    private val openFds = mutableListOf<ParcelFileDescriptor>()

    /** Observers are registered against MPVLib globally, so keep ours to remove it later. */
    private var observer: MPVLib.EventObserver? = null

    /**
     * @param audioLanguages / [subtitleLanguages] mpv's `alang` and `slang`: comma separated
     * codes in order of preference. Empty is left unset, because mpv reads an empty list as
     * "prefer no track at all".
     * @param speedPercent playback speed, 100 being normal.
     */
    fun initialise(
        configDir: File,
        audioLanguages: String = "",
        subtitleLanguages: String = "",
        speedPercent: Int = 100,
    ) {
        if (initialised) return
        initialised = true
        configDir.mkdirs()
        holder.addCallback(this)

        // All of libmpv is driven from one thread; see runOnMpvThread.
        postToMpv {
            MPVLib.create(context, "v")
            // Registered before anything else: without it a rejected option or a failed
            // loadfile is silent, and mpv only ever complains on its own log.
            MPVLib.setOptionString("msg-level", "all=v")
            MPVLib.addLogObserver { prefix, _, text ->
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
            // These have to be options rather than properties: mpv applies them while opening
            // a file, so setting them after init does nothing until the *next* file.
            if (audioLanguages.isNotBlank()) MPVLib.setOptionString("alang", audioLanguages)
            if (subtitleLanguages.isNotBlank()) MPVLib.setOptionString("slang", subtitleLanguages)
            MPVLib.setOptionString("speed", (speedPercent.coerceIn(25, 400) / 100.0).toString())
            MPVLib.init()

            MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_INT64)
            MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        }

        // AndroidView hands over a SurfaceView whose surface may already exist, and
        // surfaceCreated only fires for surfaces created after the callback is added.
        // Without this the pending file waits forever and the screen stays black.
        if (holder.surface?.isValid == true) {
            attach(holder)
        }
    }

    fun addObserver(observer: MPVLib.EventObserver) {
        this.observer = observer
        MPVLib.addObserver(observer)
    }

    fun playFile(uri: String, resumeAt: Int = 0) {
        pendingResumeAt = resumeAt
        if (surfaceReady) {
            load(uri, resumeAt)
        } else {
            pendingFile = uri
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

    fun togglePause() {
        val paused = MPVLib.getPropertyBoolean("pause") ?: return
        MPVLib.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(seconds: Int) = MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))

    val isReady: Boolean get() = initialised

    val timePos: Int? get() = MPVLib.getPropertyInt("time-pos")
    val duration: Int? get() = MPVLib.getPropertyInt("duration")
    val paused: Boolean? get() = MPVLib.getPropertyBoolean("pause")

    /**
     * Width over height of the video actually being decoded, or null before it is known.
     * Picture-in-picture needs this to size its window; guessing 16:9 would letterbox
     * anything that is not.
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

    fun selectAudio(trackId: Int?) = MPVLib.setPropertyString("aid", trackId?.toString() ?: "no")

    fun selectSubtitle(trackId: Int?) = MPVLib.setPropertyString("sid", trackId?.toString() ?: "no")

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
        val toRemove = observer
        observer = null

        runOnMpvThread {
            toRemove?.let { MPVLib.removeObserver(it) }
            MPVLib.destroy()
            fds.forEach { runCatching { it.close() } }
        }
    }

    /** Queues [block] on the mpv thread without waiting. Ordering is what matters here. */
    private fun postToMpv(block: () -> Unit) {
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

    companion object {
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
