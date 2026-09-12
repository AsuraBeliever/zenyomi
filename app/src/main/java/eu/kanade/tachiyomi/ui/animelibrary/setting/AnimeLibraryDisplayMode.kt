package eu.kanade.tachiyomi.ui.animelibrary.setting

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

/** How an entry is drawn in the anime library. */
enum class AnimeLibraryDisplayMode(val label: StringResource) {
    /** Cover with the title underneath. */
    COMFORTABLE_GRID(MR.strings.action_display_comfortable_grid),

    /** Cover only, title overlaid; fits more on screen. */
    COVER_ONLY_GRID(MR.strings.action_display_cover_only_grid),

    /** One row per entry, for long titles. */
    LIST(MR.strings.action_display_list),
}
