package eu.kanade.tachiyomi.ui.animeplayer

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.net.toUri
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import `is`.xyz.mpv.MPVLib
import java.io.File

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

    fun initialise(configDir: File) {
        if (initialised) return
        configDir.mkdirs()

        MPVLib.create(context, "v")
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir.path)
        // Hardware decoding where the device offers it, falling back to software.
        MPVLib.setOptionString("hwdec", "auto-safe")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("ao", "audiotrack")
        MPVLib.init()

        MPVLib.observeProperty("time-pos", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("pause", MPVLib.mpvFormat.MPV_FORMAT_FLAG)

        // Without this a failed loadfile is silent: mpv reports it on its own log.
        MPVLib.addLogObserver { prefix, level, text ->
            logcat(LogPriority.DEBUG) { "mpv [$prefix] $text".trim() }
        }
        MPVLib.setOptionString("msg-level", "all=v")

        holder.addCallback(this)
        initialised = true

        // AndroidView hands over a SurfaceView whose surface may already exist, and
        // surfaceCreated only fires for surfaces created after the callback is added.
        // Without this the pending file waits forever and the screen stays black.
        if (holder.surface?.isValid == true) {
            attach(holder)
        }

        // The surface may already exist by the time this runs, and a callback added
        // afterwards is never told about a surface that was created before it. Attach it
        // here, or the file stays pending forever and the screen sits black.
        if (holder.surface?.isValid == true) {
            surfaceCreated(holder)
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

    /** mpv takes the start position as a loadfile option, avoiding a visible seek. */
    private fun load(uri: String, resumeAt: Int) {
        val target = resolve(uri) ?: return
        if (resumeAt > 0) {
            MPVLib.command(arrayOf("loadfile", target, "replace", "start=$resumeAt"))
        } else {
            MPVLib.command(arrayOf("loadfile", target))
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

    fun release() {
        if (!initialised) return
        observer?.let { MPVLib.removeObserver(it) }
        observer = null
        holder.removeCallback(this)
        MPVLib.destroy()
        openFds.forEach { runCatching { it.close() } }
        openFds.clear()
        initialised = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) = attach(holder)

    private fun attach(holder: SurfaceHolder) {
        if (surfaceReady) return
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setOptionString("vo", "gpu")
        surfaceReady = true
        pendingFile?.let {
            pendingFile = null
            load(it, pendingResumeAt)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        MPVLib.setOptionString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }
}
