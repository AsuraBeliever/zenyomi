package eu.kanade.tachiyomi.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import eu.kanade.tachiyomi.data.torrent.TorrentAddon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Starts TorrServer through the torrent add-on and asks it for its version.
 *
 * Debug-only, like [AnimeSourceProbeActivity]: it proves the whole chain — signature check,
 * binding, the add-on launching its executable, HTTP on localhost — before any UI depends on it.
 *
 *     adb shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.TorrentAddonProbeActivity
 *     adb logcat -s TorrentAddonProbe
 *
 * TorrServer stays up while this activity is open; finishing it (or killing the app) releases
 * the binding, and the add-on stops TorrServer.
 */
class TorrentAddonProbeActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connection: TorrentAddon.Connection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply { textSize = 18f }
        setContentView(status)

        val addon = TorrentAddon(this)
        scope.launch {
            val result = try {
                val state = addon.state()
                report(status, "add-on ${addon.packageName}: $state")
                val connected = addon.connect().also { connection = it }
                val echo = withContext(Dispatchers.IO) {
                    URI("${connected.baseUrl}/echo").toURL().readText().trim()
                }
                "echo=$echo port=${connected.port} version=${connected.version}"
            } catch (e: Exception) {
                "failed: ${e.message}"
            }
            report(status, result)
        }
    }

    override fun onDestroy() {
        connection?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun report(view: TextView, line: String) {
        Log.i(TAG, line)
        view.append(line + "\n")
    }

    private companion object {
        const val TAG = "TorrentAddonProbe"
    }
}
