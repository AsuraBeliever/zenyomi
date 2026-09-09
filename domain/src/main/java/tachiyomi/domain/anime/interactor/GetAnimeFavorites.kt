package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository

@Inject
class GetAnimeFavorites(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<Anime> {
        return animeRepository.getAnimeFavorites()
    }

    fun subscribe(sourceId: Long): Flow<List<Anime>> {
        return animeRepository.getAnimeFavoritesBySourceId(sourceId)
    }
}
