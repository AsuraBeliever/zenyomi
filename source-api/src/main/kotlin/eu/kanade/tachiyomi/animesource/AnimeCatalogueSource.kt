package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

/**
 * Aniyomi's own version of this interface overrides getHosterList and getVideoList with bodies
 * that call themselves, which is an infinite recursion rather than the "not implemented" the
 * defaults in [AnimeSource] express. It never fires there or here because every catalogue
 * source in practice extends AnimeHttpSource, which overrides both — but a source that did not
 * would blow the stack instead of throwing something a caller can handle, so the overrides are
 * dropped and the defaults left to do their job.
 */
interface AnimeCatalogueSource : AnimeSource {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    @Suppress("DEPRECATION")
    override suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = fetchSearchAnime(page, query, filters).awaitSingle()

    @Suppress("DEPRECATION")
    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = supervisorScope {
        val asyncAnime = if (fetchDetails) async { getAnimeDetails(anime) } else null
        val asyncEpisodes = if (fetchEpisodes) async { getEpisodeList(anime) } else null
        SAnimeEpisodeUpdate(asyncAnime?.await() ?: anime, asyncEpisodes?.await() ?: episodes)
    }

    @Suppress("DEPRECATION")
    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = supervisorScope {
        val asyncAnime = if (fetchDetails) async { getAnimeDetails(anime) } else null
        val asyncSeasons = if (fetchSeasons) async { getSeasonList(anime) } else null
        SAnimeSeasonUpdate(asyncAnime?.await() ?: anime, asyncSeasons?.await() ?: seasons)
    }

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> {
        throw Exception("Stub!")
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAnime"),
    )
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAnime"),
    )
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>
}
