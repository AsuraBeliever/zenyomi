package eu.kanade.tachiyomi.data.download.anime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Settings for downloading episodes.
 *
 * Its own class rather than an addition to Mihon's
 * [tachiyomi.domain.download.service.DownloadPreferences]: nothing here means anything to a
 * chapter, and keeping Mihon's file untouched is what keeps upstream merges cheap.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeDownloadPreferences(
    preferenceStore: PreferenceStore,
) {

    /**
     * The vertical resolution to download at, or [UNSET] until the viewer has been asked.
     *
     * Asked once, the first time anything is downloaded, and then left alone — a dialog on
     * every download would be in the way of the common case, which is downloading the next
     * episode of something at the quality you always use. It is changed from settings after
     * that, and the dialog that asks the first time says so.
     */
    val quality: Preference<Int> = preferenceStore.getInt("pref_anime_download_quality", UNSET)

    companion object {
        /** Nobody has chosen yet. Distinct from "the best available", which is a real choice. */
        const val UNSET = 0

        /** Whatever the source offers, whatever its numbers are. */
        const val BEST = -1

        /** The heights offered in settings. What a source actually has may differ. */
        val OFFERED_HEIGHTS = listOf(1080, 720, 480, 360)
    }
}
