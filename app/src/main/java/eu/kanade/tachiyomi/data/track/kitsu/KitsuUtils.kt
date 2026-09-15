package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack

internal fun Track.toKitsuApiStatus() = when (status) {
    Kitsu.READING -> "CURRENT"
    Kitsu.COMPLETED -> "COMPLETED"
    Kitsu.ON_HOLD -> "ON_HOLD"
    Kitsu.DROPPED -> "DROPPED"
    Kitsu.PLAN_TO_READ -> "PLANNED"
    else -> throw Exception("Unknown status: $status")
}

internal fun String.toKitsuLocalStatus() = when (this) {
    "CURRENT" -> Kitsu.READING
    "COMPLETED" -> Kitsu.COMPLETED
    "ON_HOLD" -> Kitsu.ON_HOLD
    "DROPPED" -> Kitsu.DROPPED
    "PLANNED" -> Kitsu.PLAN_TO_READ
    else -> throw Exception("Unknown status: $this")
}

// ---- Anime ---------------------------------------------------------------------------------
//
// Kitsu keeps one library with a media type on each entry, so an anime and a manga share the
// same five list statuses and the same `LibraryEntryStatusEnum` values. Only the local constant
// names differ, and [toKitsuLocalStatus] above is reused as is.

internal fun AnimeTrack.toKitsuApiStatus() = when (status) {
    Kitsu.WATCHING -> "CURRENT"
    Kitsu.COMPLETED -> "COMPLETED"
    Kitsu.ON_HOLD -> "ON_HOLD"
    Kitsu.DROPPED -> "DROPPED"
    Kitsu.PLAN_TO_WATCH -> "PLANNED"
    else -> throw Exception("Unknown status: $status")
}

internal fun kitsuAnimeUrl(slug: String) = "https://kitsu.app/anime/$slug"

/**
 * Kitsu's release status in the words an anime uses: a running series airs, it does not publish.
 */
internal fun String.toKitsuAnimeStatus() = when (this) {
    "TBA" -> "TBA"
    "CURRENT" -> "Airing"
    else -> lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * TV, OVA and ONA are acronyms; running them through the manga side's title-casing would print
 * "Ova". The rest — MOVIE, SPECIAL, MUSIC — are words and read better title-cased.
 */
internal fun String.toKitsuAnimeSubtype() = when (this) {
    "TV", "OVA", "ONA" -> this
    else -> lowercase().replaceFirstChar { it.uppercase() }
}
