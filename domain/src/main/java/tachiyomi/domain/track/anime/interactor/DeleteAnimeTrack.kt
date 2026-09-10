package tachiyomi.domain.track.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

@Inject
class DeleteAnimeTrack(
    private val repository: AnimeTrackRepository,
) {
    suspend fun await(animeId: Long, trackerId: Long) = repository.delete(animeId, trackerId)
}
