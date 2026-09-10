@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.track.model

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack

/** One result from a tracker's search, before the user picks which entry to bind. */
class AnimeTrackSearch : AnimeTrack {

    override var id: Long? = null

    override var anime_id: Long = 0

    override var tracker_id: Long = 0

    override var remote_id: Long = 0

    override var library_id: Long? = null

    override lateinit var title: String

    override var last_episode_seen: Double = 0.0

    override var total_episodes: Long = 0

    override var score: Double = -1.0

    override var status: Long = 0

    override var started_watching_date: Long = 0

    override var finished_watching_date: Long = 0

    override var private: Boolean = false

    override lateinit var tracking_url: String

    var cover_url: String = ""

    var summary: String = ""

    var publishing_status: String = ""

    var publishing_type: String = ""

    var start_date: String = ""

    companion object {
        fun create(trackerId: Long): AnimeTrackSearch = AnimeTrackSearch().apply {
            tracker_id = trackerId
            title = ""
            tracking_url = ""
        }
    }
}
