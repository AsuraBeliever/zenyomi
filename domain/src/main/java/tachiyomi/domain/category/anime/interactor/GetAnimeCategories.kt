package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

@Inject
class GetAnimeCategories(
    private val repository: AnimeCategoryRepository,
) {

    fun subscribe(): Flow<List<AnimeCategory>> = repository.getAllAsFlow()

    fun subscribe(animeId: Long): Flow<List<AnimeCategory>> =
        repository.getCategoriesByAnimeIdAsFlow(animeId)

    suspend fun await(): List<AnimeCategory> = repository.getAll()

    suspend fun await(animeId: Long): List<AnimeCategory> = repository.getCategoriesByAnimeId(animeId)
}
