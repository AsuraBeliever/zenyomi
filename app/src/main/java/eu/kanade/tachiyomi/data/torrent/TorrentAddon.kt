package eu.kanade.tachiyomi.data.torrent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * The torrent add-on: a separate APK that runs TorrServer (docs/adr/0008).
 *
 * Zenyomi never links TorrServer. It binds to the add-on's service, which starts TorrServer on a
 * free port of 127.0.0.1, and talks to it over HTTP. The binding is what keeps TorrServer alive:
 * closing the [Connection], or this process dying, stops it.
 */
class TorrentAddon(private val context: Context) {

    val packageName: String = BuildConfig.TORRENT_ADDON_PACKAGE

    fun state(): State {
        val info = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return State.NotInstalled
        }
        // A package can take the add-on's name without being ours; only our key is trusted.
        if (context.packageManager.checkSignatures(context.packageName, packageName) !=
            PackageManager.SIGNATURE_MATCH
        ) {
            return State.Untrusted
        }
        return State.Installed(info.versionName.orEmpty())
    }

    /** Binds to the add-on and waits until TorrServer answers. */
    suspend fun connect(): Connection = withContext(Dispatchers.Main) { withTimeout(CONNECT_TIMEOUT) { bind() } }

    // Runs on the main thread, like every callback below, so [bound] needs no locking.
    private suspend fun bind(): Connection {
        val state = state()
        if (state !is State.Installed) throw TorrentAddonException("Torrent add-on not usable: $state")

        return suspendCancellableCoroutine { continuation ->
            var bound = false
            lateinit var serviceConnection: ServiceConnection

            fun unbind() {
                if (bound) context.unbindService(serviceConnection)
                bound = false
            }

            fun fail(message: String) {
                unbind()
                if (continuation.isActive) continuation.resumeWithException(TorrentAddonException(message))
            }

            val replies = Messenger(
                Handler(Looper.getMainLooper()) { msg ->
                    if (msg.what == MSG_STARTED && continuation.isActive) {
                        val error = msg.data.getString(KEY_ERROR)
                        if (error != null) {
                            fail("TorrServer did not start: $error")
                        } else {
                            val connection = Connection(
                                port = msg.data.getInt(KEY_PORT),
                                version = msg.data.getString(KEY_VERSION).orEmpty(),
                                close = { Handler(Looper.getMainLooper()).post(::unbind) },
                            )
                            continuation.resume(connection)
                        }
                    }
                    true
                },
            )

            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    try {
                        Messenger(service).send(Message.obtain(null, MSG_START).apply { replyTo = replies })
                    } catch (e: RemoteException) {
                        fail("Torrent add-on unreachable: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) = fail("Torrent add-on disconnected")
                override fun onBindingDied(name: ComponentName) = fail("Torrent add-on died")
                override fun onNullBinding(name: ComponentName) = fail("Torrent add-on refused the binding")
            }

            val intent = Intent().setComponent(ComponentName(packageName, SERVICE_CLASS))
            bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                // bindService can leave a half-made binding behind even when it returns false.
                runCatching { context.unbindService(serviceConnection) }
                fail("Could not bind to the torrent add-on")
            }

            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post(::unbind)
            }
        }
    }

    sealed interface State {
        data object NotInstalled : State
        data object Untrusted : State
        data class Installed(val versionName: String) : State
    }

    /** A running TorrServer. [close] releases it; once nobody holds one, it stops. */
    class Connection(val port: Int, val version: String, private val close: () -> Unit) : AutoCloseable {
        val baseUrl = "http://127.0.0.1:$port"
        override fun close() = close.invoke()
    }

    companion object {
        private val CONNECT_TIMEOUT = 30.seconds

        // Mirrors app.zenyomi.torrent.TorrServerService; the two must change together.
        private const val SERVICE_CLASS = "app.zenyomi.torrent.TorrServerService"
        private const val MSG_START = 1
        private const val MSG_STARTED = 2
        private const val KEY_PORT = "port"
        private const val KEY_VERSION = "version"
        private const val KEY_ERROR = "error"
    }
}

class TorrentAddonException(message: String) : Exception(message)
