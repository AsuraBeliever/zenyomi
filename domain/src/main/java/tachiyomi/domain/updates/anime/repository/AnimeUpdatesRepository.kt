package tachiyomi.domain.updates.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations

interface AnimeUpdatesRepository {

    fun subscribeAll(after: Long, limit: Long): Flow<List<AnimeUpdatesWithRelations>>
}
