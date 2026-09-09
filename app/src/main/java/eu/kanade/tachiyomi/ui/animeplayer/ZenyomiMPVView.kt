package eu.kanade.tachiyomi.ui.animeplayer

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
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

        holder.addCallback(this)
        initialised = true
    }

    fun addObserver(observer: MPVLib.EventObserver) {
        this.observer = observer
        MPVLib.addObserver(observer)
    }

    fun playFile(uri: String) {
        if (surfaceReady) {
            MPVLib.command(arrayOf("loadfile", uri))
        } else {
            pendingFile = uri
        }
    }

    fun togglePause() {
        val paused = MPVLib.getPropertyBoolean("pause") ?: return
        MPVLib.setPropertyBoolean("pause", !paused)
    }

    fun seekTo(seconds: Int) = MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))

    val timePos: Int? get() = MPVLib.getPropertyInt("time-pos")
    val duration: Int? get() = MPVLib.getPropertyInt("duration")
    val paused: Boolean? get() = MPVLib.getPropertyBoolean("pause")

    fun release() {
        if (!initialised) return
        observer?.let { MPVLib.removeObserver(it) }
        observer = null
        holder.removeCallback(this)
        MPVLib.destroy()
        initialised = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        MPVLib.attachSurface(holder.surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setOptionString("vo", "gpu")
        surfaceReady = true
        pendingFile?.let {
            pendingFile = null
            MPVLib.command(arrayOf("loadfile", it))
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
