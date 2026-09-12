package tachiyomi.domain.updates.anime.model

import tachiyomi.domain.anime.model.AnimeCover

/**
 * An episode the library gained, with enough of its anime to draw a row.
 *
 * Anime counterpart of [tachiyomi.domain.updates.model.UpdatesWithRelations]. Kept apart
 * rather than generalised: the two read from separate databases and carry different
 * per-item state (seen position in seconds against a page number).
 */
data class AnimeUpdatesWithRelations(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val seen: Boolean,
    val bookmark: Boolean,
    val fillermark: Boolean,
    val lastSecondSeen: Long,
    val totalSeconds: Long,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: AnimeCover,
)
