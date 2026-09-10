package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import kotlin.time.Clock

/**
 * Anime counterpart of [eu.kanade.domain.manga.interactor.UpdateManga], narrowed to what
 * the anime side needs so far.
 */
@Inject
class UpdateAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(animeUpdate: AnimeUpdate): Boolean =
        animeRepository.updateAnime(animeUpdate)

    suspend fun awaitAll(animeUpdates: List<AnimeUpdate>): Boolean =
        animeRepository.updateAllAnime(animeUpdates)

    /**
     * Stamps dateAdded when an entry enters the library and clears it when it leaves, so
     * "recently added" sorting has something to work with.
     */
    suspend fun awaitUpdateFavorite(animeId: Long, favorite: Boolean): Boolean {
        val dateAdded = if (favorite) Clock.System.now().toEpochMilliseconds() else 0
        return await(AnimeUpdate(id = animeId, favorite = favorite, dateAdded = dateAdded))
    }
}
