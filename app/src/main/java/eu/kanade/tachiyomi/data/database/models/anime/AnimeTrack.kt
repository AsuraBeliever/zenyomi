@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models.anime

import java.io.Serializable

/**
 * The mutable shape a tracker works with while binding or updating an anime.
 *
 * A parallel of [eu.kanade.tachiyomi.data.database.models.Track] rather than a generalisation
 * of it: the charter keeps manga and anime as separate trees, and naming the field
 * `last_episode_seen` instead of something neutral is exactly what stops the two getting
 * confused at the call site.
 */
interface AnimeTrack : Serializable {

    var id: Long?

    var anime_id: Long

    var tracker_id: Long

    var remote_id: Long

    var library_id: Long?

    var title: String

    var last_episode_seen: Double

    var total_episodes: Long

    var score: Double

    var status: Long

    var started_watching_date: Long

    var finished_watching_date: Long

    var tracking_url: String

    var private: Boolean

    fun copyPersonalFrom(other: AnimeTrack, copyRemotePrivate: Boolean = true) {
        last_episode_seen = other.last_episode_seen
        score = other.score
        status = other.status
        started_watching_date = other.started_watching_date
        finished_watching_date = other.finished_watching_date
        if (copyRemotePrivate) private = other.private
    }

    companion object {
        fun create(trackerId: Long): AnimeTrack = AnimeTrackImpl().apply {
            tracker_id = trackerId
        }
    }
}
