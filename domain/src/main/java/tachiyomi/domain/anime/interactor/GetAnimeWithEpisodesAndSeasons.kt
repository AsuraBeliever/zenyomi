package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.SeasonAnime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.repository.EpisodeRepository

@Inject
class GetAnimeWithEpisodesAndSeasons(
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun subscribe(id: Long): Flow<Triple<Anime, List<Episode>, List<SeasonAnime>>> {
        return combine(
            animeRepository.getAnimeByIdAsFlow(id),
            episodeRepository.getEpisodeByAnimeIdAsFlow(id),
            animeRepository.getAnimeSeasonsByIdAsFlow(id),
        ) { anime, episodes, seasons ->
            Triple(anime, episodes, seasons)
        }
    }

    suspend fun awaitAnime(id: Long): Anime {
        return animeRepository.getAnimeById(id)
    }

    suspend fun awaitEpisodes(id: Long): List<Episode> {
        return episodeRepository.getEpisodeByAnimeId(id)
    }

    suspend fun awaitSeasons(id: Long): List<SeasonAnime> {
        return animeRepository.getAnimeSeasonsById(id)
    }
}
