package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

@Inject
class CreateAnimeCategoryWithName(
    private val repository: AnimeCategoryRepository,
) {

    /**
     * Rejects a name already in use. Two categories with the same name are indistinguishable
     * in every list that shows them, so the duplicate is only ever a mistake.
     */
    suspend fun await(name: String): Result {
        val categories = repository.getAll()
        if (categories.any { it.name.equals(name, ignoreCase = true) }) {
            return Result.NameAlreadyExists
        }

        return try {
            repository.insert(
                AnimeCategory(
                    id = 0,
                    name = name,
                    // New categories go last; the system category sits at -1.
                    order = categories.maxOfOrNull { it.order }?.plus(1) ?: 0,
                    flags = 0,
                    hidden = false,
                ),
            )
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data object NameAlreadyExists : Result
        data class InternalError(val error: Throwable) : Result
    }
}
