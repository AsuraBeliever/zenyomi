package tachiyomi.domain.source.anime.repository

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.anime.model.AnimeSource

// Mirrors Mihon: pages carry domain models, not source ones. Aniyomi types this
// against SAnime and converts later; see docs/adr/0001-arbol-paralelo-anime.md
typealias AnimeSourcePagingSourceType = PagingSource<Long, Anime>

interface AnimeSourceRepository {

    fun getAnimeSources(): Flow<List<AnimeSource>>

    fun getOnlineAnimeSources(): Flow<List<AnimeSource>>

    fun getAnimeSourcesWithFavoriteCount(): Flow<List<Pair<AnimeSource, Long>>>

    fun searchAnime(sourceId: Long, query: String, filterList: AnimeFilterList): AnimeSourcePagingSourceType

    fun getPopularAnime(sourceId: Long): AnimeSourcePagingSourceType

    fun getLatestAnime(sourceId: Long): AnimeSourcePagingSourceType
}
