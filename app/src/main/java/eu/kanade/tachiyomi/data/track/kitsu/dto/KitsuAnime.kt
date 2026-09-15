package eu.kanade.tachiyomi.data.track.kitsu.dto

import eu.kanade.tachiyomi.data.track.kitsu.kitsuAnimeUrl
import eu.kanade.tachiyomi.data.track.kitsu.toKitsuAnimeStatus
import eu.kanade.tachiyomi.data.track.kitsu.toKitsuAnimeSubtype
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.Serializable

/**
 * One anime as Kitsu's GraphQL hands it back.
 *
 * `titles` and `posterImage` are the very same GraphQL types the manga query asks for — Kitsu's
 * schema shares them across media — so the DTOs already declared for the manga side are reused
 * instead of copied under an anime name. What actually differs is `episodeCount` rather than
 * `chapterCount`, and no staff: an [AnimeTrackSearch] has nowhere to put authors or artists,
 * so the query does not ask for five people it would then drop.
 */
@Serializable
data class KitsuAnime(
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
) {
    fun toAnimeTrackSearch(trackId: Long): AnimeTrackSearch {
        return AnimeTrackSearch.create(trackId).apply {
            remote_id = this@KitsuAnime.id.toLong()
            title = titles.preferred
            total_episodes = episodeCount ?: 0
            cover_url = posterImage.getPosterUrl()
            summary = description["en"] ?: ""
            tracking_url = kitsuAnimeUrl(slug)
            score = averageRating ?: -1.0
            publishing_status = this@KitsuAnime.status.toKitsuAnimeStatus()
            publishing_type = subtype.toKitsuAnimeSubtype()
            start_date = startDate ?: ""
        }
    }
}
