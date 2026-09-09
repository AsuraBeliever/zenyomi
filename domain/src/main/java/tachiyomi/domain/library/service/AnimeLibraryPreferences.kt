package tachiyomi.domain.library.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.anime.model.Anime

/**
 * Library preferences that apply to anime and episodes.
 *
 * Aniyomi keeps these in [LibraryPreferences] alongside the manga ones. Here they live
 * in their own class so that Mihon's file stays untouched and keeps merging cleanly;
 * see docs/adr/0001-arbol-paralelo-anime.md.
 *
 * The preference keys are distinct from the manga ones, so anime and manga defaults are
 * configured independently.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeLibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val filterEpisodeBySeen: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_seen",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByDownloaded: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_downloaded",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByBookmarked: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_bookmarked",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByFillermarked: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_fillermarked",
        Anime.SHOW_ALL,
    )

    val sortEpisodeBySourceOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    val sortEpisodeByAscendingOrDescending: Preference<Long> = preferenceStore.getLong(
        "default_episode_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    val displayEpisodeByNameOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_episode_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    val showEpisodeThumbnailPreviews: Preference<Long> = preferenceStore.getLong(
        "default_episode_show_thumbnail_previews",
        Anime.EPISODE_SHOW_PREVIEWS,
    )

    val showEpisodeSummaries: Preference<Long> = preferenceStore.getLong(
        "default_episode_show_summaries",
        Anime.EPISODE_SHOW_SUMMARIES,
    )

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen.set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded.set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked.set(anime.bookmarkedFilterRaw)
        filterEpisodeByFillermarked.set(anime.fillermarkedFilterRaw)
        sortEpisodeBySourceOrNumber.set(anime.sorting)
        displayEpisodeByNameOrNumber.set(anime.displayMode)
        sortEpisodeByAscendingOrDescending.set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
    }
}
