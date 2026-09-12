package tachiyomi.data.category.anime

import tachiyomi.domain.category.anime.model.AnimeCategory

/**
 * Anime counterpart of the mapper inlined in [tachiyomi.data.category.CategoryRepositoryImpl].
 */
object AnimeCategoryMapper {

    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): AnimeCategory = AnimeCategory(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden == 1L,
    )
}
