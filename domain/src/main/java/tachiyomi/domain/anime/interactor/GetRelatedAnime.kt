package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeRelationGroup
import tachiyomi.domain.anime.repository.AnimeRelationRepository

@Inject
class GetRelatedAnime(
    private val relationRepository: AnimeRelationRepository,
) {
    fun subscribe(animeId: Long): Flow<List<AnimeRelationGroup>> {
        return relationRepository.subscribeRelatedAnime(animeId)
    }
}
