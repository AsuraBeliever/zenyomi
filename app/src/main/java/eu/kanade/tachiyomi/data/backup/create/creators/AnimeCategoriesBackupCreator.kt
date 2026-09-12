package eu.kanade.tachiyomi.data.backup.create.creators

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory

/**
 * Anime counterpart of [CategoriesBackupCreator].
 *
 * Reuses [BackupCategory] rather than adding a parallel type: the fields are the same, and a
 * second wire format would be two things to keep in step for no gain. The anime `hidden` flag
 * is left out on purpose — it is a per-device view preference, not something a restore on
 * another phone should impose.
 */
@Inject
class AnimeCategoriesBackupCreator(
    private val getAnimeCategories: GetAnimeCategories,
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getAnimeCategories.await()
            .filterNot(AnimeCategory::isSystemCategory)
            .map { category ->
                BackupCategory(
                    id = category.id,
                    name = category.name,
                    order = category.order,
                    flags = category.flags,
                )
            }
    }
}
