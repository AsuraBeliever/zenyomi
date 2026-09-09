package tachiyomi.data.episode

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EpisodeRepositoryImpl(
    private val database: AnimeDatabase,
) : EpisodeRepository {

    override suspend fun addAllEpisodes(episodes: List<Episode>): List<Episode> {
        return try {
            database.transactionWithResult {
                episodes.map { episode ->
                    database.episodesQueries.insert(
                        episode.animeId,
                        episode.url,
                        episode.name,
                        episode.scanlator,
                        episode.seen,
                        episode.bookmark,
                        episode.lastSecondSeen,
                        episode.totalSeconds,
                        episode.episodeNumber,
                        episode.sourceOrder,
                        episode.dateFetch,
                        episode.dateUpload,
                        episode.version,
                        episode.summary,
                        episode.previewUrl,
                        episode.fillermark,
                        episode.memo,
                    )
                    val lastInsertId = database.episodesQueries.selectLastInsertedRowId().awaitAsOne()
                    episode.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun updateEpisode(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAllEpisodes(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        database.transaction {
            episodeUpdates.forEach { episodeUpdate ->
                database.episodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    totalSeconds = episodeUpdate.totalSeconds,
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    episodeId = episodeUpdate.id,
                    version = episodeUpdate.version,
                    isSyncing = 0,
                    summary = episodeUpdate.summary,
                    previewUrl = episodeUpdate.previewUrl,
                    fillermark = episodeUpdate.fillermark,
                    memo = episodeUpdate.memo?.let(MemoColumnAdapter::encode),
                )
            }
        }
    }

    override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) {
        try {
            database.transaction { database.episodesQueries.removeEpisodesWithIds(episodeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getEpisodeByAnimeId(animeId: Long): List<Episode> {
        return database.episodesQueries.getEpisodesByAnimeId(animeId, ::mapEpisode).awaitAsList()
    }

    override suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return database.episodesQueries.getBookmarkedEpisodesByAnimeId(
                animeId,
                ::mapEpisode,
            ).awaitAsList()
    }

    override suspend fun getEpisodeById(id: Long): Episode? {
        return database.episodesQueries.getEpisodeById(id, ::mapEpisode).awaitAsOneOrNull()
    }

    override suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long): Flow<List<Episode>> {
        return database.episodesQueries.getEpisodesByAnimeId(
                animeId,
                ::mapEpisode,
            ).subscribeToList()
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode? {
        return database.episodesQueries.getEpisodeByUrlAndAnimeId(
                url,
                animeId,
                ::mapEpisode,
            ).awaitAsOneOrNull()
    }

    private fun mapEpisode(
        id: Long,
        animeId: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
        summary: String?,
        previewUrl: String?,
        fillermark: Boolean,
        memo: JsonObject,
    ): Episode = Episode(
        id = id,
        animeId = animeId,
        seen = seen,
        bookmark = bookmark,
        fillermark = fillermark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber,
        scanlator = scanlator,
        summary = summary,
        previewUrl = previewUrl,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memo = memo,
    )
}
