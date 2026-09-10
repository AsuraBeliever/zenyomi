package tachiyomi.domain.track.anime.model

import java.io.Serializable

/**
 * A link between one anime in the library and one entry on a tracking service.
 *
 * Mirrors [tachiyomi.domain.track.model.Track] rather than generalising it: the two differ in
 * what they count (episodes against chapters), and a shared type would have to be vague about
 * exactly the field every caller cares about.
 */
data class AnimeTrack(
    val id: Long,
    val animeId: Long,
    val trackerId: Long,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastEpisodeSeen: Double,
    val totalEpisodes: Long,
    val status: Long,
    val score: Double,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
    val private: Boolean,
) : Serializable
