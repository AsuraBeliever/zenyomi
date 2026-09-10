package tachiyomi.data.history.anime

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.history.anime.model.AnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository

/**
 * Mirrors [tachiyomi.data.history.HistoryRepositoryImpl] against the anime database.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeHistoryRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeHistoryRepository {

    override fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return database.animehistoryViewQueries
            .animehistory(query, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
            .subscribeToList()
    }

    override suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations? {
        return database.animehistoryViewQueries
            .getLatestAnimeHistory(AnimeHistoryMapper::mapAnimeHistoryWithRelations)
            .awaitAsOneOrNull()
    }

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return database.animehistoryQueries
            .getHistoryByAnimeId(animeId, AnimeHistoryMapper::mapAnimeHistory)
            .awaitAsList()
    }

    override suspend fun resetAnimeHistory(historyId: Long) {
        try {
            database.animehistoryQueries.resetAnimeHistoryById(historyId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun resetHistoryByAnimeId(animeId: Long) {
        try {
            database.animehistoryQueries.resetHistoryByAnimeId(animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllAnimeHistory(): Boolean {
        return try {
            database.animehistoryQueries.removeAllHistory()
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertAnimeHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            database.animehistoryQueries.upsert(historyUpdate.episodeId, historyUpdate.seenAt)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
