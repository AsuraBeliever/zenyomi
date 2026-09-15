package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.kitsu.kitsuAnimeUrl
import eu.kanade.tachiyomi.data.track.kitsu.toKitsuAnimeStatus
import eu.kanade.tachiyomi.data.track.kitsu.toKitsuAnimeSubtype
import eu.kanade.tachiyomi.data.track.kitsu.toKitsuLocalStatus
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * [KitsuAnime] plus what the signed-in account has on its own list for it.
 *
 * [KitsuLibraryEntryData] is shared with the manga side because a Kitsu library entry is one
 * type: the same progress, rating and status fields whether it points at an anime or a manga.
 * Its `progress` lands in `last_episode_seen` here and in `last_chapter_read` there, which is
 * the whole of the difference.
 */
@Serializable
data class KitsuAnimeWithLibraryEntry(
    val id: String,
    val titles: KitsuMangaTitles,
    val episodeCount: Long?,
    val posterImage: KitsuMangaPosters,
    val description: Map<String, String>,
    val status: String,
    val subtype: String,
    val startDate: String?,
    val endDate: String?,
    val slug: String,
    val averageRating: Double?,
    val myLibraryEntry: KitsuLibraryEntryData?,
) {
    fun toAnimeTrackSearch(trackId: Long): AnimeTrackSearch? {
        if (myLibraryEntry == null) return null

        return AnimeTrackSearch.create(trackId).apply {
            remote_id = this@KitsuAnimeWithLibraryEntry.id.toLong()
            library_id = myLibraryEntry.id.toLong()
            title = titles.preferred
            total_episodes = episodeCount ?: 0
            cover_url = posterImage.getPosterUrl()
            summary = description["en"] ?: ""
            tracking_url = kitsuAnimeUrl(slug)
            publishing_status = this@KitsuAnimeWithLibraryEntry.status.toKitsuAnimeStatus()
            publishing_type = subtype.toKitsuAnimeSubtype()
            start_date = startDate ?: ""

            started_watching_date = myLibraryEntry.startedAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            finished_watching_date = myLibraryEntry.finishedAt?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0
            status = myLibraryEntry.status.toKitsuLocalStatus()
            score = myLibraryEntry.rating?.toDouble() ?: 0.0
            last_episode_seen = myLibraryEntry.progress.toDouble()
            private = myLibraryEntry.private
        }
    }
}
