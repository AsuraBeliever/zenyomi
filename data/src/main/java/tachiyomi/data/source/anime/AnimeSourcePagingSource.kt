package tachiyomi.data.source.anime

import androidx.paging.PagingState
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import kotlinx.coroutines.CancellationException
import mihon.domain.anime.model.toDomainAnime
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.anime.repository.AnimeSourcePagingSourceType

/**
 * Paging sources for browsing an anime catalogue.
 *
 * Mirrors [tachiyomi.data.source.SourcePagingSource]; the anime source API names its
 * calls getPopularAnime and getSearchAnime, and pages carry `animes` rather than
 * `mangas`, so this could not simply be renamed from the manga version.
 */
class AnimeSourceSearchPagingSource(
    source: suspend () -> AnimeSource,
    private val query: String,
    private val filters: AnimeFilterList,
    networkToLocalAnime: NetworkToLocalAnime,
) : BaseAnimeSourcePagingSource(source, networkToLocalAnime) {
    override suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage {
        return source.getSearchAnime(currentPage, query, filters)
    }
}

class AnimeSourcePopularPagingSource(
    source: suspend () -> AnimeSource,
    networkToLocalAnime: NetworkToLocalAnime,
) : BaseAnimeSourcePagingSource(source, networkToLocalAnime) {
    override suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage {
        return source.getPopularAnime(currentPage)
    }
}

class AnimeSourceLatestPagingSource(
    source: suspend () -> AnimeSource,
    networkToLocalAnime: NetworkToLocalAnime,
) : BaseAnimeSourcePagingSource(source, networkToLocalAnime) {
    override suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseAnimeSourcePagingSource(
    private val source: suspend () -> AnimeSource,
    private val networkToLocalAnime: NetworkToLocalAnime,
) : AnimeSourcePagingSourceType() {

    private val seenAnime = hashSetOf<String>()

    abstract suspend fun requestNextPage(source: AnimeSource, currentPage: Int): AnimesPage

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Anime> {
        val page = params.key ?: 1

        return try {
            val source = source()
            val animesPage = withIOContext {
                requestNextPage(source, page.toInt())
                    .takeIf { it.animes.isNotEmpty() }
                    ?: throw NoAnimeResultsException()
            }

            val anime = animesPage.animes
                .map { it.toDomainAnime(source.id) }
                .filter { seenAnime.add(it.url) }
                .let { networkToLocalAnime(it) }

            LoadResult.Page(
                data = anime,
                prevKey = null,
                nextKey = if (animesPage.hasNextPage) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, Anime>): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoAnimeResultsException : Exception()
