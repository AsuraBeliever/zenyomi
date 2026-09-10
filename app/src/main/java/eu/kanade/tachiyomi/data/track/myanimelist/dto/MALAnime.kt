package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALAnime(
    val id: Long,
    val title: String,
    val synopsis: String = "",
    @SerialName("num_episodes")
    val numEpisodes: Long = 0,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALMangaCovers?,
    val status: String = "",
    @SerialName("media_type")
    val mediaType: String = "",
    @SerialName("start_date")
    val startDate: String? = null,
)

@Serializable
data class MALAnimeSearchResult(
    val data: List<MALAnimeSearchItem>,
)

@Serializable
data class MALAnimeSearchItem(
    val node: MALAnime,
)

@Serializable
data class MALAnimeListItem(
    @SerialName("num_episodes")
    val numEpisodes: Long = 0,
    @SerialName("my_list_status")
    val myListStatus: MALAnimeListItemStatus?,
)

@Serializable
data class MALAnimeListItemStatus(
    @SerialName("is_rewatching")
    val isRewatching: Boolean = false,
    val status: String,
    @SerialName("num_episodes_watched")
    val numEpisodesWatched: Double = 0.0,
    val score: Int = 0,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
)
