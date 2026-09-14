package eu.kanade.tachiyomi.ui.animelibrary.setting

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.anime.ANMR

/** How the anime library is ordered. */
enum class AnimeLibrarySort(val label: StringResource) {
    TITLE(ANMR.strings.sort_anime_title),
    LAST_SEEN(ANMR.strings.sort_anime_last_seen),
    UNSEEN(ANMR.strings.sort_anime_unseen),
    TOTAL_EPISODES(ANMR.strings.sort_anime_total_episodes),
    LATEST_EPISODE(ANMR.strings.sort_anime_latest_episode),
    DATE_ADDED(ANMR.strings.sort_anime_date_added),

    /** When the library last checked this entry for new episodes. */
    LAST_UPDATE_CHECK(ANMR.strings.sort_anime_last_update_check),

    /** When the newest episode arrived in the local database. */
    EPISODE_FETCH_DATE(ANMR.strings.sort_anime_episode_fetch_date),

    /** Shuffled, for when you cannot decide. Re-shuffles on every pick, as Mihon's does. */
    RANDOM(MR.strings.action_sort_random),
}
