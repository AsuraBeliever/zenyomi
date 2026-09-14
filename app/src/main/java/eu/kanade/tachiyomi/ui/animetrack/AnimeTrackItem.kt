package eu.kanade.tachiyomi.ui.animetrack

import eu.kanade.tachiyomi.data.track.AnimeTracker
import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * One row of the tracking sheet: a service, and the entry bound to it if there is one.
 *
 * The twin of Mihon's [eu.kanade.tachiyomi.ui.manga.track.TrackItem]. [displayScore] is carried
 * rather than computed in the composable because formatting it is the service's job — AniList
 * prints a five-point score differently from a hundred-point one — and that belongs off the
 * drawing thread.
 */
data class AnimeTrackItem(
    val track: AnimeTrack?,
    val tracker: AnimeTracker,
    val displayScore: String = "",
)
