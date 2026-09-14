package eu.kanade.tachiyomi.ui.animelibrary.setting

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/**
 * How an entry is drawn in the anime library.
 *
 * The same four the manga library offers, in the same order. It was missing the compact grid,
 * which is the one Mihon defaults to, so the two libraries could not even be set to look alike.
 */
enum class AnimeLibraryDisplayMode(val label: StringResource) {
    /** Cover with the title underneath. */
    COMFORTABLE_GRID(MR.strings.action_display_comfortable_grid),

    /** Cover with the title over the bottom of it. */
    COMPACT_GRID(MR.strings.action_display_grid),

    /** Cover and nothing else, which fits the most on screen. */
    COVER_ONLY_GRID(MR.strings.action_display_cover_only_grid),

    /** One row per entry, for long titles. */
    LIST(MR.strings.action_display_list),
}
