package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.repository.AnimeRepository

@Inject
class ResetAnimeViewerFlags(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(): Boolean {
        return animeRepository.resetAnimeViewerFlags()
    }
}
