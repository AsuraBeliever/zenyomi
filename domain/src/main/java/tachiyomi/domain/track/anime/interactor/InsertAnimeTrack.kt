package tachiyomi.domain.track.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

@Inject
class InsertAnimeTrack(
    private val repository: AnimeTrackRepository,
) {
    suspend fun await(track: AnimeTrack) = repository.insert(track)
}
