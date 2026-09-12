package tachiyomi.domain.category.anime.model

import java.io.Serializable

/**
 * A category in the anime library.
 *
 * Its own type rather than Mihon's [tachiyomi.domain.category.model.Category]: the two live in
 * separate databases, and the anime table carries a `hidden` column that the manga one does
 * not. Sharing the model would mean either dropping that column or adding a field to Mihon's
 * type that only the anime side ever sets.
 */
data class AnimeCategory(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val hidden: Boolean,
) : Serializable {

    /** Row 0 holds everything not filed anywhere. It cannot be renamed or deleted. */
    val isSystemCategory: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L
    }
}
