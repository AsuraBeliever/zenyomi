package tachiyomi.data.updates.anime

import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations

/**
 * Anime counterpart of the mapper inlined in [tachiyomi.data.updates.UpdatesRepositoryImpl];
 * kept as its own object to match [tachiyomi.data.history.anime.AnimeHistoryMapper].
 */
object AnimeUpdatesMapper {

    @Suppress("LongParameterList")
    fun mapUpdatesWithRelations(
        animeId: Long,
        animeTitle: String,
        episodeId: Long,
        episodeName: String,
        seen: Boolean,
        bookmark: Boolean,
        fillermark: Boolean,
        lastSecondSeen: Long,
        totalSeconds: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateFetch: Long,
    ): AnimeUpdatesWithRelations = AnimeUpdatesWithRelations(
        animeId = animeId,
        animeTitle = animeTitle,
        episodeId = episodeId,
        episodeName = episodeName,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = AnimeCover(
            animeId = animeId,
            sourceId = sourceId,
            isAnimeFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
