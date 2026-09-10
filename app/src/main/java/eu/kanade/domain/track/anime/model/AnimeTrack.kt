package eu.kanade.domain.track.anime.model

import tachiyomi.domain.track.anime.model.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack as DbAnimeTrack

fun AnimeTrack.toDbTrack(): DbAnimeTrack = DbAnimeTrack.create(trackerId).also {
    it.id = id
    it.anime_id = animeId
    it.remote_id = remoteId
    it.library_id = libraryId
    it.title = title
    it.last_episode_seen = lastEpisodeSeen
    it.total_episodes = totalEpisodes
    it.status = status
    it.score = score
    it.tracking_url = remoteUrl
    it.started_watching_date = startDate
    it.finished_watching_date = finishDate
    it.private = private
}

/**
 * @param animeId the library anime this belongs to; a tracker hands back an entry that knows
 * nothing about the local database, so the caller supplies it.
 * @param id the row being replaced, or -1 for a new binding.
 */
fun DbAnimeTrack.toDomainTrack(animeId: Long, id: Long = -1L): AnimeTrack = AnimeTrack(
    id = id,
    animeId = animeId,
    trackerId = tracker_id,
    remoteId = remote_id,
    libraryId = library_id,
    title = title,
    lastEpisodeSeen = last_episode_seen,
    totalEpisodes = total_episodes,
    status = status,
    score = score,
    remoteUrl = tracking_url,
    startDate = started_watching_date,
    finishDate = finished_watching_date,
    private = private,
)
