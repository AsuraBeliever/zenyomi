package tachiyomi.data.track.anime

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeTrackRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeTrackRepository {

    override suspend fun getTracksByAnimeId(animeId: Long): List<AnimeTrack> {
        return database.anime_syncQueries
            .getTracksByAnimeId(animeId, AnimeTrackMapper::mapTrack)
            .awaitAsList()
    }

    override fun getTracksByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeTrack>> {
        return database.anime_syncQueries
            .getTracksByAnimeId(animeId, AnimeTrackMapper::mapTrack)
            .subscribeToList()
    }

    override suspend fun delete(animeId: Long, trackerId: Long) {
        database.anime_syncQueries.delete(animeId, trackerId)
    }

    override suspend fun insert(track: AnimeTrack) {
        // The table declares UNIQUE(anime_id, sync_id) ON CONFLICT REPLACE, so re-binding an
        // anime to the same tracker overwrites the old row instead of piling up duplicates.
        database.anime_syncQueries.insert(
            animeId = track.animeId,
            syncId = track.trackerId,
            remoteId = track.remoteId,
            libraryId = track.libraryId,
            title = track.title,
            lastEpisodeSeen = track.lastEpisodeSeen,
            totalEpisodes = track.totalEpisodes,
            status = track.status,
            score = track.score,
            remoteUrl = track.remoteUrl,
            startDate = track.startDate,
            finishDate = track.finishDate,
            private = track.private,
        )
    }
}
