package eu.kanade.tachiyomi.data.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch

/**
 * Anime support for a tracker, added alongside [Tracker] rather than inside it.
 *
 * Not every service Mihon supports has anime at all — Komga, Kavita, MangaUpdates and the rest
 * are manga-only — so this is a capability a tracker opts into, and the UI offers anime
 * tracking for exactly the trackers that implement it. Login, logout and the account state stay
 * on [Tracker]: a user signs in to AniList once, not once per media type.
 */
interface AnimeTracker {

    suspend fun searchAnime(query: String): List<AnimeTrackSearch>

    suspend fun bindAnime(track: AnimeTrack, hasSeenEpisodes: Boolean = false): AnimeTrack

    suspend fun updateAnime(track: AnimeTrack, didWatchEpisode: Boolean = false): AnimeTrack

    suspend fun refreshAnime(track: AnimeTrack): AnimeTrack

    suspend fun deleteAnime(track: AnimeTrack)

    /** Status the service uses for "currently watching", used when the user starts an anime. */
    fun getWatchingStatus(): Long

    fun getCompletionStatusAnime(): Long

    /**
     * The statuses to offer for an anime, and what to call each one.
     *
     * Separate from [Tracker.getStatusList] and [Tracker.getStatus] because the same numbers
     * mean different words: status 1 on MyAnimeList is "Reading" for a manga and "Watching"
     * for an anime, and the sheet was showing the manga word over an anime. Every service here
     * reuses its manga status numbers for anime, so only the labels differ.
     */
    fun getStatusListAnime(): List<Long>

    fun getStatusForAnime(status: Long): StringResource?
}
