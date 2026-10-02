package app.zenyomi.torrent

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs TorrServer for as long as Zenyomi is bound to it.
 *
 * Zenyomi binds, sends [MSG_START] and gets back [MSG_STARTED] with the port TorrServer listens
 * on, or an error. From then on they talk HTTP on 127.0.0.1. When the last client unbinds —
 * including because Zenyomi's process died — the service is destroyed and takes TorrServer with
 * it, so nothing keeps downloading behind the user's back.
 *
 * The message protocol is mirrored in Zenyomi's `TorrentAddon`; the two must change together.
 */
class TorrServerService : Service() {

    private val worker = Executors.newSingleThreadExecutor()

    // Only touched from [worker].
    private var server: Process? = null
    private var port = 0

    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == MSG_START) {
                val replyTo = msg.replyTo
                worker.execute { reply(replyTo, start()) }
            }
            true
        },
    )

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        worker.execute(::stop)
        worker.shutdown()
        super.onDestroy()
    }

    private fun start(): Bundle {
        val running = server
        if (running != null && running.isAlive) return started()

        return try {
            port = freeLocalPort()
            val dataDir = File(filesDir, "torrserver").apply { mkdirs() }
            val process = ProcessBuilder(
                File(applicationInfo.nativeLibraryDir, "libtorrserver.so").path,
                "--ip",
                LOCALHOST,
                "--port",
                port.toString(),
                "--path",
                dataDir.path,
            )
                .redirectErrorStream(true)
                .start()
            server = process
            drainOutput(process)
            awaitEcho(process)
            started()
        } catch (e: Exception) {
            Log.e(TAG, "TorrServer did not start", e)
            stop()
            Bundle().apply { putString(KEY_ERROR, e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun started() = Bundle().apply {
        putInt(KEY_PORT, port)
        putString(KEY_VERSION, BuildConfig.TORRSERVER_VERSION)
    }

    private fun stop() {
        val process = server ?: return
        server = null
        process.destroy()
        if (!process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        Log.i(TAG, "TorrServer stopped")
    }

    /** Polls `/echo` until TorrServer answers, or fails if it exits or takes too long. */
    private fun awaitEcho(process: Process) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) error("TorrServer exited with code ${process.exitValue()}")
            val echo = runCatching {
                val connection = URI("http://$LOCALHOST:$port/echo").toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 500
                connection.readTimeout = 500
                try {
                    connection.inputStream.bufferedReader().readText().trim()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (echo != null) {
                Log.i(TAG, "TorrServer $echo listening on $LOCALHOST:$port")
                return
            }
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        error("TorrServer did not answer within $START_TIMEOUT_SECONDS s")
    }

    /** Forwards TorrServer's output to logcat. Left unread, a full pipe would block it. */
    private fun drainOutput(process: Process) {
        Thread({
            try {
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach { Log.d(TAG, it) } }
            } catch (_: IOException) {
                // destroy() closes the stream under this read. That is the end of the output,
                // not a failure, and uncaught it would take the whole add-on down.
            }
        }, "torrserver-log").apply { isDaemon = true }.start()
    }

    private fun reply(to: Messenger?, data: Bundle) {
        try {
            to?.send(Message.obtain(null, MSG_STARTED).apply { this.data = data })
        } catch (e: RemoteException) {
            Log.w(TAG, "Client went away before the reply", e)
        }
    }

    private fun freeLocalPort(): Int = ServerSocket(0, 1, InetAddress.getByName(LOCALHOST)).use { it.localPort }

    companion object {
        private const val TAG = "TorrServerService"

        const val MSG_START = 1
        const val MSG_STARTED = 2
        const val KEY_PORT = "port"
        const val KEY_VERSION = "version"
        const val KEY_ERROR = "error"

        private const val LOCALHOST = "127.0.0.1"
        private const val START_TIMEOUT_SECONDS = 20L
        private const val STOP_TIMEOUT_SECONDS = 2L
        private const val POLL_INTERVAL_MILLIS = 100L
    }
}
