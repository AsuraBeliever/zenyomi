package tachiyomi.domain.source.anime.service

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.anime.model.StubAnimeSource

/**
 * Anime counterpart of [tachiyomi.domain.source.service.SourceManager].
 *
 * Mirrors Mihon's shape rather than Aniyomi's: the accessors are suspending, because
 * source lookup waits on the extension manager finishing its first load. Aniyomi
 * exposes them synchronously plus an `isInitialized` flag that every caller has to
 * remember to check. See docs/adr/0001-arbol-paralelo-anime.md.
 */
interface AnimeSourceManager {

    val sources: Flow<List<AnimeSource>>

    suspend fun get(sourceKey: Long): AnimeSource?

    suspend fun getOrStub(sourceKey: Long): AnimeSource

    suspend fun getAll(): List<AnimeSource>

    suspend fun getOnlineSources(): List<AnimeHttpSource>

    suspend fun getStubSources(): List<StubAnimeSource>
}
