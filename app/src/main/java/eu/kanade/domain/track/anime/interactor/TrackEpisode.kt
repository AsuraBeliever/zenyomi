package eu.kanade.domain.track.anime.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack

/**
 * Pushes an anime's watch progress to every tracker it is bound to.
 *
 * Only ever moves forward: a tracker that already has a higher episode count is left alone, so
 * rewatching an old episode does not reset the user's list. Each tracker is updated
 * independently — one service being down must not stop the others.
 */
@Inject
class TrackEpisode(
    private val getTracks: GetAnimeTracks,
    private val insertTrack: InsertAnimeTrack,
    private val trackerManager: TrackerManager,
) {

    suspend fun await(animeId: Long, episodeNumber: Double) {
        val tracks = getTracks.await(animeId)
        if (tracks.isEmpty()) return

        supervisorScope {
            tracks.map { track ->
                async {
                    val tracker = trackerManager.get(track.trackerId)
                    if (tracker == null || tracker !is AnimeTracker || !tracker.isLoggedIn) {
                        return@async
                    }
                    if (episodeNumber <= track.lastEpisodeSeen) return@async

                    runCatching {
                        val updated = tracker.updateAnime(
                            track.toDbTrack().apply { last_episode_seen = episodeNumber },
                            didWatchEpisode = true,
                        )
                        insertTrack.await(updated.toDomainTrack(animeId, track.id))
                    }.onFailure {
                        logcat(LogPriority.WARN, it) { "Could not push progress to ${tracker.name}" }
                    }
                }
            }.awaitAll()
        }
    }
}
