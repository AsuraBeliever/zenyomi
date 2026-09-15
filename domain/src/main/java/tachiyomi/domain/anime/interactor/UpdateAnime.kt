package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import kotlin.time.Clock

/**
 * Anime counterpart of [eu.kanade.domain.manga.interactor.UpdateManga], narrowed to what
 * the anime side needs so far.
 */
@Inject
class UpdateAnime(
    private val animeRepository: AnimeRepository,
    private val fetchInterval: EpisodeFetchInterval,
) {

    suspend fun await(animeUpdate: AnimeUpdate): Boolean =
        animeRepository.updateAnime(animeUpdate)

    suspend fun awaitAll(animeUpdates: List<AnimeUpdate>): Boolean =
        animeRepository.updateAllAnime(animeUpdates)

    /**
     * Guarda cada cuanto volver a mirar esta serie, y cuando toca la proxima.
     *
     * Un intervalo puesto a mano se guarda en **negativo**: es la convencion del lado de manga
     * y lo que hace que el calculo automatico no lo pise en la siguiente pasada.
     */
    suspend fun awaitUpdateFetchInterval(
        anime: Anime,
        dateTime: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime.date, TimeZone.currentSystemDefault()),
    ): Boolean = await(fetchInterval.toAnimeUpdate(anime, dateTime, TimeZone.currentSystemDefault(), window))

    /**
     * Stamps dateAdded when an entry enters the library and clears it when it leaves, so
     * "recently added" sorting has something to work with.
     */
    suspend fun awaitUpdateFavorite(animeId: Long, favorite: Boolean): Boolean {
        val dateAdded = if (favorite) Clock.System.now().toEpochMilliseconds() else 0
        return await(AnimeUpdate(id = animeId, favorite = favorite, dateAdded = dateAdded))
    }
}
