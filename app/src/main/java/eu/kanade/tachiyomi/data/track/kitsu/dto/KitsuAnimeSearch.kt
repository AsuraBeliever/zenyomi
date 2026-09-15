package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

// searchAnime
@Serializable
data class KitsuSearchAnimeByTitleResult(
    val data: KitsuSearchAnimeByTitleData,
)

@Serializable
data class KitsuSearchAnimeByTitleData(
    val searchAnimeByTitle: KitsuAnimeSearchNodes,
)

@Serializable
data class KitsuAnimeSearchNodes(
    val nodes: List<KitsuAnime>,
)

// getAnimeDetails, for the "id:" and slug searches
@Serializable
data class KitsuSearchAnimeByIdResult(
    val data: KitsuSearchAnimeByIdData,
)

@Serializable
data class KitsuSearchAnimeByIdData(
    val findAnimeById: KitsuAnime?,
)

@Serializable
data class KitsuSearchAnimeBySlugResult(
    val data: KitsuSearchAnimeBySlugData,
)

@Serializable
data class KitsuSearchAnimeBySlugData(
    val findAnimeBySlug: KitsuAnime?,
)

// findLibAnime (on binding and on refreshing the tracker sheet)
@Serializable
data class KitsuSearchAnimeByIdWithLibraryResult(
    val data: KitsuSearchAnimeByIdWithLibraryData,
)

@Serializable
data class KitsuSearchAnimeByIdWithLibraryData(
    val findAnimeById: KitsuAnimeWithLibraryEntry?,
)
