package tachiyomi.data.anime

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.subscribeToList
import tachiyomi.domain.anime.model.AnimeRelationGroup
import tachiyomi.domain.anime.repository.AnimeRelationRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeRelationRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeRelationRepository {

    override suspend fun getLastFetchedAt(animeId: Long): Long? {
        return database.anime_relationsQueries.getLastFetchedAt(animeId).awaitAsOneOrNull()
    }

    override suspend fun replaceRelations(
        animeId: Long,
        groups: List<Pair<String, List<Long>>>,
        fetchedAt: Long,
    ) {
        database.transaction {
            database.anime_relationsQueries.deleteByAnimeId(animeId)
            var sortOrder = 0L
            groups.forEach { (name, relatedIds) ->
                relatedIds.forEach { relatedId ->
                    database.anime_relationsQueries.insert(
                        animeId = animeId,
                        relatedAnimeId = relatedId,
                        name = name,
                        sortOrder = sortOrder++,
                        lastFetchedAt = fetchedAt,
                    )
                }
            }
        }
    }

    override fun subscribeRelatedAnime(animeId: Long): Flow<List<AnimeRelationGroup>> {
        return database.anime_relationsQueries
            .getRelatedAnimeByAnimeId(
                animeId,
                AnimeMapper::mapRelatedAnime,
            )
            .subscribeToList()
            .map { rows ->
                // The query is already ordered, and groupBy preserves encounter order.
                rows.groupBy({ it.first }, { it.second })
                    .map { (name, anime) -> AnimeRelationGroup(name, anime) }
            }
    }
}
