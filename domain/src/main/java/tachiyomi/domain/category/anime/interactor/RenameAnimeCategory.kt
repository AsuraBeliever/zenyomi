package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

@Inject
class RenameAnimeCategory(
    private val repository: AnimeCategoryRepository,
) {

    suspend fun await(category: AnimeCategory, name: String): Result {
        if (category.isSystemCategory) return Result.CannotRenameSystemCategory
        val categories = repository.getAll()
        if (categories.any { it.id != category.id && it.name.equals(name, ignoreCase = true) }) {
            return Result.NameAlreadyExists
        }

        return try {
            repository.updateName(category.id, name)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object NameAlreadyExists : Result
        data object CannotRenameSystemCategory : Result
        data class InternalError(val error: Throwable) : Result
    }
}
