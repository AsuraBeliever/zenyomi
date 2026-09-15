package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository

/** Gemelo de [tachiyomi.domain.manga.interactor.UpdateMangaNotes]. */
@Inject
class UpdateAnimeNotes(
    private val animeRepository: AnimeRepository,
) {

    suspend operator fun invoke(animeId: Long, notes: String): Boolean {
        return animeRepository.updateAnime(AnimeUpdate(id = animeId, notes = notes))
    }
}
