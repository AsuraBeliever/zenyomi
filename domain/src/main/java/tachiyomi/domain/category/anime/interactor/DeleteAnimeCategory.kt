package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

/**
 * Deleting a category does not delete the anime in it: the rows in `animes_categories` go with
 * it by foreign key, and anything left in no category shows up under the default one.
 */
@Inject
class DeleteAnimeCategory(
    private val repository: AnimeCategoryRepository,
) {

    suspend fun await(categoryId: Long) {
        try {
            repository.delete(categoryId)
            // Orders are positional, so the gap left behind has to be closed or the next
            // reorder will fight with it.
            val orderedIds = repository.getAll()
                .filterNot { it.isSystemCategory }
                .sortedBy { it.order }
                .map { it.id }
            repository.updateAllOrders(orderedIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
