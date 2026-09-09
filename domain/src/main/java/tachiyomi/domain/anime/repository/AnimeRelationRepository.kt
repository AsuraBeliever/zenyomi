package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeRelationGroup

interface AnimeRelationRepository {

    fun subscribeRelatedAnime(animeId: Long): Flow<List<AnimeRelationGroup>>

    suspend fun getLastFetchedAt(animeId: Long): Long?

    suspend fun replaceRelations(
        animeId: Long,
        groups: List<Pair<String, List<Long>>>,
        fetchedAt: Long,
    )
}
