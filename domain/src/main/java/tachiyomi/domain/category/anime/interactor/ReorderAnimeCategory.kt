package tachiyomi.domain.category.anime.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

@Inject
class ReorderAnimeCategory(
    private val repository: AnimeCategoryRepository,
) {

    suspend fun moveUp(category: AnimeCategory) = move(category, MOVE_UP)

    suspend fun moveDown(category: AnimeCategory) = move(category, MOVE_DOWN)

    private suspend fun move(category: AnimeCategory, offset: Int) {
        try {
            val categories = repository.getAll()
                .filterNot { it.isSystemCategory }
                .sortedBy { it.order }
                .toMutableList()

            val from = categories.indexOfFirst { it.id == category.id }
            val to = from + offset
            if (from == -1 || to !in categories.indices) return

            categories.add(to, categories.removeAt(from))
            repository.updateAllOrders(categories.map { it.id })
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private companion object {
        const val MOVE_UP = -1
        const val MOVE_DOWN = 1
    }
}
