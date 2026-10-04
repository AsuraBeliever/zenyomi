package eu.kanade.tachiyomi.data.torrent

import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.animeextension.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * Installs or updates the torrent add-on (docs/adr/0008) from the release of this build's own
 * version, with the installer the anime extensions use.
 *
 * The install outlives the screen that started it — the system's confirmation takes the viewer
 * out of the app — so its [step] lives here, in the app's scope, for any screen to follow.
 */
@Inject
@SingleIn(AppScope::class)
class TorrentAddonInstaller(
    private val addon: TorrentAddon,
    private val extensionManager: AnimeExtensionManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _step = MutableStateFlow(InstallStep.Idle)
    val step: StateFlow<InstallStep> = _step.asStateFlow()

    /** Null when this build has nowhere to download the add-on from, or no build fits the device. */
    val downloadUrl: String? by lazy {
        TorrentAddonDownload.url(BuildConfig.TORRENT_ADDON_URL, Build.SUPPORTED_ABIS.asList())
    }

    /** Starts downloading and installing the add-on. False when there is nothing to download. */
    fun install(): Boolean {
        val url = downloadUrl ?: return false
        job?.cancel()
        job = scope.launch {
            extensionManager.installApp(url, addon.packageName)
                // The installer's flow never ends on its own; it is let go once the install is over,
                // which is also what frees the installer's own bookkeeping of it.
                .transformWhile { step ->
                    emit(step)
                    !step.isCompleted()
                }
                .collect { _step.value = it }
        }
        return true
    }
}

object TorrentAddonDownload {

    /** The ABIs the release publishes an add-on for (torrent/build.gradle.kts). */
    val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /**
     * The add-on's url for a device: [template] with `{abi}` replaced by the first of the
     * device's [deviceAbis], in its order of preference, that the release has a build for.
     */
    fun url(template: String, deviceAbis: List<String>): String? {
        if (template.isBlank()) return null
        val abi = deviceAbis.firstOrNull { it in ABIS } ?: return null
        return template.replace("{abi}", abi)
    }
}
