package tachiyomi.domain.anime.interactor

import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Cada cuánto vale la pena volver a preguntarle a la fuente por una serie.
 *
 * Gemelo de [tachiyomi.domain.manga.interactor.FetchInterval], episodio por capítulo, con los
 * mismos números: la mediana de los huecos entre las últimas fechas, 7 días si no hay datos
 * suficientes, y un tope de 28. No se generaliza el de Mihon a los dos tipos —el árbol de anime
 * va en paralelo— pero sí se copia su aritmética, porque un anime que se actualiza cada semana
 * y un manga que se actualiza cada semana no son dos problemas distintos.
 *
 * Un intervalo **negativo** es el que ha puesto el usuario a mano, y entonces manda sobre el
 * calculado. Es la misma convención que usa el lado de manga.
 */
@Inject
class EpisodeFetchInterval(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
) {

    suspend fun toAnimeUpdate(
        anime: Anime,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): AnimeUpdate {
        val interval = anime.fetchInterval.takeIf { it < 0 }
            ?: calculateInterval(getEpisodesByAnimeId.await(anime.id), timeZone)
        val currentWindow = if (window.first == 0L && window.second == 0L) {
            getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        } else {
            window
        }
        return AnimeUpdate(
            id = anime.id,
            nextUpdate = calculateNextUpdate(anime, interval, dateTime, timeZone, currentWindow),
            fetchInterval = interval,
        )
    }

    fun getWindow(localDate: LocalDate, timeZone: TimeZone): Pair<Long, Long> {
        val today = localDate.atStartOfDayIn(timeZone)
        return Pair(
            (today - GRACE_PERIOD.days).toEpochMilliseconds(),
            (today + GRACE_PERIOD.days).toEpochMilliseconds(),
        )
    }

    internal fun calculateInterval(episodes: List<Episode>, zone: TimeZone): Int {
        val episodeWindow = if (episodes.size <= 8) 3 else 10

        fun datesOf(selector: (Episode) -> Long, filtered: Boolean): List<Instant> = episodes
            .asSequence()
            .let { seq -> if (filtered) seq.filter { selector(it) > 0L } else seq }
            .sortedByDescending(selector)
            .map { Instant.fromEpochMilliseconds(selector(it)).toLocalDateTime(zone).date.atStartOfDayIn(zone) }
            .distinct()
            .take(episodeWindow)
            .toList()

        val uploadDates = datesOf({ it.dateUpload }, filtered = true)
        val fetchDates = datesOf({ it.dateFetch }, filtered = false)

        fun medianGap(dates: List<Instant>): Int {
            val ranges = dates.windowed(2).map { it[1].daysUntil(it[0], zone) }.sorted()
            return ranges[(ranges.size - 1) / 2]
        }

        val interval = when {
            // La fuente da fechas de publicación suficientes
            uploadDates.size >= 3 -> medianGap(uploadDates)
            // Si no, vale cuándo los vimos aparecer nosotros
            fetchDates.size >= 3 -> medianGap(fetchDates)
            else -> 7
        }
        return interval.coerceIn(1, MAX_INTERVAL)
    }

    private fun calculateNextUpdate(
        anime: Anime,
        interval: Int,
        dateTime: LocalDateTime,
        timeZone: TimeZone,
        window: Pair<Long, Long>,
    ): Long {
        if (anime.nextUpdate in window.first..(window.second + 1)) return anime.nextUpdate

        val instant = if (anime.lastUpdate > 0) {
            Instant.fromEpochMilliseconds(anime.lastUpdate)
        } else {
            Clock.System.now()
        }
        val latestDate = instant.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
        val daysSinceLatest = (dateTime.toInstant(timeZone) - latestDate).inWholeDays
        val cycle = daysSinceLatest.floorDiv(
            interval.absoluteValue.takeIf { interval < 0 }
                ?: increaseInterval(interval, daysSinceLatest, increaseWhenOver = 10),
        )
        return latestDate.plus(((cycle + 1) * interval.absoluteValue.toLong()).days).toEpochMilliseconds()
    }

    /** Una serie que lleva muchas comprobaciones sin nada nuevo se consulta cada vez menos. */
    private fun increaseInterval(delta: Int, daysSinceLatest: Long, increaseWhenOver: Int): Int {
        if (delta >= MAX_INTERVAL) return MAX_INTERVAL
        val cycle = daysSinceLatest.floorDiv(delta) + 1
        return if (cycle > increaseWhenOver) {
            increaseInterval(delta * 2, daysSinceLatest, increaseWhenOver)
        } else {
            delta
        }
    }

    companion object {
        const val MAX_INTERVAL = 28

        private const val GRACE_PERIOD = 1L
    }
}
