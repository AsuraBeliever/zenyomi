package eu.kanade.presentation.anime

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * What our last sweep of the installed sources found.
 *
 * The anime extension ecosystem decays continuously and quietly: a source stays in the list,
 * installs fine and opens fine, and only fails when you actually ask it for something. Without
 * this, every dead source costs the user the same investigation — install, browse, fail, guess
 * whether it was them or us.
 *
 * The list is a bundled snapshot, not a live check. That is a deliberate trade: probing on
 * demand would mean hitting every site whenever the source list is opened, and a server-side
 * list would mean infrastructure to keep running. A dated snapshot refreshed at each release
 * is worth more than either, provided it is worded as what it is — an observation from a date,
 * not a verdict.
 *
 * Only failures caused by the source itself are recorded. A timeout or a Cloudflare challenge
 * says as much about the network in front of it as about the site, and labelling those would
 * be wrong often enough to make the whole list untrustworthy.
 *
 * Regenerate before a release with the debug probe; see docs/EXTENSIONS_STATUS.md.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeSourceHealth(
    private val context: Context,
) {

    private val report: Report by lazy {
        runCatching {
            context.assets.open(ASSET).use { stream ->
                Json { ignoreUnknownKeys = true }.decodeFromString<Report>(
                    stream.readBytes().decodeToString(),
                )
            }
        }
            .onFailure { logcat(LogPriority.WARN, it) { "Could not read $ASSET" } }
            .getOrDefault(Report())
    }

    private val byId: Map<Long, Entry> by lazy { report.broken.associateBy { it.id } }

    /** The date of the sweep, as written in the asset. */
    val checkedOn: String get() = report.checkedOn

    fun statusOf(sourceId: Long): Reason? = byId[sourceId]?.reason?.let {
        when (it) {
            "gone" -> Reason.Gone
            "outdated" -> Reason.Outdated
            else -> null
        }
    }

    /** Why a source did not work, in terms of what the user can do about it. */
    enum class Reason {
        /** The site it points at no longer exists. Nothing to wait for. */
        Gone,

        /** The site is up but the extension can no longer read it. Needs a new extension. */
        Outdated,
    }

    @Serializable
    private data class Report(
        val checkedOn: String = "",
        val broken: List<Entry> = emptyList(),
    )

    @Serializable
    private data class Entry(
        val id: Long,
        val name: String = "",
        val reason: String = "",
    )

    private companion object {
        const val ASSET = "anime-source-health.json"
    }
}
