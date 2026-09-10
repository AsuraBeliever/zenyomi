package tachiyomi.domain.track.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

@Inject
class GetAnimeTracks(
    private val repository: AnimeTrackRepository,
) {
    suspend fun await(animeId: Long): List<AnimeTrack> = repository.getTracksByAnimeId(animeId)

    fun subscribe(animeId: Long): Flow<List<AnimeTrack>> = repository.getTracksByAnimeIdAsFlow(animeId)
}
