package tachiyomi.data.category.anime

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

/**
 * Mirrors [tachiyomi.data.category.CategoryRepositoryImpl] against the anime database.
 *
 * The anime schema exposes one `update` that coalesces every column, so each of the update
 * methods here passes nulls for the fields it is not touching.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeCategoryRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeCategoryRepository {

    override suspend fun get(id: Long): AnimeCategory? {
        return database.categoriesQueries
            .getCategory(id, AnimeCategoryMapper::mapCategory)
            .awaitAsOneOrNull()
    }

    override suspend fun getAll(): List<AnimeCategory> {
        return database.categoriesQueries
            .getCategories(AnimeCategoryMapper::mapCategory)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<AnimeCategory>> {
        return database.categoriesQueries
            .getCategories(AnimeCategoryMapper::mapCategory)
            .subscribeToList()
    }

    override suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory> {
        return database.categoriesQueries
            .getCategoriesByAnimeId(animeId, AnimeCategoryMapper::mapCategory)
            .awaitAsList()
    }

    override fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>> {
        return database.categoriesQueries
            .getCategoriesByAnimeId(animeId, AnimeCategoryMapper::mapCategory)
            .subscribeToList()
    }

    override suspend fun insert(category: AnimeCategory) {
        database.categoriesQueries.insert(
            name = category.name,
            order = category.order,
            flags = category.flags,
        )
    }

    override suspend fun updateName(categoryId: Long, name: String) {
        database.categoriesQueries.update(
            name = name,
            order = null,
            flags = null,
            hidden = null,
            categoryId = categoryId,
        )
    }

    override suspend fun updateHidden(categoryId: Long, hidden: Boolean) {
        database.categoriesQueries.update(
            name = null,
            order = null,
            flags = null,
            hidden = if (hidden) 1L else 0L,
            categoryId = categoryId,
        )
    }

    override suspend fun updateAllOrders(orderedIds: List<Long>) {
        database.transaction {
            orderedIds.forEachIndexed { index, categoryId ->
                database.categoriesQueries.update(
                    name = null,
                    order = index.toLong(),
                    flags = null,
                    hidden = null,
                    categoryId = categoryId,
                )
            }
        }
    }

    override suspend fun delete(categoryId: Long) {
        database.categoriesQueries.delete(categoryId = categoryId)
    }
}
