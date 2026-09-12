package tachiyomi.domain.updates.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import kotlin.time.Instant

@Inject
class GetAnimeUpdates(
    private val repository: AnimeUpdatesRepository,
) {

    fun subscribe(after: Instant): Flow<List<AnimeUpdatesWithRelations>> {
        return repository.subscribeAll(after.toEpochMilliseconds(), limit = LIMIT)
    }

    companion object {
        /** Matches Mihon's manga updates: a screen, not an archive. */
        const val LIMIT = 500L
    }
}
