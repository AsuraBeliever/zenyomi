package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

/**
 * Anime counterpart of [CategoriesRestorer].
 *
 * Matches on name, so restoring onto a device that already has a "Watching" adds the backup's
 * entries to the existing one instead of creating a second category with the same name.
 */
@Inject
class AnimeCategoriesRestorer(
    private val getAnimeCategories: GetAnimeCategories,
    private val repository: AnimeCategoryRepository,
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val existingByName = getAnimeCategories.await().associateBy { it.name }
        var nextOrder = getAnimeCategories.await().maxOfOrNull { it.order }?.plus(1) ?: 0

        backupCategories
            .sortedBy { it.order }
            .forEach { backupCategory ->
                if (existingByName.containsKey(backupCategory.name)) return@forEach
                repository.insert(
                    AnimeCategory(
                        id = 0,
                        name = backupCategory.name,
                        order = nextOrder++,
                        flags = backupCategory.flags,
                        hidden = false,
                    ),
                )
            }
    }
}
