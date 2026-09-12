package tachiyomi.data.updates.anime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository

/**
 * Mirrors [tachiyomi.data.updates.UpdatesRepositoryImpl] against the anime database.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeUpdatesRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeUpdatesRepository {

    override fun subscribeAll(after: Long, limit: Long): Flow<List<AnimeUpdatesWithRelations>> {
        return database.animeupdatesViewQueries
            .getAnimeUpdatesAfter(after, limit, AnimeUpdatesMapper::mapUpdatesWithRelations)
            .subscribeToList()
    }
}
