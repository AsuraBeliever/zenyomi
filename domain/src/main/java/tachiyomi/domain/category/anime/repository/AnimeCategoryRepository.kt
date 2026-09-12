package tachiyomi.domain.category.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.anime.model.AnimeCategory

interface AnimeCategoryRepository {

    suspend fun get(id: Long): AnimeCategory?

    suspend fun getAll(): List<AnimeCategory>

    fun getAllAsFlow(): Flow<List<AnimeCategory>>

    suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory>

    fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>>

    suspend fun insert(category: AnimeCategory)

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateHidden(categoryId: Long, hidden: Boolean)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun delete(categoryId: Long)
}
