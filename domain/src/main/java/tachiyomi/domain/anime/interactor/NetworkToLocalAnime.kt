package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@Inject
class NetworkToLocalAnime(
    private val animeRepository: AnimeRepository,
    private val sourceManager: AnimeSourceManager,
) {

    /**
     * Mihon's manga counterpart exposes invoke() and batches the whole list through
     * MangaRepository.insertNetworkManga. AnimeRepository has no batch insert yet, so
     * these delegate to the per-entry path below; the call shape matches Mihon, the
     * batching does not. Porting the batch query is recorded in docs/PORTING_LOG.md.
     */
    suspend operator fun invoke(anime: Anime): Anime = await(anime)

    suspend operator fun invoke(anime: List<Anime>): List<Anime> = anime.map { await(it) }

    suspend fun await(anime: Anime): Anime {
        val localAnime = getAnime(anime.url, anime.source)
        return when {
            localAnime == null -> {
                val id = insertAnime(anime)
                anime.copy(id = id!!)
            }
            !localAnime.favorite -> {
                // if the anime isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localAnime.copy(title = anime.title)
            }
            else -> {
                localAnime
            }
        }
    }

    private suspend fun getAnime(url: String, sourceId: Long): Anime? {
        return animeRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertAnime(anime: Anime): Long? {
        return animeRepository.insertAnime(anime)
    }
}
