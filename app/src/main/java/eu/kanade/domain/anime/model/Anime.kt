package eu.kanade.domain.anime.model

import android.content.Context
import eu.kanade.tachiyomi.animesource.model.SAnime
import mihon.app.di.appGraph
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Anime counterpart of [eu.kanade.domain.manga.model.toSManga]: hands a stored entry
 * back to its source in the shape the source API expects.
 */
fun Anime.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

/**
 * Whether the episode list is filtered to downloaded episodes.
 *
 * The twin of [eu.kanade.domain.manga.model.downloadedFilter], and like it, the app-wide
 * "downloaded only" switch overrules whatever the entry itself is set to: while it is on,
 * nothing that is not on the device is shown anywhere.
 */
val Anime.downloadedFilter: TriState
    get() {
        if (Injekt.get<Context>().appGraph.basePreferences.downloadedOnly.get()) return TriState.ENABLED_IS
        return when (downloadedFilterRaw) {
            Anime.EPISODE_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Anime.EPISODE_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

/** Whether any episode filter is narrowing the list, which tints the filter icon. */
fun Anime.episodesFiltered(): Boolean =
    unseenFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED

/**
 * Folds what a source just told us about an entry into the stored one.
 *
 * The twin of [eu.kanade.domain.manga.model.copyFrom], down to keeping the stored value
 * wherever the source left a field null: a source that answers with less than it did last time
 * should not blank out a description that is already there.
 */
fun Anime.copyFrom(other: SAnime): Anime {
    return this.copy(
        author = other.author ?: author,
        artist = other.artist ?: artist,
        description = other.description ?: description,
        genre = if (other.genre != null) other.getGenres() else genre,
        thumbnailUrl = other.thumbnail_url ?: thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = other.initialized && initialized,
    )
}
