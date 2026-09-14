package eu.kanade.tachiyomi.ui.animelibrary

import tachiyomi.domain.library.anime.LibraryAnime

/**
 * One entry as the library draws it: the row, plus everything the badges and filters need.
 *
 * The twin of Mihon's [eu.kanade.tachiyomi.ui.library.LibraryItem], and for the same reason:
 * the download count and the source's language are not on the entry itself, they cost a
 * directory listing and a source lookup, and neither belongs in a composable that runs on
 * every scroll.
 *
 * [badges] is the same numbers again, zeroed out when the matching overlay is switched off.
 * Kept apart from the real counts on purpose — the *Downloaded* filter has to work whether or
 * not the badge is being shown.
 */
data class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    val downloadCount: Int,
    val unseenCount: Long,
    val isLocal: Boolean,
    val sourceLanguage: String,
    val badges: Badges,
) {
    val id: Long = libraryAnime.id

    data class Badges(
        val downloadCount: Int,
        val unseenCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}
