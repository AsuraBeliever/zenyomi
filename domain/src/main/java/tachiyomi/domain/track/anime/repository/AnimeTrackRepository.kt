package tachiyomi.domain.track.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.anime.model.AnimeTrack

interface AnimeTrackRepository {

    suspend fun getTracksByAnimeId(animeId: Long): List<AnimeTrack>

    fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeTrack>>

    suspend fun delete(animeId: Long, trackerId: Long)

    suspend fun insert(track: AnimeTrack)
}
