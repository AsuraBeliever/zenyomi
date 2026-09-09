package tachiyomi.data.anime

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.anime.model.SeasonAnime
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.model.DeletableAnime
import java.time.LocalDate
import java.time.ZoneId

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeRepository {

    override suspend fun getAnimeById(id: Long): Anime {
        return database.animesQueries.getAnimeById(id, AnimeMapper::mapAnime).awaitAsOne()
    }

    override suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime> {
        return database.animesQueries.getAnimeById(id, AnimeMapper::mapAnime).subscribeToOne()
    }

    override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? {
        return database.animesQueries.getAnimeByUrlAndSource(
                url,
                sourceId,
                AnimeMapper::mapAnime,
            ).awaitAsOneOrNull()
    }

    override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> {
        return database.animesQueries.getAnimeByUrlAndSource(
                url,
                sourceId,
                AnimeMapper::mapAnime,
            ).subscribeToOneOrNull()
    }

    override suspend fun getAnimeFavorites(): List<Anime> {
        return database.animesQueries.getFavorites(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getWatchedAnimeNotInLibrary(): List<Anime> {
        return database.animesQueries.getWatchedAnimeNotInLibrary(AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getLibraryAnime(): List<LibraryAnime> {
        return database.animelibViewQueries.animelib(AnimeMapper::mapLibraryAnime).awaitAsList()
    }

    override fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>> {
        return database.animelibViewQueries.animelib(AnimeMapper::mapLibraryAnime).subscribeToList()
    }

    override fun getAnimeFavoritesBySourceId(sourceId: Long): Flow<List<Anime>> {
        return database.animesQueries.getFavoriteBySourceId(sourceId, AnimeMapper::mapAnime).subscribeToList()
    }

    override suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<Anime> {
        return database.animesQueries.getDuplicateLibraryAnime(title, id, AnimeMapper::mapAnime).awaitAsList()
    }

    override suspend fun getUpcomingAnime(statuses: Set<Long>): Flow<List<Anime>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return database.animesQueries.getUpcomingAnime(epochMillis, statuses, AnimeMapper::mapAnime).subscribeToList()
    }

    override suspend fun resetAnimeViewerFlags(): Boolean {
        return try {
            database.transaction { database.animesQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
        database.transaction {
            database.animes_categoriesQueries.deleteAnimeCategoryByAnimeId(animeId)
            categoryIds.map { categoryId ->
                database.animes_categoriesQueries.insert(animeId, categoryId)
            }
        }
    }

    override suspend fun insertAnime(anime: Anime): Long? {
        return database.transactionWithResult {
            database.animesQueries.insert(
                source = anime.source,
                url = anime.url,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre,
                title = anime.title,
                status = anime.status,
                thumbnailUrl = anime.thumbnailUrl,
                backgroundUrl = anime.backgroundUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = anime.nextUpdate,
                calculateInterval = anime.fetchInterval.toLong(),
                initialized = anime.initialized,
                viewerFlags = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                backgroundLastModified = anime.backgroundLastModified,
                dateAdded = anime.dateAdded,
                updateStrategy = anime.updateStrategy,
                version = anime.version,
                fetchType = anime.fetchType,
                parentId = anime.parentId,
                seasonFlags = anime.seasonFlags,
                seasonNumber = anime.seasonNumber,
                seasonSourceOrder = anime.seasonSourceOrder,
                memo = anime.memo,
            )
            database.animesQueries.selectLastInsertedRowId().awaitAsOneOrNull()
        }
    }

    override suspend fun updateAnime(update: AnimeUpdate): Boolean {
        return try {
            partialUpdateAnime(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllAnime(animeUpdates: List<AnimeUpdate>): Boolean {
        return try {
            partialUpdateAnime(*animeUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime> {
        return database.animeseasonsViewQueries.getAnimeSeasonsById(parentId, AnimeMapper::mapSeasonAnime).awaitAsList()
    }

    override fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>> {
        return database.animeseasonsViewQueries.getAnimeSeasonsById(parentId, AnimeMapper::mapSeasonAnime).subscribeToList()
    }

    override suspend fun removeParentIdByIds(animeIds: List<Long>) {
        try {
            database.transaction { database.animesQueries.removeParentIdByIds(animeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override fun getDeletableParentAnime(): Flow<List<DeletableAnime>> {
        return database.animedeletableViewQueries.getDeletableParentAnime(AnimeMapper::mapDeletableAnime).subscribeToList()
    }

    override suspend fun getChildrenByParentId(parentId: Long): List<Anime> {
        return database.animesQueries.getChildrenByParentId(parentId, AnimeMapper::mapAnime).awaitAsList()
    }

    private suspend fun partialUpdateAnime(vararg animeUpdates: AnimeUpdate) {
        database.transaction {
            animeUpdates.forEach { value ->
                database.animesQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    backgroundUrl = value.backgroundUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    episodeFlags = value.episodeFlags,
                    coverLastModified = value.coverLastModified,
                    backgroundLastModified = value.backgroundLastModified,
                    dateAdded = value.dateAdded,
                    animeId = value.id,
                    updateStrategy = value.updateStrategy?.let(AnimeUpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                    fetchType = value.fetchType?.let(FetchTypeColumnAdapter::encode),
                    parentId = value.parentId,
                    seasonFlags = value.seasonFlags,
                    seasonNumber = value.seasonNumber,
                    seasonSourceOrder = value.seasonSourceOrder,
                    memo = value.memo?.let(MemoColumnAdapter::encode),
                )
            }
        }
    }
}
