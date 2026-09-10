package tachiyomi.data.source.anime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import eu.kanade.tachiyomi.animesource.AnimeSource as ApiAnimeSource
import tachiyomi.domain.source.anime.model.AnimeSource as DomainAnimeSource

/**
 * Mirrors [tachiyomi.data.source.SourceRepositoryImpl] for anime.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeSourceRepositoryImpl(
    private val sourceManager: AnimeSourceManager,
    private val database: AnimeDatabase,
    private val networkToLocalAnime: NetworkToLocalAnime,
) : AnimeSourceRepository {

    override fun getAnimeSources(): Flow<List<DomainAnimeSource>> {
        return sourceManager.sources.map { sources ->
            sources.map { mapToDomain(it).copy(supportsLatest = it.supportsLatest) }
        }
    }

    override fun getOnlineAnimeSources(): Flow<List<DomainAnimeSource>> {
        return sourceManager.sources.map { sources ->
            sources.filterIsInstance<AnimeHttpSource>().map(::mapToDomain)
        }
    }

    override fun getAnimeSourcesWithFavoriteCount(): Flow<List<Pair<DomainAnimeSource, Long>>> {
        val favoriteCounts = database.animesQueries
            .getAnimeSourceIdWithFavoriteCount()
            .subscribeToList()
        return combine(favoriteCounts, sourceManager.sources) { counts, _ -> counts }
            .map { counts ->
                counts.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    mapToDomain(source).copy(isStub = source is StubAnimeSource) to count
                }
            }
    }

    override fun searchAnime(
        sourceId: Long,
        query: String,
        filterList: AnimeFilterList,
    ): AnimeSourcePagingSourceType {
        return AnimeSourceSearchPagingSource(
            { catalogue(sourceId) },
            query,
            filterList,
            networkToLocalAnime,
        )
    }

    override fun getPopularAnime(sourceId: Long): AnimeSourcePagingSourceType {
        return AnimeSourcePopularPagingSource({ catalogue(sourceId) }, networkToLocalAnime)
    }

    override fun getLatestAnime(sourceId: Long): AnimeSourcePagingSourceType {
        return AnimeSourceLatestPagingSource({ catalogue(sourceId) }, networkToLocalAnime)
    }

    // The catalogue calls live on AnimeSource itself, so no cast to a catalogue
    // subtype is needed and sources like the local one can stay minimal.
    private suspend fun catalogue(sourceId: Long) = sourceManager.getOrStub(sourceId)

    private fun mapToDomain(source: ApiAnimeSource): DomainAnimeSource = DomainAnimeSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = false,
    )
}
