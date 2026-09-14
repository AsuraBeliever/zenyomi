package eu.kanade.tachiyomi.data.track

import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.model.Track as MangaTrack

/**
 * The edits the tracking sheet makes, for anime.
 *
 * These mirror [BaseTracker]'s `setRemote*` methods one for one, including the parts that are
 * not obvious: setting the status to "completed" fills the episode count in, and watching the
 * last episode flips the status to completed and stamps the finish date. Those rules are what
 * make a tracker agree with the app afterwards, and writing them again by feel would have
 * produced a tracking sheet that looks like Mihon's and behaves differently.
 *
 * Extensions rather than methods on [AnimeTracker] so no tracker has to implement them, and in
 * a file of our own so Mihon's stays untouched.
 *
 * Each one writes the local row too. The remote call is what can fail; losing the local edit as
 * well would leave the sheet showing something the service no longer agrees with.
 */
private suspend fun AnimeTracker.pushAnime(track: AnimeTrack, insertTrack: InsertAnimeTrack) {
    withIOContext {
        try {
            updateAnime(track)
            // The id travels so the existing row is replaced rather than a second one added.
            insertTrack.await(track.toDomainTrack(track.anime_id, track.id ?: -1L))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update the remote anime track" }
        }
    }
}

suspend fun AnimeTracker.setRemoteAnimeStatus(
    track: AnimeTrack,
    status: Long,
    insertTrack: InsertAnimeTrack,
) {
    track.status = status
    if (track.status == getCompletionStatusAnime() && track.total_episodes != 0L) {
        track.last_episode_seen = track.total_episodes.toDouble()
    }
    pushAnime(track, insertTrack)
}

suspend fun AnimeTracker.setRemoteLastEpisodeSeen(
    track: AnimeTrack,
    episodeNumber: Int,
    insertTrack: InsertAnimeTrack,
) {
    if (track.last_episode_seen == 0.0 && track.last_episode_seen < episodeNumber) {
        track.status = getWatchingStatus()
    }
    track.last_episode_seen = episodeNumber.toDouble()
    if (track.total_episodes != 0L && track.last_episode_seen.toLong() == track.total_episodes) {
        track.status = getCompletionStatusAnime()
        track.finished_watching_date = System.currentTimeMillis()
    }
    pushAnime(track, insertTrack)
}

suspend fun AnimeTracker.setRemoteAnimeScore(
    track: AnimeTrack,
    scoreString: String,
    insertTrack: InsertAnimeTrack,
) {
    val tracker = this as Tracker
    track.score = tracker.indexToScore(tracker.getScoreList().indexOf(scoreString))
    pushAnime(track, insertTrack)
}

suspend fun AnimeTracker.setRemoteAnimeStartDate(
    track: AnimeTrack,
    epochMillis: Long,
    insertTrack: InsertAnimeTrack,
) {
    track.started_watching_date = epochMillis
    pushAnime(track, insertTrack)
}

suspend fun AnimeTracker.setRemoteAnimeFinishDate(
    track: AnimeTrack,
    epochMillis: Long,
    insertTrack: InsertAnimeTrack,
) {
    track.finished_watching_date = epochMillis
    pushAnime(track, insertTrack)
}

suspend fun AnimeTracker.setRemoteAnimePrivate(
    track: AnimeTrack,
    private: Boolean,
    insertTrack: InsertAnimeTrack,
) {
    track.private = private
    pushAnime(track, insertTrack)
}

/**
 * How a service would print this score.
 *
 * [Tracker.displayScore] takes a manga track, but every implementation reads one field off it —
 * the score — and formats it against the account's own scale, which is per account and not per
 * media type. So it is asked with a stand-in carrying the anime's score rather than duplicating
 * five services' worth of formatting rules, which is where the two sides would drift.
 */
fun Tracker.displayAnimeScore(score: Double): String = displayScore(
    MangaTrack(
        id = -1L,
        mangaId = -1L,
        trackerId = id,
        remoteId = -1L,
        libraryId = null,
        title = "",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 0L,
        score = score,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    ),
)
