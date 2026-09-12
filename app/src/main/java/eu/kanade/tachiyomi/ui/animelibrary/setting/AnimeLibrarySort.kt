package eu.kanade.tachiyomi.ui.animelibrary.setting

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.anime.ANMR

/** How the anime library is ordered. */
enum class AnimeLibrarySort(val label: StringResource) {
    TITLE(ANMR.strings.sort_anime_title),
    LAST_SEEN(ANMR.strings.sort_anime_last_seen),
    UNSEEN(ANMR.strings.sort_anime_unseen),
    TOTAL_EPISODES(ANMR.strings.sort_anime_total_episodes),
    LATEST_EPISODE(ANMR.strings.sort_anime_latest_episode),
    DATE_ADDED(ANMR.strings.sort_anime_date_added),
}
