package eu.kanade.tachiyomi.ui.animelibrary.setting

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum

/**
 * How the anime library is shown and narrowed.
 *
 * Its own class rather than an addition to Mihon's
 * [tachiyomi.domain.library.service.LibraryPreferences]: none of these keys mean anything to
 * the manga library, and leaving that file untouched is what keeps upstream merges cheap.
 *
 * Sort and filter are remembered rather than reset each visit. A library is arranged once and
 * then lived in; having to re-pick "unwatched only" on every open is the kind of thing that
 * makes a filter not worth using.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeLibraryPreferences(
    preferenceStore: PreferenceStore,
) {

    val sort: Preference<AnimeLibrarySort> =
        preferenceStore.getEnum("pref_anime_library_sort", AnimeLibrarySort.TITLE)

    val sortAscending: Preference<Boolean> =
        preferenceStore.getBoolean("pref_anime_library_sort_asc", true)

    val displayMode: Preference<AnimeLibraryDisplayMode> =
        preferenceStore.getEnum("pref_anime_library_display", AnimeLibraryDisplayMode.COMFORTABLE_GRID)

    val filterUnseen: Preference<TriState> =
        preferenceStore.getEnum("pref_anime_library_filter_unseen", TriState.DISABLED)

    val filterStarted: Preference<TriState> =
        preferenceStore.getEnum("pref_anime_library_filter_started", TriState.DISABLED)

    val filterBookmarked: Preference<TriState> =
        preferenceStore.getEnum("pref_anime_library_filter_bookmarked", TriState.DISABLED)

    val filterCompleted: Preference<TriState> =
        preferenceStore.getEnum("pref_anime_library_filter_completed", TriState.DISABLED)
}
