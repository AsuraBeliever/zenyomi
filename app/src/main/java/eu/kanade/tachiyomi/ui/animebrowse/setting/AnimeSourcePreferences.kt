package eu.kanade.tachiyomi.ui.animebrowse.setting

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Which anime sources the user wants to see, and in what order.
 *
 * Its own class rather than an addition to Mihon's
 * [eu.kanade.domain.source.service.SourcePreferences]: the ids live in a different namespace,
 * and an anime source sharing a key with a manga one would pin or hide the wrong thing.
 *
 * Ids are stored as strings because that is what a preference set holds, and it is what
 * Mihon does for the same reason.
 */
@Inject
@SingleIn(AppScope::class)
class AnimeSourcePreferences(
    preferenceStore: PreferenceStore,
) {

    /** Sources kept at the top of the list. */
    val pinnedSources: Preference<Set<String>> =
        preferenceStore.getStringSet("pref_anime_pinned_sources", emptySet())

    /**
     * Sources kept out of the list entirely, and out of global search.
     *
     * The second half is the point. Most of the anime ecosystem is broken at any moment, and
     * a global search that keeps asking the dead ones is slower and noisier for no return.
     */
    val hiddenSources: Preference<Set<String>> =
        preferenceStore.getStringSet("pref_anime_hidden_sources", emptySet())
}
