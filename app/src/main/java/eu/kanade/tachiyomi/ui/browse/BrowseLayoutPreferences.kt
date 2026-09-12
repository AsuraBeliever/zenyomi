package eu.kanade.tachiyomi.ui.browse

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/** Manga, anime, or both, in the Browse tab's two lists. */
enum class BrowseMedia { BOTH, MANGA, ANIME }

/**
 * How the Browse tab is arranged.
 *
 * Browse used to carry five tabs — Sources, Manga extensions, Anime sources, Anime extensions,
 * Migrate — and their titles were truncated to "Manga …", "Anime …", "Anime …", which is no use
 * at all for telling the last two apart. Three tabs, with the two halves stacked inside under
 * collapsible headers and a filter to show one or both, says more in less room.
 *
 * Both the filter and the collapsed state are remembered. A user who only reads manga should
 * not have to say so on every visit, and collapsing a list of several hundred extensions is
 * pointless if it springs open again on the next launch.
 */
@Inject
@SingleIn(AppScope::class)
class BrowseLayoutPreferences(
    preferenceStore: PreferenceStore,
) {

    val sourcesMedia: Preference<BrowseMedia> =
        preferenceStore.getEnum("pref_browse_sources_media", BrowseMedia.BOTH)

    val extensionsMedia: Preference<BrowseMedia> =
        preferenceStore.getEnum("pref_browse_extensions_media", BrowseMedia.BOTH)

    val mangaSourcesExpanded: Preference<Boolean> =
        preferenceStore.getBoolean("pref_browse_sources_manga_expanded", true)

    val animeSourcesExpanded: Preference<Boolean> =
        preferenceStore.getBoolean("pref_browse_sources_anime_expanded", true)

    val mangaExtensionsExpanded: Preference<Boolean> =
        preferenceStore.getBoolean("pref_browse_ext_manga_expanded", true)

    /** Collapsed by default: this is the list with several hundred entries in it. */
    val animeExtensionsExpanded: Preference<Boolean> =
        preferenceStore.getBoolean("pref_browse_ext_anime_expanded", false)
}
