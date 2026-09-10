package eu.kanade.tachiyomi.data.track.myanimelist

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.DeletableTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALOAuth
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.model.Track as DomainTrack

class MyAnimeList(id: Long) : BaseTracker(id, "MyAnimeList"), DeletableTracker, AnimeTracker {

    companion object {
        const val READING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_READ = 6L
        const val REREADING = 7L

        /**
         * MAL keeps separate status strings per media type ("reading" against "watching"), but
         * the numeric list positions line up, so anime reuses them under names that read right.
         */
        const val WATCHING = READING
        const val PLAN_TO_WATCH = PLAN_TO_READ

        private const val SEARCH_ID_PREFIX = "id:"
        private const val SEARCH_LIST_PREFIX = "my:"

        private val SCORE_LIST = IntRange(0, 10)
            .map(Int::toString)
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { MyAnimeListInterceptor(this) }
    private val api by lazy { MyAnimeListApi(id, client, interceptor) }

    override val supportsReadingDates: Boolean = true

    override fun getLogo() = R.drawable.brand_myanimelist

    override fun getStatusList(): List<Long> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)
    }

    override fun getStatus(status: Long): StringResource? = when (status) {
        READING -> MR.strings.reading
        PLAN_TO_READ -> MR.strings.plan_to_read
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REREADING -> MR.strings.repeating
        else -> null
    }

    override fun getReadingStatus(): Long = READING

    override fun getRereadingStatus(): Long = REREADING

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: DomainTrack): String {
        return track.score.toInt().toString()
    }

    private suspend fun add(track: Track): Track {
        return api.updateItem(track)
    }

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toLong() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateItem(track)
    }

    override suspend fun delete(track: DomainTrack) {
        api.deleteItem(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        val remoteTrack = api.findListItem(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().toIntOrNull()?.let { id ->
                return listOf(api.getMangaDetails(id))
            }
        }

        if (query.startsWith(SEARCH_LIST_PREFIX)) {
            query.substringAfter(SEARCH_LIST_PREFIX).let { title ->
                return api.findListItems(title)
            }
        }

        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        return api.findListItem(track) ?: add(track)
    }

    // ---- Anime ------------------------------------------------------------------------

    override fun getWatchingStatus(): Long = WATCHING

    override fun getCompletionStatusAnime(): Long = COMPLETED

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = api.searchAnime(query)

    override suspend fun bindAnime(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        // findAnimeListItem fills the track in place and returns it, or null when the anime is
        // not on the user's list yet, so there is nothing to copy across here.
        val alreadyOnList = api.findAnimeListItem(track) != null
        return if (alreadyOnList) {
            if (track.status != COMPLETED) {
                val isRewatching = track.status == REREADING
                track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
            }

            updateAnime(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            updateAnime(track)
        }
    }

    override suspend fun updateAnime(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != COMPLETED && didWatchEpisode) {
            if (track.last_episode_seen.toLong() == track.total_episodes && track.total_episodes > 0) {
                track.status = COMPLETED
                track.finished_watching_date = System.currentTimeMillis()
            } else if (track.status != REREADING) {
                track.status = WATCHING
                if (track.last_episode_seen == 1.0) {
                    track.started_watching_date = System.currentTimeMillis()
                }
            }
        }

        return api.updateAnimeItem(track)
    }

    override suspend fun refreshAnime(track: AnimeTrack): AnimeTrack {
        return api.findAnimeListItem(track) ?: track
    }

    override suspend fun deleteAnime(track: AnimeTrack) = api.deleteAnimeItem(track)

    override suspend fun login(username: String, password: String) = login(password)

    suspend fun login(authCode: String) {
        try {
            val oauth = api.getAccessToken(authCode)
            interceptor.setAuth(oauth)
            val username = api.getCurrentUser()
            saveDisplayUsername(username)
            saveCredentials(username, oauth.accessToken)
        } catch (_: Throwable) {
            logout()
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.setAuth(null)
    }

    fun getIfAuthExpired(): Boolean {
        return trackPreferences.trackAuthExpired(this).get()
    }

    fun setAuthExpired() {
        trackPreferences.trackAuthExpired(this).set(true)
    }

    fun saveOAuth(oAuth: MALOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oAuth))
    }

    fun loadOAuth(): MALOAuth? {
        return try {
            json.decodeFromString<MALOAuth>(trackPreferences.trackToken(this).get())
        } catch (_: Exception) {
            null
        }
    }
}
