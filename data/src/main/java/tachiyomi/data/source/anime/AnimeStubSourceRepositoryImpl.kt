package tachiyomi.data.source.anime

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository

/**
 * Remembers the id, name and language of anime sources whose extension is no longer
 * installed, so the library can still show something meaningful for their entries.
 *
 * Mirrors [tachiyomi.data.source.StubSourceRepositoryImpl] against the anime database.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeStubSourceRepositoryImpl(
    private val database: AnimeDatabase,
) : AnimeStubSourceRepository {

    override fun subscribeAll(): Flow<List<StubAnimeSource>> {
        return database.animesourcesQueries
            .findAll(::mapStubAnimeSource)
            .subscribeToList()
    }

    override suspend fun getStubSource(id: Long): StubAnimeSource? {
        return database.animesourcesQueries
            .findOne(id, ::mapStubAnimeSource)
            .awaitAsOneOrNull()
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        database.animesourcesQueries.upsert(id, lang, name)
    }

    private fun mapStubAnimeSource(
        id: Long,
        lang: String,
        name: String,
    ): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)
}
